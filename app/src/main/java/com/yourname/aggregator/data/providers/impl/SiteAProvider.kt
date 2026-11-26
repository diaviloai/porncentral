package com.yourname.aggregator.data.providers.impl

import com.yourname.aggregator.core.model.StreamSource
import com.yourname.aggregator.core.model.VideoItem
import com.yourname.aggregator.core.network.NetworkClient
import com.yourname.aggregator.core.network.SafeFetch
import com.yourname.aggregator.data.providers.SiteProvider
import org.jsoup.nodes.Document
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class SiteAProvider : SiteProvider {

    override val id: String = "site_a"
    override val displayName: String = "Site A"
    override val baseUrl: String = "https://example.com"

    override suspend fun search(query: String, page: Int): List<VideoItem> {
        val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
        val searchUrl = "$baseUrl/search?q=$encodedQuery&page=$page"
        val doc = SafeFetch.fetchDocument(url = searchUrl, referer = "$baseUrl/")
        return parseVideoCards(doc)
    }

    override suspend fun getTrending(page: Int): List<VideoItem> {
        val trendingUrl = "$baseUrl/trending?page=$page"
        val doc = SafeFetch.fetchDocument(url = trendingUrl, referer = "$baseUrl/")
        return parseVideoCards(doc)
    }

    override suspend fun resolveStream(videoPageUrl: String): StreamSource {
        val rawHtml = SafeFetch.fetchText(url = videoPageUrl, referer = "$baseUrl/")

        // Look for direct HLS (.m3u8) or MP4 stream URLs embedded in page HTML/JS
        val streamRegex = Regex("""(https?:\\?/\\?/[^"'\s\\]+\.(?:m3u8|mp4)(?:\?[^"'\s\\]*)?)""")
        val matchedUrl = streamRegex.find(rawHtml)
            ?.groupValues
            ?.getOrNull(1)
            ?.replace("\\/", "/")

        return if (!matchedUrl.isNullOrBlank()) {
            StreamSource.DirectStream(
                url = matchedUrl,
                headers = mapOf(
                    "Referer" to videoPageUrl,
                    "User-Agent" to NetworkClient.DEFAULT_USER_AGENT
                )
            )
        } else {
            StreamSource.WebFallback(videoPageUrl)
        }
    }

    private fun parseVideoCards(doc: Document): List<VideoItem> {
        // Replace these CSS selectors with the target site's actual card markup
        val cards = doc.select("div.video-card, article.video-item")

        return cards.mapNotNull { card ->
            val linkEl = card.selectFirst("a.video-link, a[href]") ?: return@mapNotNull null
            val pageUrl = linkEl.absUrl("href")
            if (pageUrl.isBlank()) return@mapNotNull null

            val title = card.selectFirst(".video-title, h3, img")?.let { el ->
                el.attr("title").ifBlank { el.attr("alt").ifBlank { el.text() } }
            }?.trim().orEmpty()
            if (title.isBlank()) return@mapNotNull null

            // Check lazy-loading attributes first before falling back to standard src
            val imgEl = card.selectFirst("img")
            val thumbnailUrl = listOf("data-src", "data-original", "data-lazy", "src")
                .firstNotNullOfOrNull { attr ->
                    imgEl?.absUrl(attr)?.takeIf { it.startsWith("http") }
                }.orEmpty()

            val previewVideoUrl = card.attr("data-preview")
                .ifBlank { card.selectFirst("video source")?.absUrl("src").orEmpty() }
                .takeIf { it.startsWith("http") }

            val durationText = card.selectFirst(".duration, span.time")?.text()?.trim().orEmpty()
            val durationSeconds = SiteProvider.parseDurationToSeconds(durationText)

            val tags = card.select(".tags a, .tag-chip")
                .map { it.text().trim() }
                .filter { it.isNotEmpty() }

            val rawId = card.attr("data-id")
                .ifBlank { pageUrl.hashCode().toUInt().toString(16) }

            VideoItem(
                id = "${id}_$rawId",
                sourceId = id,
                sourceName = displayName,
                title = title,
                pageUrl = pageUrl,
                thumbnailUrl = thumbnailUrl,
                previewVideoUrl = previewVideoUrl,
                durationSeconds = durationSeconds,
                durationFormatted = durationText,
                tags = tags
            )
        }
    }
}
