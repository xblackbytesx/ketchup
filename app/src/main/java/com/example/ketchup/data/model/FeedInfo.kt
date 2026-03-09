package com.example.ketchup.data.model

data class FeedInfo(
    val id: String,
    val title: String,
    val feedUrl: String,
    val siteUrl: String,
    val faviconUrl: String?,
    val categoryLabel: String  // empty string = uncategorized
)
