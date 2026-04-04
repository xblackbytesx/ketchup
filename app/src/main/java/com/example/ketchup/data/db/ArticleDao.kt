package com.example.ketchup.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ArticleDao {
    @Query("SELECT * FROM articles ORDER BY publishedMs DESC")
    fun observeAll(): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE isRead = 0 ORDER BY publishedMs DESC")
    fun observeUnread(): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles ORDER BY publishedMs ASC")
    fun observeAllOldestFirst(): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE isRead = 0 ORDER BY publishedMs ASC")
    fun observeUnreadOldestFirst(): Flow<List<ArticleEntity>>

    // Today
    @Query("SELECT * FROM articles WHERE publishedMs >= :startOfDayMs ORDER BY publishedMs DESC")
    fun observeToday(startOfDayMs: Long): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE publishedMs >= :startOfDayMs AND isRead = 0 ORDER BY publishedMs DESC")
    fun observeTodayUnread(startOfDayMs: Long): Flow<List<ArticleEntity>>

    // By single feed
    @Query("SELECT * FROM articles WHERE feedId = :feedId ORDER BY publishedMs DESC")
    fun observeByFeed(feedId: String): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE feedId = :feedId AND isRead = 0 ORDER BY publishedMs DESC")
    fun observeByFeedUnread(feedId: String): Flow<List<ArticleEntity>>

    // By multiple feeds (for category)
    @Query("SELECT * FROM articles WHERE feedId IN (:feedIds) ORDER BY publishedMs DESC")
    fun observeByFeeds(feedIds: List<String>): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE feedId IN (:feedIds) AND isRead = 0 ORDER BY publishedMs DESC")
    fun observeByFeedsUnread(feedIds: List<String>): Flow<List<ArticleEntity>>

    // Unread counts per feed for badges
    @Query("SELECT feedId, COUNT(*) as count FROM articles WHERE isRead = 0 GROUP BY feedId")
    fun observeUnreadCountsByFeed(): Flow<List<FeedUnreadCount>>

    @Upsert
    suspend fun upsertAll(articles: List<ArticleEntity>)

    @Query("UPDATE articles SET isRead = :isRead WHERE id = :id")
    suspend fun updateReadState(id: String, isRead: Boolean)

    @Query("UPDATE articles SET isStarred = :isStarred WHERE id = :id")
    suspend fun updateStarred(id: String, isStarred: Boolean)

    @Query("UPDATE articles SET fetchedContent = :content, fetchedAt = :fetchedAt WHERE id = :id")
    suspend fun updateFetchedContent(id: String, content: String, fetchedAt: Long)

    @Query("SELECT * FROM articles WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ArticleEntity?

    @Query("UPDATE articles SET fetchedContent = NULL, fetchedAt = NULL WHERE fetchedAt IS NOT NULL")
    suspend fun clearFetchedContent()

    @Query("SELECT * FROM articles WHERE isStarred = 1 ORDER BY publishedMs DESC")
    fun observeStarred(): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE feedId = :feedId")
    suspend fun getByFeedId(feedId: String): List<ArticleEntity>

    @Query("DELETE FROM articles WHERE feedId = :feedId")
    suspend fun deleteByFeedId(feedId: String)

    // ID-only queries for navigation — avoids deserializing full entities (especially fetchedContent)
    @Query("SELECT id FROM articles ORDER BY publishedMs DESC")
    suspend fun getAllArticleIds(): List<String>

    @Query("SELECT id FROM articles ORDER BY publishedMs ASC")
    suspend fun getAllArticleIdsOldestFirst(): List<String>

    @Query("SELECT id FROM articles WHERE isRead = 0 ORDER BY publishedMs DESC")
    suspend fun getUnreadArticleIds(): List<String>

    @Query("SELECT id FROM articles WHERE isRead = 0 ORDER BY publishedMs ASC")
    suspend fun getUnreadArticleIdsOldestFirst(): List<String>

    @Query("SELECT id FROM articles WHERE publishedMs >= :startOfDayMs ORDER BY publishedMs DESC")
    suspend fun getTodayArticleIds(startOfDayMs: Long): List<String>

    @Query("SELECT id FROM articles WHERE publishedMs >= :startOfDayMs AND isRead = 0 ORDER BY publishedMs DESC")
    suspend fun getTodayUnreadArticleIds(startOfDayMs: Long): List<String>

    @Query("SELECT id FROM articles WHERE feedId = :feedId ORDER BY publishedMs DESC")
    suspend fun getArticleIdsByFeed(feedId: String): List<String>

    @Query("SELECT id FROM articles WHERE feedId = :feedId AND isRead = 0 ORDER BY publishedMs DESC")
    suspend fun getArticleIdsByFeedUnread(feedId: String): List<String>

    @Query("SELECT id FROM articles WHERE feedId IN (:feedIds) ORDER BY publishedMs DESC")
    suspend fun getArticleIdsByFeeds(feedIds: List<String>): List<String>

    @Query("SELECT id FROM articles WHERE feedId IN (:feedIds) AND isRead = 0 ORDER BY publishedMs DESC")
    suspend fun getArticleIdsByFeedsUnread(feedIds: List<String>): List<String>

    @Query("SELECT id FROM articles WHERE isStarred = 1 ORDER BY publishedMs DESC")
    suspend fun getStarredArticleIds(): List<String>
}
