package com.example.ketchup.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.ketchup.data.model.Article

@Entity(tableName = "articles")
data class ArticleEntity(
    @PrimaryKey val id: String,
    val title: String,
    val url: String,
    val author: String?,
    val publishedMs: Long,
    val feedTitle: String,
    val feedId: String,
    val summary: String?,
    val thumbnailUrl: String?,
    val isRead: Boolean,
    val isStarred: Boolean = false,
    val fetchedContent: String?,
    val fetchedAt: Long?,
    val syncedAt: Long
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
        fetchedContent = fetchedContent,
        fetchedAt = fetchedAt
    )
}
