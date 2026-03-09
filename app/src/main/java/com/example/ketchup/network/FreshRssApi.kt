package com.example.ketchup.network

import com.example.ketchup.data.model.Article
import com.example.ketchup.data.model.FeedInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class FreshRssApi {
    companion object {
        // Compiled once at class-load time; matches <img src="…"> and <img src='…'>
        // Uses *? (zero-or-more, lazy) so src can be the first or any attribute
        private val IMG_SRC_REGEX = Regex("""<img\b[^>]*?\bsrc=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun login(baseUrl: String, username: String, password: String): String = withContext(Dispatchers.IO) {
        val url = "${baseUrl.trimEnd('/')}/api/greader.php/accounts/ClientLogin"
        val body = FormBody.Builder()
            .add("Email", username)
            .add("Passwd", password)
            .build()
        val request = Request.Builder().url(url).post(body).build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) throw Exception("Login failed: ${response.code}")
        val text = response.body?.string() ?: throw Exception("Empty login response")
        text.lines()
            .firstOrNull { it.startsWith("Auth=") }
            ?.removePrefix("Auth=")
            ?: throw Exception("Auth token not found in response")
    }

    suspend fun getReadingList(
        baseUrl: String,
        token: String,
        includeRead: Boolean = true,
        continuation: String? = null
    ): Pair<List<Article>, String?> = withContext(Dispatchers.IO) {
        val urlBuilder = StringBuilder("${baseUrl.trimEnd('/')}/api/greader.php/reader/api/0/stream/contents/user/-/state/com.google/reading-list?output=json&n=50")
        if (!includeRead) urlBuilder.append("&xt=user/-/state/com.google/read")
        if (continuation != null) urlBuilder.append("&c=$continuation")

        val request = Request.Builder()
            .url(urlBuilder.toString())
            .header("Authorization", "GoogleLogin auth=$token")
            .get()
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) throw Exception("Feed fetch failed: ${response.code}")
        val text = response.body?.string() ?: throw Exception("Empty feed response")
        val json = JSONObject(text)

        val items = json.optJSONArray("items") ?: return@withContext Pair(emptyList(), null)
        val nextContinuation = json.optString("continuation").takeIf { it.isNotBlank() }

        val articles = mutableListOf<Article>()
        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            val id = item.optString("id")
            val title = item.optString("title", "Untitled")
            val published = item.optLong("published", 0L) * 1000L
            val author = item.optString("author").takeIf { it.isNotBlank() }

            val canonical = item.optJSONArray("canonical")
            val url = if (canonical != null && canonical.length() > 0) {
                canonical.getJSONObject(0).optString("href", "")
            } else ""

            // Prefer full article body from `content`, fall back to `summary` snippet
            val contentObj = item.optJSONObject("content")
            val summaryObj = item.optJSONObject("summary")
            val contentHtml = contentObj?.optString("content")?.takeIf { it.isNotBlank() }
            val summaryHtml = summaryObj?.optString("content")?.takeIf { it.isNotBlank() }
            val summary = contentHtml ?: summaryHtml

            val enclosure = item.optJSONArray("enclosure")
            var thumbnailUrl: String? = null
            if (enclosure != null && enclosure.length() > 0) {
                val enc = enclosure.getJSONObject(0)
                val type = enc.optString("type", "")
                if (type.startsWith("image/")) {
                    thumbnailUrl = enc.optString("href").takeIf { it.isNotBlank() }
                }
            }
            // Fall back to first <img> found in content or summary HTML
            // Scan both fields independently — one may have images the other lacks
            if (thumbnailUrl == null) {
                thumbnailUrl = contentHtml?.let { extractFirstImageUrl(it) }
                    ?: summaryHtml?.let { extractFirstImageUrl(it) }
            }

            val origin = item.optJSONObject("origin")
            val feedTitle = origin?.optString("title", "") ?: ""
            val feedId = origin?.optString("streamId", "") ?: ""

            val categories = item.optJSONArray("categories")
            var isRead = false
            var isStarred = false
            if (categories != null) {
                for (j in 0 until categories.length()) {
                    val cat = categories.getString(j)
                    if (cat.endsWith("/state/com.google/read")) isRead = true
                    if (cat.endsWith("/state/com.google/starred")) isStarred = true
                }
            }

            articles.add(
                Article(
                    id = id,
                    title = title,
                    url = url,
                    author = author,
                    publishedMs = published,
                    feedTitle = feedTitle,
                    feedId = feedId,
                    summary = summary,
                    thumbnailUrl = thumbnailUrl,
                    isRead = isRead,
                    isStarred = isStarred,
                    fetchedContent = null,
                    fetchedAt = null
                )
            )
        }
        Pair(articles, nextContinuation)
    }

    suspend fun getSubscriptions(baseUrl: String, token: String): List<FeedInfo> = withContext(Dispatchers.IO) {
        val url = "${baseUrl.trimEnd('/')}/api/greader.php/reader/api/0/subscription/list?output=json"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "GoogleLogin auth=$token")
            .get()
            .build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) throw Exception("Subscription list fetch failed: ${response.code}")
        val text = response.body?.string() ?: throw Exception("Empty subscription response")
        val json = JSONObject(text)
        val subscriptions = json.optJSONArray("subscriptions") ?: return@withContext emptyList()

        val feeds = mutableListOf<FeedInfo>()
        for (i in 0 until subscriptions.length()) {
            val sub = subscriptions.getJSONObject(i)
            val id = sub.optString("id")
            val title = sub.optString("title", "")
            val feedUrl = sub.optString("url", "")
            val siteUrl = sub.optString("htmlUrl", "")
            val faviconUrl = sub.optString("iconUrl").takeIf { it.isNotBlank() }

            val categoriesArr = sub.optJSONArray("categories")
            val categoryLabel = if (categoriesArr != null && categoriesArr.length() > 0) {
                categoriesArr.getJSONObject(0).optString("label", "")
            } else ""

            feeds.add(FeedInfo(id, title, feedUrl, siteUrl, faviconUrl, categoryLabel))
        }
        feeds
    }

    suspend fun getActionToken(baseUrl: String, token: String): String = withContext(Dispatchers.IO) {
        val url = "${baseUrl.trimEnd('/')}/api/greader.php/reader/api/0/token"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "GoogleLogin auth=$token")
            .get()
            .build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) throw Exception("Action token fetch failed: ${response.code}")
        response.body?.string()?.trim() ?: throw Exception("Empty action token response")
    }

    suspend fun markRead(baseUrl: String, token: String, actionToken: String, articleId: String) {
        editTag(baseUrl, token, actionToken, articleId, add = "user/-/state/com.google/read", remove = null)
    }

    suspend fun markUnread(baseUrl: String, token: String, actionToken: String, articleId: String) {
        editTag(baseUrl, token, actionToken, articleId, add = null, remove = "user/-/state/com.google/read")
    }

    suspend fun markStarred(baseUrl: String, token: String, actionToken: String, articleId: String) {
        editTag(baseUrl, token, actionToken, articleId, add = "user/-/state/com.google/starred", remove = null)
    }

    suspend fun markUnstarred(baseUrl: String, token: String, actionToken: String, articleId: String) {
        editTag(baseUrl, token, actionToken, articleId, add = null, remove = "user/-/state/com.google/starred")
    }

    private fun extractFirstImageUrl(html: String): String? {
        // Quick regex scan — runs on already-fetched strings, no extra I/O
        // Matches both src="…" and src='…' forms; skips data URIs and relative paths
        return IMG_SRC_REGEX.find(html)
            ?.groupValues?.getOrNull(1)
            ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
    }

    private suspend fun editTag(
        baseUrl: String, token: String, actionToken: String, articleId: String,
        add: String?, remove: String?
    ) = withContext(Dispatchers.IO) {
        val url = "${baseUrl.trimEnd('/')}/api/greader.php/reader/api/0/edit-tag"
        val bodyBuilder = FormBody.Builder()
            .add("i", articleId)
            .add("T", actionToken)
        if (add != null) bodyBuilder.add("a", add)
        if (remove != null) bodyBuilder.add("r", remove)

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "GoogleLogin auth=$token")
            .post(bodyBuilder.build())
            .build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) throw Exception("Edit tag failed: ${response.code}")
    }
}
