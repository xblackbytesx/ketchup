package com.example.ketchup.ui.reader

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ketchup.KetchupApplication
import com.example.ketchup.data.model.Article
import com.example.ketchup.data.model.NavFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ReaderUiState(
    val article: Article? = null,
    val isLoading: Boolean = true,
    val isFetchingContent: Boolean = false,
    val showingFetchedContent: Boolean = false,
    val articleList: List<String> = emptyList(),
    val position: Int = 0,
)

class ArticleReaderViewModel(
    private val app: KetchupApplication,
    private val initialArticleId: String,
    private val filterId: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState

    private val repository = app.repository
    private val prefs = app.prefsManager

    init {
        loadArticleAndList()
    }

    private fun loadArticleAndList() {
        viewModelScope.launch {
            // Step 1: single-row lookup — fast, show article immediately
            val article = withContext(Dispatchers.IO) {
                repository.getArticleById(initialArticleId)
            } ?: run {
                _uiState.value = _uiState.value.copy(isLoading = false)
                return@launch
            }

            if (prefs.autoMarkRead) {
                withContext(Dispatchers.IO) { repository.markRead(initialArticleId) }
            }

            _uiState.value = _uiState.value.copy(
                article = article,
                isLoading = false,
                showingFetchedContent = article.fetchedContent != null &&
                        repository.isFetchCacheValid(article.fetchedAt),
            )

            // Step 2: load article list in background for prev/next navigation
            val articleList = withContext(Dispatchers.IO) {
                try {
                    val filter = filterId.toNavFilter()
                    val allFeeds = repository.observeFeeds().first()
                    val feedIds = if (filter is NavFilter.ByCategory) {
                        allFeeds.filter { it.categoryLabel == filter.label }.map { it.id }
                    } else emptyList()
                    repository.getArticleIdsByFilter(filter, prefs.showReadArticles, feedIds)
                } catch (e: Exception) {
                    Log.e("ArticleReaderViewModel", "Failed to load article list", e)
                    listOf(initialArticleId)
                }
            }

            val position = articleList.indexOf(initialArticleId).coerceAtLeast(0)
            _uiState.value = _uiState.value.copy(
                articleList = articleList,
                position = position,
            )
        }
    }

    fun navigateNext() {
        val state = _uiState.value
        val nextPos = state.position + 1
        val nextId = state.articleList.getOrNull(nextPos) ?: return

        viewModelScope.launch {
            val nextArticle = withContext(Dispatchers.IO) { repository.getArticleById(nextId) } ?: return@launch
            if (prefs.autoMarkRead) {
                withContext(Dispatchers.IO) { repository.markRead(nextId) }
            }
            _uiState.value = _uiState.value.copy(
                article = nextArticle,
                position = nextPos,
                showingFetchedContent = nextArticle.fetchedContent != null &&
                        repository.isFetchCacheValid(nextArticle.fetchedAt),
            )
        }
    }

    fun navigatePrev() {
        val state = _uiState.value
        val prevPos = state.position - 1
        val prevId = state.articleList.getOrNull(prevPos) ?: return

        viewModelScope.launch {
            val prevArticle = withContext(Dispatchers.IO) { repository.getArticleById(prevId) } ?: return@launch
            if (prefs.autoMarkRead) {
                withContext(Dispatchers.IO) { repository.markRead(prevId) }
            }
            _uiState.value = _uiState.value.copy(
                article = prevArticle,
                position = prevPos,
                showingFetchedContent = prevArticle.fetchedContent != null &&
                        repository.isFetchCacheValid(prevArticle.fetchedAt),
            )
        }
    }

    fun toggleStar() {
        val article = _uiState.value.article ?: return
        val newStarred = !article.isStarred
        _uiState.value = _uiState.value.copy(article = article.copy(isStarred = newStarred))
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.toggleStar(article.id, newStarred) }
        }
    }

    fun fetchFullContent() {
        val article = _uiState.value.article ?: return
        _uiState.value = _uiState.value.copy(isFetchingContent = true)
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) { repository.fetchAndCacheContent(article.id) }
            if (success) {
                val updated = withContext(Dispatchers.IO) { repository.getArticleById(article.id) }
                _uiState.value = _uiState.value.copy(
                    article = updated ?: article,
                    isFetchingContent = false,
                    showingFetchedContent = updated?.fetchedContent != null,
                )
            } else {
                _uiState.value = _uiState.value.copy(isFetchingContent = false)
            }
        }
    }

    fun toggleFetchedContent() {
        val state = _uiState.value
        val article = state.article ?: return
        if (state.showingFetchedContent) {
            _uiState.value = state.copy(showingFetchedContent = false)
        } else if (article.fetchedContent != null && repository.isFetchCacheValid(article.fetchedAt)) {
            _uiState.value = state.copy(showingFetchedContent = true)
        } else {
            fetchFullContent()
        }
    }

    fun isFetchCacheValid(fetchedAt: Long?): Boolean = repository.isFetchCacheValid(fetchedAt)

    private fun String.toNavFilter(): NavFilter = when {
        this == "all" -> NavFilter.AllArticles
        this == "today" -> NavFilter.Today
        this == "starred" -> NavFilter.Starred
        this.startsWith("category:") -> NavFilter.ByCategory(this.removePrefix("category:"))
        this.startsWith("feed:") -> NavFilter.ByFeed(this.removePrefix("feed:"), "", null)
        else -> NavFilter.AllArticles
    }
}

class ArticleReaderViewModelFactory(
    private val app: KetchupApplication,
    private val articleId: String,
    private val filterId: String,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ArticleReaderViewModel(app, articleId, filterId) as T
}
