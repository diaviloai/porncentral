package com.yourname.aggregator.data.providers.impl

import com.yourname.aggregator.core.model.StreamSource
import com.yourname.aggregator.core.model.VideoItem
import com.yourname.aggregator.core.network.NetworkClient
import com.yourname.aggregator.core.network.SafeFetch
import com.yourname.aggregator.data.providers.SiteProvider
import org.json.JSONArray
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class HentaiTvProvider : SiteProvider {

    override val id: String = "hentaitv"
    override val displayName: String = "Hentai.tv"
    override val baseUrl: String = "https://hentai.tv"

    override suspend fun search(query: String, page: Int): List<VideoItem> {
        val encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.toString())

        // 1. Try WordPress REST API first (fast, structured JSON)
        val wpApiUrl = "$baseUrl/wp-json/wp/v2/posts?_embed&per_page=20&page=$page&search=$encoded"
        val apiItems = runCatching { fetchWpRestApi(wpApiUrl) }.getOrNull()
        if (!apiItems.isNullOrEmpty()) return apiItems

        // 2. Fallback to HTML search page
        val htmlUrl = if (page <= 1) "$baseUrl/?s=$encoded" else "$baseUrl/page/$page/?s=$encoded"
        val doc = SafeFetch.fetchDocument(
            url = htmlUrl,
            referer = "$baseUrl/",
            extraHeaders = mapOf("User-Agent" to NetworkClient.DESKTOP_USER_AGENT)
        )
        return parseCards(doc)
    }

    override suspend fun getTrending(page: Int): List<VideoItem> {
        // 1. Try WordPress REST API latest posts first
        val wpApiUrl = "$baseUrl/wp-json/wp/v2/posts?_embed&per_page=20&page=$page"
        val apiItems = runCatching { fetchWpRestApi(wpApiUrl) }.getOrNull()
        if (!apiItems.isNullOrEmpty()) return apiItems

        // 2. Fallback to homepage HTML
        val htmlUrl = if (page <= 1) "$baseUrl/" else "$baseUrl/page/$page/"
        val doc = SafeFetch.fetchDocument(
            url = htmlUrl,
            referer = "$baseUrl/",
            extraHeaders = mapOf("User-Agent" to NetworkClient.DESKTOP_USER_AGENT)
        )
        return parseCards(doc)
    }

    override suspend fun resolveStream(videoPageUrl: String): StreamSource {
        val html = SafeFetch.fetchText(
            url = videoPageUrl,
            referer = "$baseUrl/",
            extraHeaders = mapOf("User-Agent" to NetworkClient.DESKTOP_USER_AGENT)
        )

        val streamRegex = Regex("""(https?:\\?/\\?/[^"'\s\\<>]+\.(?:m3u8|mp4)(?:\?[^"'\s\\<>]*)?)""")
        val streamUrl = streamRegex.findAll(html)
            .map { it.groupValues[1].replace("\\/", "/") }
            .firstOrNull { !it.contains("preview", ignoreCase = true) }

        return if (!streamUrl.isNullOrBlank()) {
            StreamSource.DirectStream(
                url = streamUrl,
                headers = mapOf(
                    "Referer" to videoPageUrl,
                    "User-Agent" to NetworkClient.DESKTOP_USER_AGENT
                )
            )
        } else {
            StreamSource.WebFallback(videoPageUrl)
        }
    }

    private suspend fun fetchWpRestApi(apiUrl: String): List<VideoItem> {
        val raw = SafeFetch.fetchText(
            url = apiUrl,
            referer = "$baseUrl/",
            extraHeaders = mapOf(
                "User-Agent" to NetworkClient.DESKTOP_USER_AGENT,
                "Accept" to "application/json"
            )
        )
        if (!raw.trimStart().startsWith("[")) return emptyList()

        val posts = JSONArray(raw)
        val results = mutableListOf<VideoItem>()

        for (i in 0 until posts.length()) {
            val post = posts.optJSONObject(i) ?: continue
            val postId = post.optInt("id", 0)
            val link = post.optString("link").trim()
            val rawTitle = post.optJSONObject("title")?.optString("rendered").orEmpty()
            val cleanTitle = Jsoup.parse(rawTitle).text().trim()
            if (link.isBlank() || cleanTitle.isBlank()) continue

            val thumb = post.optJSONObject("_embedded")
                ?.optJSONArray("wp:featuredmedia")
                ?.optJSONObject(0)
                ?.optString("source_url")
                .orEmpty()

            results.add(
                VideoItem(
                    id = "${id}_${if (postId != 0) postId else link.hashCode().toUInt().toString(16)}",
                    sourceId = id,
                    sourceName = displayName,
                    title = cleanTitle,
                    pageUrl = link,
                    thumbnailUrl = thumb
                )
            )
        }
        return results
    }

    private fun parseCards(doc: Document): List<VideoItem> {
        val ignoredSegments = setOf("/genre/", "/tag/", "/category/", "/page/", "/wp-", "/author/", "/feed")
        val seenUrls = HashSet<String>()
        val results = mutableListOf<VideoItem>()

        // Match any link containing a thumbnail image inside the main content area
        val candidateLinks = doc.select("a:has(img)")
        for (link in candidateLinks) {
            val pageUrl = link.absUrl("href").substringBefore("#").substringBefore("?")
            if (!pageUrl.startsWith(baseUrl)) continue
            if (pageUrl.trimEnd('/') == baseUrl.trimEnd('/')) continue
            if (ignoredSegments.any { pageUrl.contains(it, ignoreCase = true) }) continue
            if (!seenUrls.add(pageUrl)) continue

            val imgEl = link.selectFirst("img")
            val thumb = listOf("data-src", "data-lazy-src", "data-original", "src")
                .firstNotNullOfOrNull { attr ->
                    imgEl?.absUrl(attr)?.takeIf { it.startsWith("http") && !it.startsWith("data:") }
                }.orEmpty()

            val cardRoot = link.parents().firstOrNull { parent ->
                parent.tagName() in setOf("article", "li") || parent.hasClass("crsl-slde")
            } ?: link.parent() ?: link

            val title = link.attr("title")
                .ifBlank { imgEl?.attr("alt").orEmpty() }
                .ifBlank { cardRoot.selectFirst("h2, h3, h4, .title, .entry-title")?.text().orEmpty() }
                .trim()

            if (title.isBlank()) continue

            val durationText = cardRoot.selectFirst(".duration, span.time")?.text()?.trim().orEmpty()

            results.add(
                VideoItem(
                    id = "${id}_${pageUrl.hashCode().toUInt().toString(16)}",
                    sourceId = id,
                    sourceName = displayName,
                    title = title,
                    pageUrl = pageUrl,
                    thumbnailUrl = thumb,
                    durationSeconds = SiteProvider.parseDurationToSeconds(durationText),
                    durationFormatted = durationText
                )
            )
        }

        return results
    }
}
