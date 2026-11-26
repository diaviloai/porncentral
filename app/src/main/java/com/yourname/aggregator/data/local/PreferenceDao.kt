package com.yourname.aggregator.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PreferenceDao {

    @Query("SELECT * FROM home_preferences ORDER BY addedAt DESC")
    fun observeKeywords(): Flow<List<PreferenceEntity>>

    @Upsert
    suspend fun upsertKeyword(preference: PreferenceEntity)

    @Query("DELETE FROM home_preferences WHERE keyword = :keyword")
    suspend fun deleteKeyword(keyword: String)

    @Query("DELETE FROM home_preferences")
    suspend fun clearAll()
}
