package com.yourname.aggregator.data.repository

import com.yourname.aggregator.core.model.SearchFilter
import com.yourname.aggregator.core.model.SortOrder
import com.yourname.aggregator.core.model.StreamSource
import com.yourname.aggregator.core.model.VideoItem
import com.yourname.aggregator.data.local.FavoriteDao
import com.yourname.aggregator.data.local.FavoriteEntity
import com.yourname.aggregator.data.local.PreferenceDao
import com.yourname.aggregator.data.providers.ProviderRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class AggregatedResult(
    val videos: List<VideoItem>,
    val failedSources: List<String> = emptyList() // Surfaces subtle warnings if a scraper broke
)

class VideoRepository(
    private val registry: ProviderRegistry,
    private val favoriteDao: FavoriteDao,
    private val preferenceDao: PreferenceDao
) {

    /**
     * Runs keyword searches across all enabled sites in parallel,
     * isolates per-site failures, and applies local filtering & sorting.
     */
    suspend fun search(
        query: String,
        page: Int = 1,
        filter: SearchFilter = SearchFilter()
    ): AggregatedResult = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) return@withContext AggregatedResult(emptyList())

        val activeProviders = registry.getActive(filter.enabledSourceIds)
        val failedSources = mutableListOf<String>()

        val perSiteResults: List<List<VideoItem>> = supervisorScope {
            activeProviders.map { provider ->
                async {
                    val result = withTimeoutOrNull(8_000L) {
                        runCatching { provider.search(cleanQuery, page) }.getOrNull()
                    }
                    if (result == null) {
                        synchronized(failedSources) { failedSources.add(provider.displayName) }
                        emptyList()
                    } else {
                        result
                    }
                }
            }.awaitAll()
        }

        val merged = mergeAndFilter(perSiteResults, filter)
        AggregatedResult(videos = merged, failedSources = failedSources)
    }

    /**
     * Builds the Home Screen feed from saved keyword preferences in Room.
     * Falls back to provider trending feeds if no preference keywords are configured.
     */
    suspend fun getHomeFeed(
        page: Int = 1,
        filter: SearchFilter = SearchFilter()
    ): AggregatedResult = withContext(Dispatchers.IO) {
        val savedKeywords = preferenceDao.observeKeywords().first()
            .map { it.keyword.trim() }
            .filter { it.isNotEmpty() }

        val activeProviders = registry.getActive(filter.enabledSourceIds)
        val failedSources = mutableListOf<String>()

        val buckets: List<List<VideoItem>> = supervisorScope {
            if (savedKeywords.isEmpty()) {
                // No preferences saved yet -> fetch trending from each enabled site
                activeProviders.map { provider ->
                    async {
                        val items = withTimeoutOrNull(8_000L) {
                            runCatching { provider.getTrending(page) }.getOrNull()
                        }
                        if (items == null) {
                            synchronized(failedSources) { failedSources.add(provider.displayName) }
                            emptyList()
                        } else {
                            items
                        }
                    }
                }.awaitAll()
            } else {
                // Sample up to 3 saved preference keywords in parallel across active providers
                val selectedKeywords = savedKeywords.shuffled().take(3)
                selectedKeywords.flatMap { keyword ->
                    activeProviders.map { provider ->
                        async {
                            withTimeoutOrNull(8_000L) {
                                runCatching { provider.search(keyword, page) }.getOrDefault(emptyList())
                            } ?: emptyList()
                        }
                    }
                }.awaitAll()
            }
        }

        val merged = mergeAndFilter(buckets, filter)
        AggregatedResult(videos = merged, failedSources = failedSources)
    }

    /** Resolves playable stream URL via the video's originating SiteProvider. */
    suspend fun resolveStream(video: VideoItem): StreamSource = withContext(Dispatchers.IO) {
        val provider = registry.getById(video.sourceId)
            ?: return@withContext StreamSource.WebFallback(video.pageUrl)

        withTimeoutOrNull(8_000L) {
            runCatching { provider.resolveStream(video.pageUrl) }.getOrNull()
        } ?: StreamSource.WebFallback(video.pageUrl)
    }

    // --- Favorites ---

    fun observeFavorites(): Flow<List<VideoItem>> =
        favoriteDao.observeAll().map { entities -> entities.map { it.toVideoItem() } }

    fun observeFavoriteIds(): Flow<Set<String>> =
        favoriteDao.observeAllIds().map { it.toSet() }

    suspend fun toggleFavorite(video: VideoItem) = withContext(Dispatchers.IO) {
        if (favoriteDao.exists(video.id)) {
            favoriteDao.deleteById(video.id)
        } else {
            favoriteDao.upsert(FavoriteEntity.fromVideoItem(video))
        }
    }

    // --- Internal Merging, Filtering & Sorting ---

    private fun mergeAndFilter(
        buckets: List<List<VideoItem>>,
        filter: SearchFilter
    ): List<VideoItem> {
        val normalizedExclusions = filter.excludedTags.map { it.lowercase().trim() }.toSet()

        // 1. Interleave round-robin so top results from every site appear near the top
        val interleaved = roundRobinInterleave(buckets)

        // 2. Deduplicate and apply local metadata filters
        val filtered = interleaved
            .distinctBy { it.id }
            .filter { video ->
                val matchesMinDuration = video.durationSeconds == 0 ||
                    video.durationSeconds >= filter.minDurationSeconds

                val matchesMaxDuration = filter.maxDurationSeconds == null ||
                    video.durationSeconds == 0 ||
                    video.durationSeconds <= filter.maxDurationSeconds

                val hasExcludedTag = normalizedExclusions.isNotEmpty() &&
                    video.tags.any { tag -> tag.lowercase().trim() in normalizedExclusions }

                matchesMinDuration && matchesMaxDuration && !hasExcludedTag
            }

        // 3. Apply requested sort order
        return when (filter.sortOrder) {
            SortOrder.RELEVANCE -> filtered
            SortOrder.LONGEST -> filtered.sortedByDescending { it.durationSeconds }
            SortOrder.SHORTEST -> filtered.sortedWith(
                compareBy<VideoItem> { it.durationSeconds == 0 }.thenBy { it.durationSeconds }
            )
            SortOrder.MOST_VIEWED -> filtered.sortedByDescending { it.views ?: -1L }
        }
    }

    private fun <T> roundRobinInterleave(lists: List<List<T>>): List<T> {
        if (lists.isEmpty()) return emptyList()
        val maxSize = lists.maxOfOrNull { it.size } ?: 0
        val result = ArrayList<T>(lists.sumOf { it.size })
        for (index in 0 until maxSize) {
            for (list in lists) {
                if (index < list.size) {
                    result.add(list[index])
                }
            }
        }
        return result
    }
}
