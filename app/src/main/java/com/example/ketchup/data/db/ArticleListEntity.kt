package com.example.ketchup.data.db

import com.example.ketchup.data.model.Article

/**
 * List-screen projection of [ArticleEntity] without the fetchedContent blob.
 *
 * List Flows re-run their query on every articles-table change, so selecting
 * `*` would materialize every cached article page (potentially hundreds of KB
 * each) on each emission — the app's single largest memory cost. The reader
 * loads the full row by id when it actually needs the content.
 */
data class ArticleListEntity(
    val id: String,
    val title: String,
    val url: String,
    val author: String?,
    val publishedMs: Long,
    val feedTitle: String,
    val feedId: String,
    val summary: String?,
    val thumbnailUrl: String?,
    val isRead: Boolean,
    val isStarred: Boolean,
    val fetchedAt: Long?,
    val sourceFaviconUrl: String?
) {
    fun toDomain(): Article = Article(
        id = id,
        title = title,
        url = url,
        author = author,
        publishedMs = publishedMs,
        feedTitle = feedTitle,
        feedId = feedId,
        summary = summary,
        thumbnailUrl = thumbnailUrl,
        isRead = isRead,
        isStarred = isStarred,
        fetchedContent = null,
        fetchedAt = fetchedAt,
        sourceFaviconUrl = sourceFaviconUrl
    )
}
