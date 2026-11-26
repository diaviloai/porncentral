package com.yourname.aggregator.data.providers.impl

import com.yourname.aggregator.core.model.StreamSource
import com.yourname.aggregator.core.model.VideoItem
import com.yourname.aggregator.core.network.NetworkClient
import com.yourname.aggregator.core.network.SafeFetch
import com.yourname.aggregator.data.providers.SiteProvider
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class BellesaProvider : SiteProvider {

    override val id: String = "bellesa"
    override val displayName: String = "Bellesa"
    override val baseUrl: String = "https://www.bellesa.co"

    override suspend fun search(query: String, page: Int): List<VideoItem> {
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
        val apiUrl = "$baseUrl/api/rest/v1/videos?filter[search]=$encoded&page=$page&limit=24"
        return fetchFromApiOrHtml(apiUrl, fallbackUrl = "$baseUrl/search?q=$encoded")
    }

    override suspend fun getTrending(page: Int): List<VideoItem> {
        val apiUrl = "$baseUrl/api/rest/v1/videos?sort=-views&page=$page&limit=24"
        return fetchFromApiOrHtml(apiUrl, fallbackUrl = "$baseUrl/videos")
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

    private suspend fun fetchFromApiOrHtml(apiUrl: String, fallbackUrl: String): List<VideoItem> {
        val raw = runCatching {
            SafeFetch.fetchText(
                url = apiUrl,
                referer = "$baseUrl/",
                extraHeaders = mapOf("Accept" to "application/json")
            )
        }.getOrNull()

        if (!raw.isNullOrBlank() && (raw.trimStart().startsWith("{") || raw.trimStart().startsWith("["))) {
            val parsed = parseJsonVideos(raw)
            if (parsed.isNotEmpty()) return parsed
        }

        val doc = SafeFetch.fetchDocument(url = fallbackUrl, referer = "$baseUrl/")
        return doc.select("a[href*=/videos/]").mapNotNull { link ->
            val pageUrl = link.absUrl("href")
            val img = link.selectFirst("img")
            val title = link.attr("title").ifBlank { img?.attr("alt").orEmpty() }.ifBlank { link.text() }.trim()
            if (pageUrl.isBlank() || title.isBlank()) return@mapNotNull null

            val thumb = img?.absUrl("data-src")?.ifBlank { img.absUrl("src") }.orEmpty()
            VideoItem(
                id = "${id}_${pageUrl.hashCode().toUInt().toString(16)}",
                sourceId = id,
                sourceName = displayName,
                title = title,
                pageUrl = pageUrl,
                thumbnailUrl = thumb
            )
        }.distinctBy { it.pageUrl }
    }

    private fun parseJsonVideos(rawJson: String): List<VideoItem> {
        val itemsArray: JSONArray = if (rawJson.trimStart().startsWith("[")) {
            JSONArray(rawJson)
        } else {
            val root = JSONObject(rawJson)
            root.optJSONArray("data") ?: root.optJSONArray("videos") ?: return emptyList()
        }

        val results = mutableListOf<VideoItem>()
        for (i in 0 until itemsArray.length()) {
            val obj = itemsArray.optJSONObject(i) ?: continue
            val rawId = obj.optString("id").ifBlank { i.toString() }
            val title = obj.optString("title").trim()
            val slug = obj.optString("slug")
            val pageUrl = obj.optString("url").ifBlank {
                if (slug.isNotBlank()) "$baseUrl/videos/$rawId/$slug" else "$baseUrl/videos/$rawId"
            }
            if (title.isBlank()) continue

            val thumb = obj.optString("image")
                .ifBlank { obj.optString("thumbnail") }
                .ifBlank { obj.optString("poster") }

            val durationSec = obj.optInt("duration", 0)
            val durationFormatted = if (durationSec > 0) {
                val mins = durationSec / 60
                val secs = durationSec % 60
                "%d:%02d".format(mins, secs)
            } else {
                ""
            }

            results.add(
                VideoItem(
                    id = "${id}_$rawId",
                    sourceId = id,
                    sourceName = displayName,
                    title = title,
                    pageUrl = pageUrl,
                    thumbnailUrl = thumb,
                    durationSeconds = durationSec,
                    durationFormatted = durationFormatted
                )
            )
        }
        return results
    }
}
