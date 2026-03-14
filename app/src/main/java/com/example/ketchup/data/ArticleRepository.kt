package com.example.ketchup.data

import android.util.Log
import com.example.ketchup.data.db.AppDatabase
import com.example.ketchup.data.db.ArticleEntity
import com.example.ketchup.data.db.FeedEntity
import com.example.ketchup.data.model.Article
import com.example.ketchup.data.model.FeedInfo
import com.example.ketchup.data.model.NavFilter
import com.example.ketchup.network.ArticleFetcher
import com.example.ketchup.network.AtomFeedParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Calendar
import java.util.concurrent.TimeUnit

class ArticleRepository(
    private val db: AppDatabase,
    private val fetcher: ArticleFetcher,
    private val prefs: PreferencesManager
) {
    private val dao = db.articleDao()
    private val feedDao = db.feedDao()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    fun observeFeeds(): Flow<List<FeedInfo>> {
        return feedDao.observeAll().map { entities -> entities.map { it.toDomain() } }
    }

    fun observeUnreadCountsByFeed(): Flow<Map<String, Int>> {
        return dao.observeUnreadCountsByFeed().map { list ->
            list.associate { it.feedId to it.count }
        }
    }

    fun observeArticlesByFilter(
        navFilter: NavFilter,
        showRead: Boolean,
        feedIds: List<String> = emptyList()
    ): Flow<List<Article>> {
        val startOfDay = getStartOfDayMs()
        val entityFlow: Flow<List<ArticleEntity>> = when {
            navFilter is NavFilter.Today && showRead -> dao.observeToday(startOfDay)
            navFilter is NavFilter.Today && !showRead -> dao.observeTodayUnread(startOfDay)
            navFilter is NavFilter.ByFeed && showRead -> dao.observeByFeed(navFilter.feedId)
            navFilter is NavFilter.ByFeed && !showRead -> dao.observeByFeedUnread(navFilter.feedId)
            navFilter is NavFilter.ByCategory && feedIds.isNotEmpty() && showRead -> dao.observeByFeeds(feedIds)
            navFilter is NavFilter.ByCategory && feedIds.isNotEmpty() && !showRead -> dao.observeByFeedsUnread(feedIds)
            navFilter is NavFilter.ByCategory && feedIds.isEmpty() -> flowOf(emptyList())
            navFilter is NavFilter.AllArticles && showRead && prefs.sortOrder == "oldest_first" -> dao.observeAllOldestFirst()
            navFilter is NavFilter.AllArticles && showRead -> dao.observeAll()
            navFilter is NavFilter.AllArticles && prefs.sortOrder == "oldest_first" -> dao.observeUnreadOldestFirst()
            else -> dao.observeUnread()
        }
        return entityFlow.map { entities -> entities.map { it.toDomain() } }
    }

    suspend fun syncArticles() = withContext(Dispatchers.IO) {
        val feeds = feedDao.getAllOnce()
        feeds.forEach { feed ->
            try {
                syncFeed(feed)
            } catch (e: Exception) {
                Log.w("ArticleRepository", "syncFeed failed for ${feed.feedUrl}", e)
            }
        }
    }

    private suspend fun syncFeed(feed: FeedEntity) {
        val body = fetchFeedBody(feed.feedUrl) ?: return
        val parsed = AtomFeedParser().parse(body, feed.feedUrl)

        if (parsed.title != feed.title && parsed.title.isNotBlank()) {
            feedDao.upsertAll(listOf(feed.copy(title = parsed.title)))
        }

        val now = System.currentTimeMillis()
        val entities = parsed.articles.map { article ->
            val existing = dao.getById(article.id)
            ArticleEntity(
                id = article.id,
                title = article.title,
                url = article.url,
                author = article.author,
                publishedMs = article.publishedMs,
                feedTitle = article.sourceTitle ?: parsed.title,
                feedId = feed.id,
                summary = article.summary,
                thumbnailUrl = article.thumbnailUrl,
                isRead = existing?.isRead ?: false,
                isStarred = existing?.isStarred ?: false,
                fetchedContent = article.content ?: existing?.fetchedContent,
                fetchedAt = if (article.content != null) now else existing?.fetchedAt,
                syncedAt = now,
                sourceFaviconUrl = article.sourceFaviconUrl
            )
        }
        dao.upsertAll(entities)
    }

    suspend fun addFeed(feedUrl: String): FeedInfo = withContext(Dispatchers.IO) {
        val body = fetchFeedBody(feedUrl) ?: throw Exception("Could not fetch feed — check the URL and your connection")
        val parsed = AtomFeedParser().parse(body, feedUrl)
        val entity = FeedEntity(
            id = feedUrl,
            title = parsed.title,
            feedUrl = feedUrl,
            siteUrl = "",
            faviconUrl = null,
            categoryLabel = ""
        )
        feedDao.upsertAll(listOf(entity))
        syncFeed(entity)
        entity.toDomain()
    }

    private fun fetchFeedBody(url: String): String? {
        return try {
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) null else response.body?.string()
            }
        } catch (e: Exception) {
            Log.w("ArticleRepository", "fetchFeedBody failed for $url", e)
            null
        }
    }

    private fun getStartOfDayMs(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    suspend fun fetchAndCacheContent(articleId: String): Boolean = withContext(Dispatchers.IO) {
        val entity = dao.getById(articleId) ?: return@withContext false
        val ttlMs = prefs.cacheTtlHours * 3600_000L

        if (entity.fetchedContent != null && entity.fetchedAt != null &&
            System.currentTimeMillis() - entity.fetchedAt < ttlMs
        ) {
            return@withContext true
        }

        val content = fetcher.fetchFullContent(entity.url) ?: return@withContext false
        dao.updateFetchedContent(articleId, content, System.currentTimeMillis())
        true
    }

    suspend fun markRead(articleId: String) = withContext(Dispatchers.IO) {
        dao.updateReadState(articleId, true)
    }

    suspend fun markUnread(articleId: String) = withContext(Dispatchers.IO) {
        dao.updateReadState(articleId, false)
    }

    suspend fun toggleStar(articleId: String, starred: Boolean) = withContext(Dispatchers.IO) {
        dao.updateStarred(articleId, starred)
    }

    suspend fun clearFetchedContent() = withContext(Dispatchers.IO) {
        dao.clearFetchedContent()
    }

    suspend fun getArticleById(id: String): Article? = withContext(Dispatchers.IO) {
        dao.getById(id)?.toDomain()
    }

    fun isFetchCacheValid(fetchedAt: Long?, ttlMs: Long): Boolean {
        if (fetchedAt == null) return false
        return System.currentTimeMillis() - fetchedAt < ttlMs
    }
}
