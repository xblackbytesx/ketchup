package com.example.ketchup.ui.feed

import com.example.ketchup.data.model.Article

sealed class FeedUiState {
    object Loading : FeedUiState()
    data class Success(val articles: List<Article>, val isRefreshing: Boolean = false) : FeedUiState()
    data class Error(val message: String) : FeedUiState()
}
