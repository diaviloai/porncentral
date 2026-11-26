package com.yourname.aggregator.data.providers.impl

import com.yourname.aggregator.core.model.StreamSource
import com.yourname.aggregator.core.model.VideoItem
import com.yourname.aggregator.core.network.NetworkClient
import com.yourname.aggregator.core.network.SafeFetch
import com.yourname.aggregator.data.providers.SiteProvider
import org.jsoup.nodes.Document
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class PornhatProvider : SiteProvider {

    override val id: String = "pornhat"
    override val displayName: String = "PornHat"
    override val baseUrl: String = "https://www.pornhat.com"

    override suspend fun search(query: String, page: Int): List<VideoItem> {
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
        val url = "$baseUrl/search/$encoded/?page=$page"
        val doc = SafeFetch.fetchDocument(url = url, referer = "$baseUrl/")
        return parseCards(doc)
    }

    override suspend fun getTrending(page: Int): List<VideoItem> {
        val url = "$baseUrl/popular/?page=$page"
        val doc = SafeFetch.fetchDocument(url = url, referer = "$baseUrl/")
        return parseCards(doc)
    }

    override suspend fun resolveStream(videoPageUrl: String): StreamSource {
        val html = SafeFetch.fetchText(
            url = videoPageUrl,
            referer = "$baseUrl/",
            extraHeaders = mapOf("User-Agent" to NetworkClient.DESKTOP_USER_AGENT)
        )

        // KVS player used by PornHat stores video_url / video_alt_url in flashvars
        val kvsRegex = Regex("""video_(?:alt_)?url\d*\s*:\s*['"](https?://[^'"]+)['"]""")
        val genericRegex = Regex("""(https?:\\?/\\?/[^"'\s\\<>]+\.(?:m3u8|mp4)(?:\?[^"'\s\\<>]*)?)""")

        val candidates = (
            kvsRegex.findAll(html).map { it.groupValues[1] } +
                genericRegex.findAll(html).map { it.groupValues[1].replace("\\/", "/") }
            ).filter { url ->
            url.startsWith("http") &&
                !url.contains("preview", ignoreCase = true) &&
                !url.contains("thumb", ignoreCase = true)
        }.toList()

        val bestUrl = candidates.firstOrNull()

        return if (!bestUrl.isNullOrBlank()) {
            StreamSource.DirectStream(
                url = bestUrl,
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
        val cards = doc.select("div.item, div.thumb")
        return cards.mapNotNull { card ->
            val linkEl = card.selectFirst("a[href*=/video/], a[title]") ?: return@mapNotNull null
            val pageUrl = linkEl.absUrl("href")
            if (pageUrl.isBlank()) return@mapNotNull null

            val title = linkEl.attr("title")
                .ifBlank { card.selectFirst(".title, h2, h3")?.text().orEmpty() }
                .trim()
            if (title.isBlank()) return@mapNotNull null

            val imgEl = card.selectFirst("img")
            val thumb = listOf("data-original", "data-src", "src")
                .firstNotNullOfOrNull { attr ->
                    imgEl?.absUrl(attr)?.takeIf { it.startsWith("http") }
                }.orEmpty()

            val durationText = card.selectFirst(".duration, span.time")?.text()?.trim().orEmpty()
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
