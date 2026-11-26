package com.yourname.aggregator.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.yourname.aggregator.core.model.VideoItem

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey
    val id: String,
    val sourceId: String,
    val sourceName: String,
    val title: String,
    val pageUrl: String,
    val thumbnailUrl: String,
    val previewVideoUrl: String?,
    val durationSeconds: Int,
    val durationFormatted: String,
    val views: Long?,
    val uploadedTimestamp: Long?,
    val tagsCsv: String,
    val savedAt: Long = System.currentTimeMillis()
) {
    fun toVideoItem(): VideoItem = VideoItem(
        id = id,
        sourceId = sourceId,
        sourceName = sourceName,
        title = title,
        pageUrl = pageUrl,
        thumbnailUrl = thumbnailUrl,
        previewVideoUrl = previewVideoUrl,
        durationSeconds = durationSeconds,
        durationFormatted = durationFormatted,
        views = views,
        uploadedTimestamp = uploadedTimestamp,
        tags = if (tagsCsv.isBlank()) emptyList() else tagsCsv.split("|")
    )

    companion object {
        fun fromVideoItem(video: VideoItem): FavoriteEntity = FavoriteEntity(
            id = video.id,
            sourceId = video.sourceId,
            sourceName = video.sourceName,
            title = video.title,
            pageUrl = video.pageUrl,
            thumbnailUrl = video.thumbnailUrl,
            previewVideoUrl = video.previewVideoUrl,
            durationSeconds = video.durationSeconds,
            durationFormatted = video.durationFormatted,
            views = video.views,
            uploadedTimestamp = video.uploadedTimestamp,
            tagsCsv = video.tags.joinToString("|")
        )
    }
}
