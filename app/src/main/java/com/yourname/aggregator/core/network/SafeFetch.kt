package com.yourname.aggregator.core.network

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object SafeFetch {

    /**
     * Executes a GET request with coroutine cancellation support and parses HTML via Jsoup.
     */
    suspend fun fetchDocument(
        url: String,
        referer: String? = null,
        extraHeaders: Map<String, String> = emptyMap(),
        client: OkHttpClient = NetworkClient.okHttpClient
    ): Document {
        val html = fetchText(
            url = url,
            referer = referer,
            extraHeaders = extraHeaders,
            client = client
        )
        return Jsoup.parse(html, url)
    }

    /**
     * Executes a GET request and returns the raw response body string.
     */
    suspend fun fetchText(
        url: String,
        referer: String? = null,
        extraHeaders: Map<String, String> = emptyMap(),
        client: OkHttpClient = NetworkClient.okHttpClient
    ): String {
        val requestBuilder = Request.Builder().url(url)
        if (referer != null) {
            requestBuilder.header("Referer", referer)
        }
        extraHeaders.forEach { (key, value) ->
            requestBuilder.header(key, value)
        }

        return client.newCall(requestBuilder.build()).awaitBodyString()
    }

    private suspend fun Call.awaitBodyString(): String =
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation {
                runCatching { cancel() }
            }

            enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(e)
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { res ->
                        if (!res.isSuccessful) {
                            if (continuation.isActive) {
                                continuation.resumeWithException(
                                    IOException("HTTP ${res.code} for ${res.request.url}")
                                )
                            }
                            return
                        }

                        val body = res.body?.string().orEmpty()
                        if (continuation.isActive) {
                            continuation.resume(body)
                        }
                    }
                }
            })
        }
}
