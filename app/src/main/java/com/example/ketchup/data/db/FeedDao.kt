package com.example.ketchup.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FeedDao {
    @Query("SELECT * FROM feeds ORDER BY title ASC")
    fun observeAll(): Flow<List<FeedEntity>>

    @Upsert
    suspend fun upsertAll(feeds: List<FeedEntity>)

    @Query("DELETE FROM feeds")
    suspend fun deleteAll()
}
