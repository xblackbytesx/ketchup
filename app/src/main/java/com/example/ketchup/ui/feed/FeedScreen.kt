package com.example.ketchup.ui.feed

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ketchup.KetchupApplication
import com.example.ketchup.data.model.FeedInfo
import com.example.ketchup.data.model.NavFilter
import com.example.ketchup.data.model.Article
import com.example.ketchup.ui.components.EmptyState
import com.example.ketchup.ui.components.FeedNavDrawer
import com.example.ketchup.ui.components.HeroArticleCard
import com.example.ketchup.ui.components.SecondaryArticleCard
import com.example.ketchup.ui.components.StandardArticleCard
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    app: KetchupApplication,
    onOpenArticle: (articleId: String, filterId: String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val viewModel: FeedViewModel = viewModel(
        factory = FeedViewModelFactory(app)
    )
    val uiState by viewModel.uiState.collectAsState()
    val feeds by viewModel.feeds.collectAsState()
    val unreadCounts by viewModel.unreadCounts.collectAsState()
    val navFilter by viewModel.navFilter.collectAsState()
    val showRead by viewModel.showRead.collectAsState()

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var editFeed by remember { mutableStateOf<FeedInfo?>(null) }
    var deleteFeed by remember { mutableStateOf<FeedInfo?>(null) }

    // Show sync errors as snackbar
    LaunchedEffect(Unit) {
        viewModel.syncError.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    // FAB visibility — show after 3+ items scrolled
    val showFab by remember {
        derivedStateOf { listState.firstVisibleItemIndex >= 3 }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface,
            ) {
            FeedNavDrawer(
                feeds = feeds,
                unreadCounts = unreadCounts,
                currentFilter = navFilter,
                onFilterSelected = { filter ->
                    viewModel.setNavFilter(filter)
                    scope.launch { drawerState.close() }
                },
                onAddFeed = {
                    scope.launch { drawerState.close() }
                    // Navigate to setup — handled by showing a dialog inline for add-more
                    viewModel.showAddFeedDialog()
                },
                onEditFeed = { feed ->
                    editFeed = feed
                    scope.launch { drawerState.close() }
                },
                onDeleteFeed = { feed ->
                    deleteFeed = feed
                    scope.launch { drawerState.close() }
                },
            )
            } // ModalDrawerSheet
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(navFilter.displayName()) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Open drawer")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.toggleShowRead() }) {
                            Icon(
                                Icons.Default.FilterList,
                                contentDescription = if (showRead) "Hide read" else "Show read",
                                tint = if (!showRead) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                )
            },
            floatingActionButton = {
                AnimatedVisibility(visible = showFab, enter = fadeIn(), exit = fadeOut()) {
                    FloatingActionButton(
                        onClick = { scope.launch { listState.animateScrollToItem(0) } },
                        containerColor = MaterialTheme.colorScheme.primary,
                    ) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = "Scroll to top")
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { innerPadding ->
            when (val state = uiState) {
                is FeedUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize().padding(innerPadding))
                }

                is FeedUiState.Error -> {
                    EmptyState(
                        icon = Icons.Default.RssFeed,
                        message = state.message,
                        modifier = Modifier.padding(innerPadding),
                    )
                }

                is FeedUiState.Success -> {
                    val articles = state.articles
                    val featuredLayout = app.prefsManager.featuredLayout

                    if (articles.isEmpty()) {
                        EmptyState(
                            icon = Icons.Default.RssFeed,
                            message = "No articles yet.\nPull down to refresh.",
                            modifier = Modifier.padding(innerPadding),
                        )
                    } else {
                        val filterId = navFilter.toFilterId()

                        val feedRows = remember(articles, featuredLayout) {
                            buildFeedRows(articles, featuredLayout)
                        }

                        PullToRefreshBox(
                            isRefreshing = state.isRefreshing,
                            onRefresh = { viewModel.refresh() },
                            modifier = Modifier.padding(innerPadding),
                        ) {
                            LazyColumn(
                                state = listState,
                                contentPadding = PaddingValues(bottom = 88.dp),
                            ) {
                                items(feedRows, key = { it.key }) { row ->
                                    fun open(articleId: String) {
                                        viewModel.markRead(articleId)
                                        onOpenArticle(articleId, filterId)
                                    }
                                    when (row) {
                                        is FeedRow.Hero -> HeroArticleCard(
                                            article = row.article,
                                            onClick = { open(row.article.id) },
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        )
                                        is FeedRow.Pair -> Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            SecondaryArticleCard(
                                                article = row.first,
                                                onClick = { open(row.first.id) },
                                                modifier = Modifier.weight(1f),
                                            )
                                            SecondaryArticleCard(
                                                article = row.second,
                                                onClick = { open(row.second.id) },
                                                modifier = Modifier.weight(1f),
                                            )
                                        }
                                        is FeedRow.Standard -> StandardArticleCard(
                                            article = row.article,
                                            onClick = { open(row.article.id) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Edit feed dialog
    editFeed?.let { feed ->
        EditFeedDialog(
            feed = feed,
            onDismiss = { editFeed = null },
            onConfirm = { title, category ->
                viewModel.updateFeed(feed.id, title, category)
                editFeed = null
            },
        )
    }

    // Delete feed confirm dialog
    deleteFeed?.let { feed ->
        AlertDialog(
            onDismissRequest = { deleteFeed = null },
            title = { Text("Remove feed") },
            text = { Text("Remove \"${feed.title}\"? All cached articles will be deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteFeed(feed.id)
                    deleteFeed = null
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteFeed = null }) { Text("Cancel") }
            },
        )
    }

    // Add feed dialog (shown when triggered from drawer)
    val showAddDialog by viewModel.showAddFeedDialog.collectAsState()
    if (showAddDialog) {
        AddFeedDialog(
            onDismiss = { viewModel.dismissAddFeedDialog() },
            onAdd = { url ->
                viewModel.addFeed(url)
            },
        )
    }
}

@Composable
private fun EditFeedDialog(
    feed: FeedInfo,
    onDismiss: () -> Unit,
    onConfirm: (title: String, category: String) -> Unit,
) {
    var title by remember { mutableStateOf(feed.title) }
    var category by remember { mutableStateOf(feed.categoryLabel) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit feed") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category (optional)") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(title.trim(), category.trim()) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun AddFeedDialog(
    onDismiss: () -> Unit,
    onAdd: (url: String) -> Unit,
) {
    var url by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add feed") },
        text = {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Feed URL") },
                placeholder = { Text("https://example.com/feed.rss") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { if (url.isNotBlank()) onAdd(url.trim()); onDismiss() }) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private sealed class FeedRow {
    abstract val key: String
    data class Hero(val article: Article) : FeedRow() { override val key = article.id }
    data class Pair(val first: Article, val second: Article) : FeedRow() { override val key = first.id + "_pair" }
    data class Standard(val article: Article) : FeedRow() { override val key = article.id }
}

private fun buildFeedRows(articles: List<Article>, featuredLayout: Boolean): List<FeedRow> {
    if (!featuredLayout) return articles.map { FeedRow.Standard(it) }
    return buildList {
        var i = 0
        while (i < articles.size) {
            when (i % 7) {
                0 -> {
                    if (articles[i].thumbnailUrl != null) {
                        add(FeedRow.Hero(articles[i]))
                    } else {
                        add(FeedRow.Standard(articles[i]))
                    }
                    i++
                }
                1 -> {
                    if (i + 1 < articles.size) {
                        add(FeedRow.Pair(articles[i], articles[i + 1]))
                        i += 2
                    } else {
                        add(FeedRow.Standard(articles[i]))
                        i++
                    }
                }
                else -> {
                    add(FeedRow.Standard(articles[i]))
                    i++
                }
            }
        }
    }
}

private fun NavFilter.toFilterId(): String = when (this) {
    is NavFilter.AllArticles -> "all"
    is NavFilter.Today -> "today"
    is NavFilter.Starred -> "starred"
    is NavFilter.ByCategory -> "category:$label"
    is NavFilter.ByFeed -> "feed:$feedId"
}
