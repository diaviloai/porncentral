package com.yourname.aggregator.core.network

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object NetworkClient {

    const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 15; SM-G998U) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

    /** Lightweight session cookie store so age-gate cookies persist during a session. */
    private val memoryCookieJar = object : CookieJar {
        private val store = ConcurrentHashMap<String, List<Cookie>>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val existing = store[url.host].orEmpty().associateBy { it.name }.toMutableMap()
            cookies.forEach { existing[it.name] = it }
            store[url.host] = existing.values.toList()
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val now = System.currentTimeMillis()
            return store[url.host].orEmpty().filter { it.expiresAt >= now }
        }
    }

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cookieJar(memoryCookieJar)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor { chain ->
                val original = chain.request()
                val defaultReferer = "${original.url.scheme}://${original.url.host}/"

                val requestWithHeaders = original.newBuilder()
                    .header("User-Agent", original.header("User-Agent") ?: DEFAULT_USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("Referer", original.header("Referer") ?: defaultReferer)
                    .build()

                chain.proceed(requestWithHeaders)
            }
            .build()
    }
}
