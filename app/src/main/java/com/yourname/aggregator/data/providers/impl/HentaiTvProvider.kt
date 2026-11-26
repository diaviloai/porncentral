package com.yourname.aggregator.data.providers.impl

import com.yourname.aggregator.core.model.StreamSource
import com.yourname.aggregator.core.model.VideoItem
import com.yourname.aggregator.core.network.NetworkClient
import com.yourname.aggregator.core.network.SafeFetch
import com.yourname.aggregator.data.providers.SiteProvider
import org.jsoup.nodes.Document
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class HentaiTvProvider : SiteProvider {

    override val id: String = "hentaitv"
    override val displayName: String = "Hentai.tv"
    override val baseUrl: String = "https://hentai.tv"

    override suspend fun search(query: String, page: Int): List<VideoItem> {
        val encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.toString())
        val url = if (page <= 1) {
            "$baseUrl/?s=$encoded"
        } else {
            "$baseUrl/page/$page/?s=$encoded"
        }
        val doc = SafeFetch.fetchDocument(
            url = url,
            referer = "$baseUrl/",
            extraHeaders = mapOf("User-Agent" to NetworkClient.DESKTOP_USER_AGENT)
        )
        return parseCards(doc)
    }

    override suspend fun getTrending(page: Int): List<VideoItem> {
        val url = if (page <= 1) "$baseUrl/" else "$baseUrl/page/$page/"
        val doc = SafeFetch.fetchDocument(
            url = url,
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

    private fun parseCards(doc: Document): List<VideoItem> {
        // Match all episode links directly and walk up to the nearest card wrapper for title & thumbnail
        val episodeLinks = doc.select("a[href*=/hentai/]")
        val seenUrls = HashSet<String>()
        val results = mutableListOf<VideoItem>()

        for (link in episodeLinks) {
            val pageUrl = link.absUrl("href").substringBefore("#")
            if (pageUrl.isBlank() || !seenUrls.add(pageUrl)) continue
            // Skip genre/tag/index links that end right at /hentai/
            if (pageUrl.trimEnd('/').endsWith("/hentai")) continue

            val container = link.parents().firstOrNull { parent ->
                parent.tagName() in setOf("article", "li") ||
                    (parent.tagName() == "div" && parent.selectFirst("img") != null && parent.text().isNotBlank())
            } ?: link.parent() ?: link

            val imgEl = container.selectFirst("img")
            val thumb = listOf("data-src", "data-lazy-src", "data-original", "src")
                .firstNotNullOfOrNull { attr ->
                    imgEl?.absUrl(attr)?.takeIf { it.startsWith("http") && !it.startsWith("data:") }
                }.orEmpty()

            val title = link.attr("title")
                .ifBlank { container.selectFirst("h2, h3, h4, .entry-title, a[title]")?.text().orEmpty() }
                .ifBlank { imgEl?.attr("alt").orEmpty() }
                .ifBlank { link.text() }
                .trim()

            if (title.isBlank() || title.equals("Watch", ignoreCase = true)) continue

            val durationText = container.selectFirst(".duration, span.time")?.text()?.trim().orEmpty()

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
