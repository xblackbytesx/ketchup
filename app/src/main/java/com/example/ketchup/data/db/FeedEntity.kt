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
    val categoryLabel: String
) {
    fun toDomain() = FeedInfo(id, title, feedUrl, siteUrl, faviconUrl, categoryLabel)
}
