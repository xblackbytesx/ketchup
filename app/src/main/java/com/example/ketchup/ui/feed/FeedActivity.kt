package com.example.ketchup.ui.feed

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
import com.example.ketchup.ui.setup.SetupActivity
import com.google.android.material.snackbar.Snackbar
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
                    .putExtra(ArticleReaderActivity.EXTRA_ARTICLE_ID, article.id)
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

        // FAB: show when scrolled past a few items, hide when back at top
        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val firstVisible = (recyclerView.layoutManager as? LinearLayoutManager)
                    ?.findFirstVisibleItemPosition()
                    ?: (recyclerView.layoutManager as? GridLayoutManager)
                        ?.findFirstVisibleItemPosition()
                    ?: 0
                if (firstVisible > 3) {
                    showFab()
                } else {
                    hideFab()
                }
            }
        })
        binding.fabScrollTop.setOnClickListener {
            binding.recyclerView.scrollToPosition(0)
            hideFab()
        }

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
            },
            onFeedLongPress = { feed -> showFeedOptions(feed) }
        )

        binding.navDrawer.rvNav.layoutManager = LinearLayoutManager(this)
        binding.navDrawer.rvNav.adapter = navDrawerAdapter

        binding.swipeRefresh.setOnRefreshListener { viewModel.refresh() }

        // Observe UI state
        var wasRefreshing = false
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
                        if (wasRefreshing && !state.isRefreshing && state.articles.isNotEmpty()) {
                            showFab()
                        }
                        wasRefreshing = state.isRefreshing
                    }
                    is FeedUiState.Error -> {
                        binding.progressBar.visibility = View.GONE
                        binding.errorText.visibility = View.VISIBLE
                        binding.errorText.text = state.message
                        binding.tvEmptyState.visibility = View.GONE
                        binding.swipeRefresh.isRefreshing = false
                        wasRefreshing = false
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

        lifecycleScope.launch {
            viewModel.syncError.collect { message ->
                Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun showFab() {
        binding.fabScrollTop.show()
    }

    private fun hideFab() {
        binding.fabScrollTop.hide()
    }

    private fun showFeedOptions(feed: FeedInfo) {
        val options = arrayOf("Edit Feed", "Copy URL", "Remove Feed")
        AlertDialog.Builder(this)
            .setTitle(feed.title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showEditFeedDialog(feed)
                    1 -> {
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Feed URL", feed.feedUrl))
                        Snackbar.make(binding.root, "URL copied", Snackbar.LENGTH_SHORT).show()
                    }
                    2 -> AlertDialog.Builder(this)
                        .setTitle("Remove feed")
                        .setMessage("Remove \"${feed.title}\"? This will also delete all its cached articles.")
                        .setPositiveButton("Remove") { _, _ -> viewModel.deleteFeed(feed.id) }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
            .show()
    }

    private fun showEditFeedDialog(feed: FeedInfo) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, 0)
        }
        val etTitle = EditText(this).apply {
            hint = "Title"
            setText(feed.title)
        }
        val etCategory = EditText(this).apply {
            hint = "Category (leave blank for none)"
            setText(feed.categoryLabel)
        }
        container.addView(etTitle)
        container.addView(etCategory)

        AlertDialog.Builder(this)
            .setTitle("Edit Feed")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val newTitle = etTitle.text.toString().trim().ifEmpty { feed.title }
                val newCategory = etCategory.text.toString().trim()
                viewModel.updateFeed(feed.id, newTitle, newCategory)
            }
            .setNegativeButton("Cancel", null)
            .show()
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
            R.id.action_add_feed -> {
                startActivity(Intent(this, SetupActivity::class.java))
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
            items.add(
                NavItem.FilterItem(
                    filter = NavFilter.Starred,
                    label = "Starred",
                    iconRes = android.R.drawable.btn_star_big_on,
                    isSelected = navFilter is NavFilter.Starred,
                    unreadCount = 0
                )
            )

            // Group feeds by category
            val categorized = feeds.filter { it.categoryLabel.isNotBlank() }.groupBy { it.categoryLabel }
            val uncategorized = feeds.filter { it.categoryLabel.isBlank() }

            if (feeds.isNotEmpty()) {
                items.add(NavItem.SectionLabel("Feeds"))
            }

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
