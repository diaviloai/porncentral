package com.yourname.aggregator.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "home_preferences")
data class PreferenceEntity(
    @PrimaryKey
    val keyword: String,
    val addedAt: Long = System.currentTimeMillis()
)
