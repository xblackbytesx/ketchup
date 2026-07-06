package com.example.ketchup.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// Columns for list screens — everything except the fetchedContent blob.
private const val LIST_COLS =
    "id, title, url, author, publishedMs, feedTitle, feedId, summary, " +
        "thumbnailUrl, isRead, isStarred, fetchedAt, sourceFaviconUrl"

@Dao
interface ArticleDao {
    @Query("SELECT $LIST_COLS FROM articles ORDER BY publishedMs DESC")
    fun observeAll(): Flow<List<ArticleListEntity>>

    @Query("SELECT $LIST_COLS FROM articles WHERE isRead = 0 ORDER BY publishedMs DESC")
    fun observeUnread(): Flow<List<ArticleListEntity>>

    @Query("SELECT $LIST_COLS FROM articles ORDER BY publishedMs ASC")
    fun observeAllOldestFirst(): Flow<List<ArticleListEntity>>

    @Query("SELECT $LIST_COLS FROM articles WHERE isRead = 0 ORDER BY publishedMs ASC")
    fun observeUnreadOldestFirst(): Flow<List<ArticleListEntity>>

    // Today
    @Query("SELECT $LIST_COLS FROM articles WHERE publishedMs >= :startOfDayMs ORDER BY publishedMs DESC")
    fun observeToday(startOfDayMs: Long): Flow<List<ArticleListEntity>>

    @Query("SELECT $LIST_COLS FROM articles WHERE publishedMs >= :startOfDayMs AND isRead = 0 ORDER BY publishedMs DESC")
    fun observeTodayUnread(startOfDayMs: Long): Flow<List<ArticleListEntity>>

    // By single feed
    @Query("SELECT $LIST_COLS FROM articles WHERE feedId = :feedId ORDER BY publishedMs DESC")
    fun observeByFeed(feedId: String): Flow<List<ArticleListEntity>>

    @Query("SELECT $LIST_COLS FROM articles WHERE feedId = :feedId AND isRead = 0 ORDER BY publishedMs DESC")
    fun observeByFeedUnread(feedId: String): Flow<List<ArticleListEntity>>

    // By multiple feeds (for category)
    @Query("SELECT $LIST_COLS FROM articles WHERE feedId IN (:feedIds) ORDER BY publishedMs DESC")
    fun observeByFeeds(feedIds: List<String>): Flow<List<ArticleListEntity>>

    @Query("SELECT $LIST_COLS FROM articles WHERE feedId IN (:feedIds) AND isRead = 0 ORDER BY publishedMs DESC")
    fun observeByFeedsUnread(feedIds: List<String>): Flow<List<ArticleListEntity>>

    @Query("SELECT $LIST_COLS FROM articles WHERE isStarred = 1 ORDER BY publishedMs DESC")
    fun observeStarred(): Flow<List<ArticleListEntity>>

    // Unread counts per feed for badges
    @Query("SELECT feedId, COUNT(*) as count FROM articles WHERE isRead = 0 GROUP BY feedId")
    fun observeUnreadCountsByFeed(): Flow<List<FeedUnreadCount>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(articles: List<ArticleEntity>): List<Long>

    @Query(
        """UPDATE articles SET title = :title, url = :url, author = :author,
           publishedMs = :publishedMs, feedTitle = :feedTitle, summary = :summary,
           thumbnailUrl = :thumbnailUrl, sourceFaviconUrl = :sourceFaviconUrl,
           syncedAt = :syncedAt WHERE id = :id"""
    )
    suspend fun updateSyncedMetadata(
        id: String,
        title: String,
        url: String,
        author: String?,
        publishedMs: Long,
        feedTitle: String,
        summary: String?,
        thumbnailUrl: String?,
        sourceFaviconUrl: String?,
        syncedAt: Long,
    )

    /**
     * Sync-time upsert that inserts new articles and refreshes only the
     * feed-provided columns of existing ones. Unlike @Upsert (full-row
     * REPLACE), this never rewrites the fetchedContent blob unnecessarily and
     * cannot clobber isRead/isStarred.
     */
    @Transaction
    suspend fun syncUpsert(articles: List<ArticleEntity>) {
        val rowIds = insertIgnore(articles)
        articles.forEachIndexed { i, a ->
            if (rowIds[i] == -1L) { // conflict → existing row: targeted update
                updateSyncedMetadata(
                    a.id, a.title, a.url, a.author, a.publishedMs, a.feedTitle,
                    a.summary, a.thumbnailUrl, a.sourceFaviconUrl, a.syncedAt,
                )
                if (a.fetchedContent != null) {
                    updateFetchedContent(a.id, a.fetchedContent, a.fetchedAt ?: a.syncedAt)
                }
            }
        }
    }

    /**
     * Retention: deletes this feed's articles that are no longer listed in the
     * source feed, beyond the :keep newest. Starred articles are always kept.
     * Only articles absent from the live feed are eligible, so nothing pruned
     * here can be re-ingested by the next sync.
     */
    @Query(
        """DELETE FROM articles WHERE feedId = :feedId AND isStarred = 0
           AND id NOT IN (:currentFeedIds)
           AND id NOT IN (
               SELECT id FROM articles WHERE feedId = :feedId
               ORDER BY publishedMs DESC LIMIT :keep
           )"""
    )
    suspend fun pruneFeed(feedId: String, currentFeedIds: List<String>, keep: Int)

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

    @Query("DELETE FROM articles WHERE feedId = :feedId")
    suspend fun deleteByFeedId(feedId: String)

    @Query("DELETE FROM articles")
    suspend fun deleteAll()

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
