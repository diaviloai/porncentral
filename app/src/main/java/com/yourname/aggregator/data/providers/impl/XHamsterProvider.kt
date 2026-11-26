package com.yourname.aggregator.data.providers.impl

import com.yourname.aggregator.core.model.StreamSource
import com.yourname.aggregator.core.model.VideoItem
import com.yourname.aggregator.core.network.NetworkClient
import com.yourname.aggregator.core.network.SafeFetch
import com.yourname.aggregator.data.providers.SiteProvider
import org.jsoup.nodes.Document
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class XHamsterProvider : SiteProvider {

    override val id: String = "xhamster"
    override val displayName: String = "xHamster"
    override val baseUrl: String = "https://xhamster.com"

    override suspend fun search(query: String, page: Int): List<VideoItem> {
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
        val url = "$baseUrl/search/$encoded?page=$page"
        val doc = SafeFetch.fetchDocument(url = url, referer = "$baseUrl/")
        return parseCards(doc)
    }

    override suspend fun getTrending(page: Int): List<VideoItem> {
        val url = "$baseUrl/best/weekly?page=$page"
        val doc = SafeFetch.fetchDocument(url = url, referer = "$baseUrl/")
        return parseCards(doc)
    }

    override suspend fun resolveStream(videoPageUrl: String): StreamSource {
        val desktopUrl = videoPageUrl.replace("://m.xhamster.com", "://xhamster.com")
        val html = SafeFetch.fetchText(
            url = desktopUrl,
            referer = "$baseUrl/",
            extraHeaders = mapOf("User-Agent" to NetworkClient.DESKTOP_USER_AGENT)
        )

        val qualityRegex = Regex(""""(?:720p|480p|1080p|240p|url|fallback)"\s*:\s*"(https?:\\?/\\?/[^"]+\.(?:m3u8|mp4)[^"]*)"""")
        val genericRegex = Regex("""(https?:\\?/\\?/[^"'\s\\<>]+\.(?:m3u8|mp4)(?:\?[^"'\s\\<>]*)?)""")

        val candidates = (
            qualityRegex.findAll(html).map { it.groupValues[1].replace("\\/", "/") } +
                genericRegex.findAll(html).map { it.groupValues[1].replace("\\/", "/") }
            ).filter { url ->
            url.startsWith("http") &&
                !url.contains("preview", ignoreCase = true) &&
                !url.contains("thumb", ignoreCase = true)
        }.toList()

        val bestUrl = candidates.firstOrNull { it.contains(".m3u8") || it.contains("720p") }
            ?: candidates.firstOrNull()

        return if (!bestUrl.isNullOrBlank()) {
            StreamSource.DirectStream(
                url = bestUrl,
                headers = mapOf(
                    "Referer" to desktopUrl,
                    "User-Agent" to NetworkClient.DESKTOP_USER_AGENT
                )
            )
        } else {
            StreamSource.WebFallback(videoPageUrl)
        }
    }

    private fun parseCards(doc: Document): List<VideoItem> {
        val cards = doc.select("div.thumb-list__item, div.video-thumb")
        return cards.mapNotNull { card ->
            val linkEl = card.selectFirst("a.video-thumb__image-container, a.video-thumb-info__name, a[href*=/videos/]")
                ?: return@mapNotNull null
            val pageUrl = linkEl.absUrl("href")
            if (pageUrl.isBlank()) return@mapNotNull null

            val titleEl = card.selectFirst("a.video-thumb-info__name, img")
            val title = titleEl?.let { el ->
                el.attr("alt").ifBlank { el.attr("title").ifBlank { el.text() } }
            }?.trim().orEmpty()
            if (title.isBlank()) return@mapNotNull null

            val imgEl = card.selectFirst("img")
            val thumb = listOf("data-src", "src")
                .firstNotNullOfOrNull { attr ->
                    imgEl?.absUrl(attr)?.takeIf { it.startsWith("http") }
                }.orEmpty()

            val durationText = card.selectFirst("div.thumb-image-container__duration, time, .duration")
                ?.text()?.trim().orEmpty()

            val rawId = card.attr("data-video-id")
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
