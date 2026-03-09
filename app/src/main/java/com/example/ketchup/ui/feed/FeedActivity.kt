package com.example.ketchup.ui.feed

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.ketchup.KetchupApplication
import com.example.ketchup.R
import com.example.ketchup.data.PreferencesManager
import com.example.ketchup.data.SecureStorage
import com.example.ketchup.data.model.FeedInfo
import com.example.ketchup.data.model.NavFilter
import com.example.ketchup.databinding.ActivityFeedBinding
import com.example.ketchup.ui.BaseActivity
import com.example.ketchup.ui.lock.LockActivity
import com.example.ketchup.ui.reader.ArticleReaderActivity
import com.example.ketchup.ui.settings.SettingsActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class FeedActivity : BaseActivity() {
    private lateinit var binding: ActivityFeedBinding
    private val viewModel: FeedViewModel by viewModels()
    private lateinit var adapter: ArticleAdapter
    private lateinit var navDrawerAdapter: NavDrawerAdapter
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var drawerToggle: ActionBarDrawerToggle
    private lateinit var prefs: PreferencesManager
    private lateinit var storage: SecureStorage

    private val expandedCategories = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFeedBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        prefs = PreferencesManager(this)
        storage = SecureStorage(this)

        // Set up drawer
        drawerLayout = binding.drawerLayout
        drawerToggle = ActionBarDrawerToggle(
            this, drawerLayout, binding.toolbar,
            R.string.nav_drawer_open, R.string.nav_drawer_close
        )
        drawerLayout.addDrawerListener(drawerToggle)
        drawerToggle.syncState()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Set up article adapter (no onMarkRead — mark-read happens on click)
        adapter = ArticleAdapter(
            useFeaturedLayout = prefs.featuredLayout,
            onArticleClick = { article ->
                if (!article.isRead) viewModel.markRead(article.id)
                val currentList = adapter.currentList
                val position = currentList.indexOf(article).coerceAtLeast(0)
                val intent = Intent(this, ArticleReaderActivity::class.java)
                    .putExtra(ArticleReaderActivity.EXTRA_ARTICLE, article)
                    .putStringArrayListExtra(ArticleReaderActivity.EXTRA_ARTICLE_IDS, ArrayList(currentList.map { it.id }))
                    .putExtra(ArticleReaderActivity.EXTRA_POSITION, position)
                startActivity(intent)
            },
            onMarkUnread = { article -> viewModel.markUnread(article.id) }
        )

        if (prefs.featuredLayout) {
            val gridManager = GridLayoutManager(this, 2)
            gridManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int =
                    if (adapter.getItemViewType(position) == ArticleAdapter.VIEW_TYPE_SECONDARY) 1 else 2
            }
            binding.recyclerView.layoutManager = gridManager
        } else {
            binding.recyclerView.layoutManager = LinearLayoutManager(this)
        }
        binding.recyclerView.adapter = adapter

        // Set up nav drawer adapter
        navDrawerAdapter = NavDrawerAdapter(
            onFilterSelected = { filter ->
                viewModel.setNavFilter(filter)
                drawerLayout.closeDrawers()
            },
            onCategoryToggle = { label ->
                if (expandedCategories.contains(label)) {
                    expandedCategories.remove(label)
                } else {
                    expandedCategories.add(label)
                }
                rebuildNavItems()
            }
        )

        binding.navDrawer.rvNav.layoutManager = LinearLayoutManager(this)
        binding.navDrawer.rvNav.adapter = navDrawerAdapter

        binding.swipeRefresh.setOnRefreshListener { viewModel.refresh() }

        // Observe UI state
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                when (state) {
                    is FeedUiState.Loading -> {
                        binding.progressBar.visibility = View.VISIBLE
                        binding.errorText.visibility = View.GONE
                        binding.tvEmptyState.visibility = View.GONE
                    }
                    is FeedUiState.Success -> {
                        binding.progressBar.visibility = View.GONE
                        binding.errorText.visibility = View.GONE
                        binding.swipeRefresh.isRefreshing = state.isRefreshing
                        adapter.submitList(state.articles)
                        binding.tvEmptyState.visibility = if (state.articles.isEmpty()) View.VISIBLE else View.GONE
                    }
                    is FeedUiState.Error -> {
                        binding.progressBar.visibility = View.GONE
                        binding.errorText.visibility = View.VISIBLE
                        binding.errorText.text = state.message
                        binding.tvEmptyState.visibility = View.GONE
                        binding.swipeRefresh.isRefreshing = false
                    }
                }
            }
        }

        // Update toolbar title from nav filter
        lifecycleScope.launch {
            viewModel.navFilter.collect { filter ->
                supportActionBar?.title = filter.displayName()
                invalidateOptionsMenu()
            }
        }

        // Update article favicon map when feeds change
        lifecycleScope.launch {
            viewModel.feeds.collect { feeds ->
                adapter.feedFaviconMap = feeds.associate { it.id to it.faviconUrl }
                adapter.notifyDataSetChanged()
            }
        }

        // Observe feeds + unread counts + navFilter to build drawer items
        lifecycleScope.launch {
            combine(
                viewModel.feeds,
                viewModel.unreadCounts,
                viewModel.navFilter
            ) { feeds, counts, filter ->
                Triple(feeds, counts, filter)
            }.collect { (feeds, counts, filter) ->
                val items = buildNavItems(feeds, counts, filter, expandedCategories)
                navDrawerAdapter.submitList(items)
            }
        }

        lifecycleScope.launch {
            viewModel.showRead.collect { invalidateOptionsMenu() }
        }
    }

    private fun rebuildNavItems() {
        val feeds = viewModel.feeds.value
        val counts = viewModel.unreadCounts.value
        val filter = viewModel.navFilter.value
        val items = buildNavItems(feeds, counts, filter, expandedCategories)
        navDrawerAdapter.submitList(items)
    }

    override fun onResume() {
        super.onResume()
        val app = application as KetchupApplication
        if (storage.isPinConfigured() && !app.isAuthenticated) {
            startActivity(Intent(this, LockActivity::class.java))
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.feed_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_toggle_read)?.title =
            if (viewModel.showRead.value) "Hide Read Articles" else "Show Read Articles"
        menu.findItem(R.id.action_toggle_featured)?.title =
            if (prefs.featuredLayout) "Disable Featured Layout" else "Enable Featured Layout"
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (drawerToggle.onOptionsItemSelected(item)) return true
        return when (item.itemId) {
            R.id.action_toggle_read -> {
                viewModel.toggleShowRead()
                true
            }
            R.id.action_toggle_featured -> {
                val newValue = !prefs.featuredLayout
                prefs.featuredLayout = newValue
                adapter.useFeaturedLayout = newValue
                val layoutManager = if (newValue) {
                    val gridManager = GridLayoutManager(this, 2)
                    gridManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                        override fun getSpanSize(position: Int): Int =
                            if (adapter.getItemViewType(position) == ArticleAdapter.VIEW_TYPE_SECONDARY) 1 else 2
                    }
                    gridManager
                } else {
                    LinearLayoutManager(this)
                }
                binding.recyclerView.layoutManager = layoutManager
                invalidateOptionsMenu()
                true
            }
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    companion object {
        fun buildNavItems(
            feeds: List<FeedInfo>,
            counts: Map<String, Int>,
            navFilter: NavFilter,
            expandedCategories: Set<String>
        ): List<NavItem> {
            val items = mutableListOf<NavItem>()

            val totalUnread = counts.values.sum()

            // Filter items
            items.add(
                NavItem.FilterItem(
                    filter = NavFilter.AllArticles,
                    label = "All Articles",
                    iconRes = android.R.drawable.ic_menu_agenda,
                    isSelected = navFilter is NavFilter.AllArticles,
                    unreadCount = totalUnread
                )
            )
            items.add(
                NavItem.FilterItem(
                    filter = NavFilter.Today,
                    label = "Today",
                    iconRes = android.R.drawable.ic_menu_today,
                    isSelected = navFilter is NavFilter.Today,
                    unreadCount = 0
                )
            )

            // Group feeds by category
            val categorized = feeds.filter { it.categoryLabel.isNotBlank() }.groupBy { it.categoryLabel }
            val uncategorized = feeds.filter { it.categoryLabel.isBlank() }

            // Categories section
            categorized.keys.sorted().forEach { label ->
                val categoryFeeds = categorized[label] ?: emptyList()
                val categoryCount = categoryFeeds.sumOf { counts[it.id] ?: 0 }
                val isExpanded = label in expandedCategories
                items.add(NavItem.CategoryHeader(label, isExpanded, categoryCount))
                if (isExpanded) {
                    categoryFeeds.sortedBy { it.title }.forEach { feed ->
                        val feedCount = counts[feed.id] ?: 0
                        val isSelected = navFilter is NavFilter.ByFeed && navFilter.feedId == feed.id
                        items.add(NavItem.FeedRow(feed, isSelected, feedCount))
                    }
                }
            }

            // Uncategorized feeds
            uncategorized.sortedBy { it.title }.forEach { feed ->
                val feedCount = counts[feed.id] ?: 0
                val isSelected = navFilter is NavFilter.ByFeed && navFilter.feedId == feed.id
                items.add(NavItem.FeedRow(feed, isSelected, feedCount))
            }

            return items
        }
    }
}
