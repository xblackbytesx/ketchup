package com.example.ketchup.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FeedDao {
    @Query("SELECT * FROM feeds ORDER BY title ASC")
    fun observeAll(): Flow<List<FeedEntity>>

    @Query("SELECT * FROM feeds ORDER BY title ASC")
    suspend fun getAllOnce(): List<FeedEntity>

    @Query("SELECT COUNT(*) FROM feeds")
    suspend fun getCount(): Int

    @Upsert
    suspend fun upsertAll(feeds: List<FeedEntity>)

    @Query("DELETE FROM feeds")
    suspend fun deleteAll()

    @Query("DELETE FROM feeds WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE feeds SET title = :title, categoryLabel = :categoryLabel WHERE id = :id")
    suspend fun updateTitleAndCategory(id: String, title: String, categoryLabel: String)
}
