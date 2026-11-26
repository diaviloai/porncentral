package com.yourname.aggregator.data.providers.impl

import com.yourname.aggregator.core.model.StreamSource
import com.yourname.aggregator.core.model.VideoItem
import com.yourname.aggregator.core.network.NetworkClient
import com.yourname.aggregator.core.network.SafeFetch
import com.yourname.aggregator.data.providers.SiteProvider
import org.jsoup.nodes.Document
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class PornhubProvider : SiteProvider {

    override val id: String = "pornhub"
    override val displayName: String = "Pornhub"
    override val baseUrl: String = "https://www.pornhub.com"

    override suspend fun search(query: String, page: Int): List<VideoItem> {
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
        val url = "$baseUrl/video/search?search=$encoded&page=$page"
        val doc = SafeFetch.fetchDocument(url = url, referer = "$baseUrl/")
        return parseCards(doc)
    }

    override suspend fun getTrending(page: Int): List<VideoItem> {
        val url = "$baseUrl/video?page=$page"
        val doc = SafeFetch.fetchDocument(url = url, referer = "$baseUrl/")
        return parseCards(doc)
    }

    override suspend fun resolveStream(videoPageUrl: String): StreamSource {
        val html = SafeFetch.fetchText(url = videoPageUrl, referer = "$baseUrl/")

        val streamRegex = Regex("""(https?:\\?/\\?/[^"'\s\\]+\.(?:m3u8|mp4)(?:\?[^"'\s\\]*)?)""")
        val streamUrl = streamRegex.findAll(html)
            .map { it.groupValues[1].replace("\\/", "/") }
            .firstOrNull { !it.contains("preview", ignoreCase = true) }

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
        val cards = doc.select("li.pcVideoListItem, div.videoblock")
        return cards.mapNotNull { card ->
            val linkEl = card.selectFirst("a[href*=/view_video.php]") ?: return@mapNotNull null
            val pageUrl = linkEl.absUrl("href")
            if (pageUrl.isBlank()) return@mapNotNull null

            val title = card.selectFirst("span.title a, a[title]")?.let { el ->
                el.attr("title").ifBlank { el.text() }
            }?.trim().orEmpty()
            if (title.isBlank()) return@mapNotNull null

            val imgEl = card.selectFirst("img")
            val thumb = listOf("data-mediumthumb", "data-thumb_url", "data-src", "src")
                .firstNotNullOfOrNull { attr ->
                    imgEl?.absUrl(attr)?.takeIf { it.startsWith("http") && !it.contains("blank.gif") }
                }.orEmpty()

            val preview = imgEl?.attr("data-mediabook")?.takeIf { it.startsWith("http") }

            val durationText = card.selectFirst("var.duration, .duration")?.text()?.trim().orEmpty()
            val vkey = card.attr("data-video-vkey").ifBlank {
                pageUrl.substringAfter("viewkey=", "").substringBefore("&")
            }.ifBlank { pageUrl.hashCode().toUInt().toString(16) }

            VideoItem(
                id = "${id}_$vkey",
                sourceId = id,
                sourceName = displayName,
                title = title,
                pageUrl = pageUrl,
                thumbnailUrl = thumb,
                previewVideoUrl = preview,
                durationSeconds = SiteProvider.parseDurationToSeconds(durationText),
                durationFormatted = durationText
            )
        }
    }
}
