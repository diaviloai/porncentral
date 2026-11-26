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
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
        val url = if (page <= 1) {
            "$baseUrl/?s=$encoded"
        } else {
            "$baseUrl/page/$page/?s=$encoded"
        }
        val doc = SafeFetch.fetchDocument(url = url, referer = "$baseUrl/")
        return parseCards(doc)
    }

    override suspend fun getTrending(page: Int): List<VideoItem> {
        val url = if (page <= 1) "$baseUrl/trending/" else "$baseUrl/trending/page/$page/"
        val doc = SafeFetch.fetchDocument(url = url, referer = "$baseUrl/")
        return parseCards(doc)
    }

    override suspend fun resolveStream(videoPageUrl: String): StreamSource {
        val html = SafeFetch.fetchText(url = videoPageUrl, referer = "$baseUrl/")
        val streamRegex = Regex("""(https?:\\?/\\?/[^"'\s\\]+\.(?:m3u8|mp4)(?:\?[^"'\s\\]*)?)""")
        val streamUrl = streamRegex.find(html)
            ?.groupValues
            ?.getOrNull(1)
            ?.replace("\\/", "/")

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
        val cards = doc.select("div.crsl-slde, article, div.flex.flex-col")
        return cards.mapNotNull { card ->
            val linkEl = card.selectFirst("a[href*=/hentai/], a[title]") ?: return@mapNotNull null
            val pageUrl = linkEl.absUrl("href")
            if (pageUrl.isBlank() || pageUrl == "$baseUrl/") return@mapNotNull null

            val imgEl = card.selectFirst("img")
            val title = linkEl.attr("title")
                .ifBlank { imgEl?.attr("alt").orEmpty() }
                .ifBlank { card.selectFirst("h2, h3, .title")?.text().orEmpty() }
                .trim()
            if (title.isBlank()) return@mapNotNull null

            val thumb = listOf("data-src", "data-lazy-src", "src")
                .firstNotNullOfOrNull { attr ->
                    imgEl?.absUrl(attr)?.takeIf { it.startsWith("http") && !it.startsWith("data:") }
                }.orEmpty()

            val durationText = card.selectFirst(".duration, span.time")?.text()?.trim().orEmpty()

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
        }.distinctBy { it.pageUrl }
    }
}
