package com.example.ketchup.navigation

import kotlinx.serialization.Serializable

@Serializable
data object SetupRoute

@Serializable
data object SetupPinRoute

@Serializable
data object LockRoute

@Serializable
data object FeedRoute

@Serializable
data class ArticleReaderRoute(
    val articleId: String,
    val filterId: String, // serialized NavFilter key for reconstructing navigation list
)

@Serializable
data object SettingsRoute
