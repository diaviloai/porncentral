package com.yourname.aggregator.data.providers.impl

import com.yourname.aggregator.core.model.StreamSource
import com.yourname.aggregator.core.model.VideoItem
import com.yourname.aggregator.core.network.SafeFetch
import com.yourname.aggregator.data.providers.SiteProvider
import org.jsoup.nodes.Document
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class TsuminoProvider : SiteProvider {

    override val id: String = "tsumino"
    override val displayName: String = "Tsumino"
    override val baseUrl: String = "https://www.tsumino.com"

    override suspend fun search(query: String, page: Int): List<VideoItem> {
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
        val url = "$baseUrl/Books?Text=$encoded&PageNumber=$page"
        val doc = SafeFetch.fetchDocument(url = url, referer = "$baseUrl/")
        return parseCards(doc)
    }

    override suspend fun getTrending(page: Int): List<VideoItem> {
        val url = "$baseUrl/Books?Sort=Popularity&PageNumber=$page"
        val doc = SafeFetch.fetchDocument(url = url, referer = "$baseUrl/")
        return parseCards(doc)
    }

    /** Tsumino is a gallery/reader site, so always open in the built-in WebView. */
    override suspend fun resolveStream(videoPageUrl: String): StreamSource {
        return StreamSource.WebFallback(videoPageUrl)
    }

    private fun parseCards(doc: Document): List<VideoItem> {
        val cards = doc.select("div.book-grid-item, div.book-item")
        return cards.mapNotNull { card ->
            val linkEl = card.selectFirst("a[href*=/entry/]") ?: return@mapNotNull null
            val pageUrl = linkEl.absUrl("href")
            if (pageUrl.isBlank()) return@mapNotNull null

            val title = card.selectFirst("div.book-title, .title")?.text()?.trim()
                .orEmpty()
                .ifBlank { linkEl.attr("title").trim() }
            if (title.isBlank()) return@mapNotNull null

            val imgEl = card.selectFirst("img")
            val thumb = listOf("data-src", "src")
                .firstNotNullOfOrNull { attr ->
                    imgEl?.absUrl(attr)?.takeIf { it.startsWith("http") }
                }.orEmpty()

            val pagesLabel = card.selectFirst("div.book-pages, .pages")?.text()?.trim().orEmpty()
            val entryId = pageUrl.substringAfter("/entry/", "").substringBefore("/")
                .ifBlank { pageUrl.hashCode().toUInt().toString(16) }

            VideoItem(
                id = "${id}_$entryId",
                sourceId = id,
                sourceName = displayName,
                title = title,
                pageUrl = pageUrl,
                thumbnailUrl = thumb,
                durationFormatted = pagesLabel
            )
        }
    }
}
