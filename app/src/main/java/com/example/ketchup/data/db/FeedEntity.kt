package com.example.ketchup.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.ketchup.data.model.FeedInfo

@Entity(tableName = "feeds")
data class FeedEntity(
    @PrimaryKey val id: String,
    val title: String,
    val feedUrl: String,
    val siteUrl: String,
    val faviconUrl: String?,
    val categoryLabel: String,
    // true once the user renamed the feed; sync then stops auto-updating the title
    val isTitleCustomized: Boolean = false,
    // HTTP cache validators from the last fetch, sent as If-None-Match /
    // If-Modified-Since so unchanged feeds return 304 instead of a full body
    val etag: String? = null,
    val lastModified: String? = null
) {
    fun toDomain() = FeedInfo(id, title, feedUrl, siteUrl, faviconUrl, categoryLabel, isTitleCustomized)
}
