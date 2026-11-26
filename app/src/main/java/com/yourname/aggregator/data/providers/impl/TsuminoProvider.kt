package com.yourname.aggregator.data.providers.impl

import com.yourname.aggregator.core.model.StreamSource
import com.yourname.aggregator.core.model.VideoItem
import com.yourname.aggregator.core.network.NetworkClient
import com.yourname.aggregator.core.network.SafeFetch
import com.yourname.aggregator.data.providers.SiteProvider
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import org.jsoup.nodes.Document
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class TsuminoProvider : SiteProvider {

    override val id: String = "tsumino"
    override val displayName: String = "Tsumino"
    override val baseUrl: String = "https://www.tsumino.com"

    override suspend fun search(query: String, page: Int): List<VideoItem> {
        val operateItems = runCatching {
            queryOperateApi(text = query.trim(), sort = "Newest", page = page)
        }.getOrNull()
        if (!operateItems.isNullOrEmpty()) return operateItems

        val encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.toString())
        val url = "$baseUrl/Books?Text=$encoded&PageNumber=$page"
        val doc = SafeFetch.fetchDocument(url = url, referer = "$baseUrl/")
        return parseCards(doc)
    }

    override suspend fun getTrending(page: Int): List<VideoItem> {
        val operateItems = runCatching {
            queryOperateApi(text = "", sort = "Popularity", page = page)
        }.getOrNull()
        if (!operateItems.isNullOrEmpty()) return operateItems

        val url = "$baseUrl/Books?Sort=Popularity&PageNumber=$page"
        val doc = SafeFetch.fetchDocument(url = url, referer = "$baseUrl/")
        return parseCards(doc)
    }

    /** Tsumino is a gallery/reader site, so always open in the built-in WebView. */
    override suspend fun resolveStream(videoPageUrl: String): StreamSource {
        return StreamSource.WebFallback(videoPageUrl)
    }

    private suspend fun queryOperateApi(
        text: String,
        sort: String,
        page: Int
    ): List<VideoItem> {
        val formBody = FormBody.Builder()
            .add("PageNumber", page.toString())
            .add("Text", text)
            .add("Sort", sort)
            .add("List", "0")
            .add("Length", "0")
            .add("MinimumRating", "0")
            .build()

        val request = Request.Builder()
            .url("$baseUrl/Books/Operate")
            .post(formBody)
            .header("Referer", "$baseUrl/Books")
            .header("Origin", baseUrl)
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Accept", "application/json, text/javascript, */*; q=0.01")
            .build()

        val rawJson = NetworkClient.okHttpClient.newCall(request).awaitString()
        val root = JSONObject(rawJson)
        val dataArray = root.optJSONArray("data") ?: return emptyList()

        val results = mutableListOf<VideoItem>()
        for (i in 0 until dataArray.length()) {
            val wrapper = dataArray.optJSONObject(i) ?: continue
            val entry = wrapper.optJSONObject("entry") ?: wrapper
            val entryId = entry.optInt("id", 0)
            val title = entry.optString("title").trim()
            if (entryId == 0 || title.isBlank()) continue

            val thumb = entry.optString("thumbnailUrl")
                .ifBlank { "https://content.tsumino.com/thumbs/$entryId/1" }
            val pages = entry.optInt("pages", 0)

            results.add(
                VideoItem(
                    id = "${id}_$entryId",
                    sourceId = id,
                    sourceName = displayName,
                    title = title,
                    pageUrl = "$baseUrl/entry/$entryId",
                    thumbnailUrl = thumb,
                    durationFormatted = if (pages > 0) "$pages pages" else ""
                )
            )
        }
        return results
    }

    private suspend fun Call.awaitString(): String =
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { runCatching { cancel() } }
            enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { res ->
                        if (!res.isSuccessful) {
                            if (continuation.isActive) {
                                continuation.resumeWithException(IOException("HTTP ${res.code}"))
                            }
                            return
                        }
                        val body = res.body?.string().orEmpty()
                        if (continuation.isActive) continuation.resume(body)
                    }
                }
            })
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
