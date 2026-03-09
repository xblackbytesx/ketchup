package com.example.ketchup.data

import com.example.ketchup.data.db.AppDatabase
import com.example.ketchup.data.db.ArticleEntity
import com.example.ketchup.data.db.FeedEntity
import com.example.ketchup.data.model.Article
import com.example.ketchup.data.model.FeedInfo
import com.example.ketchup.data.model.NavFilter
import com.example.ketchup.network.ArticleFetcher
import com.example.ketchup.network.FreshRssApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.Calendar

class ArticleRepository(
    private val db: AppDatabase,
    private val api: FreshRssApi,
    private val fetcher: ArticleFetcher,
    private val secureStorage: SecureStorage,
    private val prefs: PreferencesManager
) {
    private val dao = db.articleDao()
    private val feedDao = db.feedDao()

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

    // Keep backward-compat method used by nothing new, but keep it for safety
    fun observeArticles(showRead: Boolean): Flow<List<Article>> {
        return observeArticlesByFilter(NavFilter.AllArticles, showRead)
    }

    suspend fun syncArticles() = withContext(Dispatchers.IO) {
        val baseUrl = secureStorage.serverUrl
        val token = try {
            api.login(baseUrl, secureStorage.username, secureStorage.apiPassword)
                .also { secureStorage.authToken = it }
        } catch (e: Exception) {
            val cached = secureStorage.authToken
            if (cached.isBlank()) throw e
            cached
        }

        // Sync subscriptions first
        try {
            syncSubscriptions(baseUrl, token)
        } catch (e: Exception) {
            android.util.Log.w("ArticleRepository", "syncSubscriptions failed (non-fatal)", e)
        }

        // Always fetch all articles (including read) so the server is the source of truth
        // for read state. The UI show/hide preference is applied at the DB query layer.
        var continuation: String? = null
        val allArticles = mutableListOf<Article>()

        do {
            val (articles, nextCont) = api.getReadingList(
                baseUrl, token, includeRead = true, continuation = continuation
            )
            allArticles.addAll(articles)
            continuation = nextCont
        } while (continuation != null && allArticles.size < 200)

        val now = System.currentTimeMillis()
        val entities = allArticles.map { article ->
            val existing = dao.getById(article.id)
            ArticleEntity(
                id = article.id,
                title = article.title,
                url = article.url,
                author = article.author,
                publishedMs = article.publishedMs,
                feedTitle = article.feedTitle,
                feedId = article.feedId,
                summary = article.summary,
                thumbnailUrl = article.thumbnailUrl,
                isRead = article.isRead,
                fetchedContent = existing?.fetchedContent,
                fetchedAt = existing?.fetchedAt,
                syncedAt = now
            )
        }
        dao.upsertAll(entities)
    }

    suspend fun syncSubscriptions() = withContext(Dispatchers.IO) {
        val baseUrl = secureStorage.serverUrl
        val token = freshToken()
        syncSubscriptions(baseUrl, token)
    }

    private suspend fun syncSubscriptions(baseUrl: String, token: String) {
        val feeds = api.getSubscriptions(baseUrl, token)
        val entities = feeds.map { feed ->
            FeedEntity(
                id = feed.id,
                title = feed.title,
                feedUrl = feed.feedUrl,
                siteUrl = feed.siteUrl,
                faviconUrl = feed.faviconUrl,
                categoryLabel = feed.categoryLabel
            )
        }
        feedDao.deleteAll()
        feedDao.upsertAll(entities)
    }

    fun getStartOfDayMs(): Long {
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
        try {
            val token = freshToken()
            val actionToken = api.getActionToken(secureStorage.serverUrl, token)
            api.markRead(secureStorage.serverUrl, token, actionToken, articleId)
        } catch (e: Exception) {
            android.util.Log.e("ArticleRepository", "markRead failed, reverting local state", e)
            dao.updateReadState(articleId, false)
        }
    }

    suspend fun markUnread(articleId: String) = withContext(Dispatchers.IO) {
        dao.updateReadState(articleId, false)
        try {
            val token = freshToken()
            val actionToken = api.getActionToken(secureStorage.serverUrl, token)
            api.markUnread(secureStorage.serverUrl, token, actionToken, articleId)
        } catch (e: Exception) {
            android.util.Log.e("ArticleRepository", "markUnread failed, reverting local state", e)
            dao.updateReadState(articleId, true)
        }
    }

    private suspend fun freshToken(): String {
        return secureStorage.authToken.ifBlank {
            api.login(secureStorage.serverUrl, secureStorage.username, secureStorage.apiPassword)
                .also { secureStorage.authToken = it }
        }
    }

    suspend fun clearFetchedContent() = withContext(Dispatchers.IO) {
        dao.clearFetchedContent()
    }

    fun isFetchCacheValid(fetchedAt: Long?, ttlMs: Long): Boolean {
        if (fetchedAt == null) return false
        return System.currentTimeMillis() - fetchedAt < ttlMs
    }
}
