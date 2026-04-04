package com.example.ketchup.ui.feed

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ketchup.KetchupApplication
import com.example.ketchup.data.model.FeedInfo
import com.example.ketchup.data.model.NavFilter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class FeedViewModel(private val app: KetchupApplication) : ViewModel() {
    private val prefs = app.prefsManager
    private val repository = app.repository

    private val _showRead = MutableStateFlow(prefs.showReadArticles)
    val showRead: StateFlow<Boolean> = _showRead.asStateFlow()

    private val _navFilter = MutableStateFlow<NavFilter>(NavFilter.AllArticles)
    val navFilter: StateFlow<NavFilter> = _navFilter.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)

    private val _syncError = Channel<String>(Channel.BUFFERED)
    val syncError: Flow<String> = _syncError.receiveAsFlow()

    private val _showAddFeedDialog = MutableStateFlow(false)
    val showAddFeedDialog: StateFlow<Boolean> = _showAddFeedDialog.asStateFlow()

    val feeds: StateFlow<List<FeedInfo>> = repository.observeFeeds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val unreadCounts: StateFlow<Map<String, Int>> = repository.observeUnreadCountsByFeed()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun setNavFilter(filter: NavFilter) {
        _navFilter.value = filter
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<FeedUiState> = combine(_navFilter, _showRead, feeds) { filter, showRead, allFeeds ->
        val feedIds = if (filter is NavFilter.ByCategory) {
            allFeeds.filter { it.categoryLabel == filter.label }.map { it.id }
        } else emptyList()
        repository.observeArticlesByFilter(filter, showRead, feedIds)
    }.flatMapLatest { it }
        .map { articles -> FeedUiState.Success(articles, false) as FeedUiState }
        .combine(_isRefreshing) { state, refreshing ->
            if (state is FeedUiState.Success) state.copy(isRefreshing = refreshing) else state
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = FeedUiState.Loading
        )

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val failures = repository.syncArticles()
                if (failures > 0) {
                    _syncError.trySend("$failures feed${if (failures == 1) "" else "s"} failed to sync")
                }
            } catch (e: Exception) {
                Log.e("FeedViewModel", "Sync error", e)
                _syncError.trySend("Sync failed")
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun toggleShowRead() {
        val newVal = !_showRead.value
        _showRead.value = newVal
        prefs.showReadArticles = newVal
    }

    fun markRead(articleId: String) {
        viewModelScope.launch { repository.markRead(articleId) }
    }

    fun markUnread(articleId: String) {
        viewModelScope.launch { repository.markUnread(articleId) }
    }

    fun deleteFeed(feedId: String) {
        viewModelScope.launch {
            repository.deleteFeed(feedId)
            if (_navFilter.value.let { it is NavFilter.ByFeed && it.feedId == feedId }) {
                _navFilter.value = NavFilter.AllArticles
            }
        }
    }

    fun updateFeed(feedId: String, title: String, categoryLabel: String) {
        viewModelScope.launch { repository.updateFeed(feedId, title, categoryLabel) }
    }

    fun showAddFeedDialog() {
        _showAddFeedDialog.value = true
    }

    fun dismissAddFeedDialog() {
        _showAddFeedDialog.value = false
    }

    fun addFeed(url: String) {
        _showAddFeedDialog.value = false
        viewModelScope.launch {
            try {
                repository.addFeed(url)
            } catch (e: Exception) {
                Log.e("FeedViewModel", "Add feed error", e)
                _syncError.trySend("Failed to add feed: ${e.message}")
            }
        }
    }
}

class FeedViewModelFactory(private val app: KetchupApplication) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = FeedViewModel(app) as T
}
