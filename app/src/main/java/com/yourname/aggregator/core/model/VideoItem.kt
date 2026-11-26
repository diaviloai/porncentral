package com.yourname.aggregator.core.model

data class VideoItem(
    val id: String,                  // Composite key, e.g. "${sourceId}_${rawVideoId}"
    val sourceId: String,            // Matches SiteProvider.id (e.g., "site_a")
    val sourceName: String,          // Display badge (e.g., "Site A")
    val title: String,
    val pageUrl: String,             // Full URL to the video page
    val thumbnailUrl: String,
    val previewVideoUrl: String? = null, // Optional short hover/scrub preview MP4/WebM
    val durationSeconds: Int = 0,    // Normalized seconds for range filtering & sorting
    val durationFormatted: String = "", // Pre-formatted "12:34" for display
    val views: Long? = null,
    val uploadedTimestamp: Long? = null,
    val tags: List<String> = emptyList()
)
