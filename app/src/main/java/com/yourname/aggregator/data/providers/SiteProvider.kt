package com.yourname.aggregator.data.providers

import com.yourname.aggregator.core.model.StreamSource
import com.yourname.aggregator.core.model.VideoItem

interface SiteProvider {
    val id: String
    val displayName: String
    val baseUrl: String

    /** Fetch & parse search results for [query] at 1-based [page]. */
    suspend fun search(query: String, page: Int = 1): List<VideoItem>

    /** Fetch default trending/popular items when no preference tags exist yet. */
    suspend fun getTrending(page: Int = 1): List<VideoItem>

    /** Extract direct MP4/HLS stream URL from [videoPageUrl], or return WebFallback. */
    suspend fun resolveStream(videoPageUrl: String): StreamSource =
        StreamSource.WebFallback(videoPageUrl)

    companion object {
        /** Converts strings like "1:04:22" or "12:34" into total seconds. */
        fun parseDurationToSeconds(raw: String): Int {
            val parts = raw.trim().split(":").mapNotNull { it.toIntOrNull() }
            return when (parts.size) {
                3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
                2 -> parts[0] * 60 + parts[1]
                1 -> parts[0]
                else -> 0
            }
        }
    }
}
