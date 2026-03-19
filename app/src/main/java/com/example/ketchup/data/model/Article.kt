package com.example.ketchup.data.model

data class Article(
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
    val isStarred: Boolean = false,
    val fetchedContent: String?,
    val fetchedAt: Long?,
    val sourceFaviconUrl: String? = null
)
