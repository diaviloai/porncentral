package com.yourname.aggregator.data.providers.impl

import com.yourname.aggregator.core.model.StreamSource
import com.yourname.aggregator.core.model.VideoItem
import com.yourname.aggregator.core.network.NetworkClient
import com.yourname.aggregator.core.network.SafeFetch
import com.yourname.aggregator.data.providers.SiteProvider
import org.jsoup.nodes.Document
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class XnxxProvider : SiteProvider {

    override val id: String = "xnxx"
    override val displayName: String = "XNXX"
    override val baseUrl: String = "https://www.xnxx.com"

    override suspend fun search(query: String, page: Int): List<VideoItem> {
        val encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.toString())
        val zeroBasedPage = (page - 1).coerceAtLeast(0)
        val url = if (zeroBasedPage == 0) {
            "$baseUrl/search/$encoded"
        } else {
            "$baseUrl/search/$encoded/$zeroBasedPage"
        }
        val doc = SafeFetch.fetchDocument(url = url, referer = "$baseUrl/")
        return parseCards(doc)
    }

    override suspend fun getTrending(page: Int): List<VideoItem> {
        val zeroBasedPage = (page - 1).coerceAtLeast(0)
        val url = if (zeroBasedPage == 0) {
            "$baseUrl/todays-selection"
        } else {
            "$baseUrl/todays-selection/$zeroBasedPage"
        }
        val doc = SafeFetch.fetchDocument(url = url, referer = "$baseUrl/")
        return parseCards(doc)
    }

    override suspend fun resolveStream(videoPageUrl: String): StreamSource {
        val html = SafeFetch.fetchText(url = videoPageUrl, referer = "$baseUrl/")

        val hlsMatch = Regex("""html5player\.setVideoHLS\(\s*['"]([^'"]+)['"]\s*\)""")
            .find(html)?.groupValues?.getOrNull(1)
        val highMp4Match = Regex("""html5player\.setVideoUrlHigh\(\s*['"]([^'"]+)['"]\s*\)""")
            .find(html)?.groupValues?.getOrNull(1)

        val streamUrl = hlsMatch ?: highMp4Match
        return if (!streamUrl.isNullOrBlank()) {
            StreamSource.DirectStream(
                url = streamUrl,
                headers = mapOf(
                    "Referer" to videoPageUrl,
                    "User-Agent" to NetworkClient.DEFAULT_USER_AGENT
                )
            )
        } else {
            StreamSource.WebFallback(videoPageUrl)
        }
    }

    private fun parseCards(doc: Document): List<VideoItem> {
        val cards = doc.select("div.mozaique div.thumb-block, div.thumb-block")
        return cards.mapNotNull { card ->
            val titleLink = card.selectFirst("div.thumb-under p a, p.title a, p a[title]")
                ?: return@mapNotNull null
            val pageUrl = titleLink.absUrl("href")
            if (pageUrl.isBlank() || !pageUrl.contains("/video")) return@mapNotNull null

            val title = titleLink.attr("title").ifBlank { titleLink.text() }.trim()
            if (title.isBlank()) return@mapNotNull null

            val imgEl = card.selectFirst("div.thumb img, img")
            val thumb = listOf("data-src", "src")
                .firstNotNullOfOrNull { attr ->
                    imgEl?.absUrl(attr)?.takeIf { it.startsWith("http") && !it.contains("lightbox") }
                }.orEmpty()

            val metaText = card.selectFirst("p.metadata")?.text().orEmpty()
            val durationText = Regex("""(\d+(?::\d{2}){1,2})""")
                .find(metaText)?.groupValues?.getOrNull(1).orEmpty()

            val rawId = card.attr("data-id")
                .ifBlank { pageUrl.hashCode().toUInt().toString(16) }

            VideoItem(
                id = "${id}_$rawId",
                sourceId = id,
                sourceName = displayName,
                title = title,
                pageUrl = pageUrl,
                thumbnailUrl = thumb,
                durationSeconds = SiteProvider.parseDurationToSeconds(durationText),
                durationFormatted = durationText
            )
        }
    }
}
