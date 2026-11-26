package com.yourname.aggregator.core.model

sealed interface StreamSource {
    /** Direct .mp4 or .m3u8 URL playable natively in Media3/ExoPlayer */
    data class DirectStream(
        val url: String,
        val headers: Map<String, String> = emptyMap(),
        val qualityLabel: String = "Auto"
    ) : StreamSource

    /** Fallback when a site obfuscates its stream behind complex JS players */
    data class WebFallback(
        val pageUrl: String
    ) : StreamSource
}
