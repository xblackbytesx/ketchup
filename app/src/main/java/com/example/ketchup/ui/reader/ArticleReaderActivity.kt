package com.example.ketchup.ui.reader

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebViewAssetLoader
import com.example.ketchup.KetchupApplication
import com.example.ketchup.R
import com.example.ketchup.data.PreferencesManager
import com.example.ketchup.data.model.Article
import com.example.ketchup.databinding.ActivityArticleReaderBinding
import com.example.ketchup.ui.BaseActivity
import com.example.ketchup.ui.ThemeHelper
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

class ArticleReaderActivity : BaseActivity() {
    companion object {
        const val EXTRA_ARTICLE_ID  = "extra_article_id"
        const val EXTRA_ARTICLE_IDS = "extra_article_ids"
        const val EXTRA_POSITION    = "extra_position"
    }

    private lateinit var binding: ActivityArticleReaderBinding
    private lateinit var article: Article
    private lateinit var prefs: PreferencesManager
    private lateinit var renderer: ArticleRenderer
    private lateinit var assetLoader: WebViewAssetLoader
    private val repository by lazy { (application as KetchupApplication).repository }

    private var articleIds: List<String> = emptyList()
    private var position: Int = 0
    private var showingFetchedContent = false
    private var barsVisible = true
    private var lastScrollY = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityArticleReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val articleId = intent.getStringExtra(EXTRA_ARTICLE_ID)
            ?: run { finish(); return }

        articleIds = intent.getStringArrayListExtra(EXTRA_ARTICLE_IDS) ?: emptyList()
        position = intent.getIntExtra(EXTRA_POSITION, 0)

        prefs = PreferencesManager(this)
        renderer = ArticleRenderer(this)

        setupWebView()
        setupScrollBehavior()
        setupBottomBar()

        lifecycleScope.launch {
            val loaded = repository.getArticleById(articleId)
                ?: run { finish(); return@launch }
            loadArticle(loaded)
        }
    }

    private fun loadArticle(a: Article) {
        article = a
        showingFetchedContent = false
        supportActionBar?.title = a.feedTitle
        updateStarIcon()
        updateNextButton()
        renderContent()
        // Snap bars back into view for the new article
        showBars(animate = false)
        lastScrollY = 0
    }

    override fun onStop() {
        super.onStop()
        if (!::article.isInitialized) return
        if (prefs.autoMarkRead) {
            lifecycleScope.launch { withContext(NonCancellable) { repository.markRead(article.id) } }
        }
    }

    override fun onDestroy() {
        binding.webView.destroy()
        super.onDestroy()
    }

    // ── WebView ───────────────────────────────────────────────────────────────

    private fun setupWebView() {
        assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        binding.webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                return assetLoader.shouldInterceptRequest(request.url)
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val scheme = request.url.scheme
                if (scheme == "http" || scheme == "https") {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, request.url))
                    } catch (_: Exception) {
                        Toast.makeText(this@ArticleReaderActivity, "No browser found", Toast.LENGTH_SHORT).show()
                    }
                }
                return true
            }
        }

        binding.webView.settings.apply {
            javaScriptEnabled = false
            allowFileAccess = false
            allowContentAccess = false
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            loadWithOverviewMode = true
            useWideViewPort = true
        }
    }

    private fun setupScrollBehavior() {
        binding.webView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            val delta = scrollY - lastScrollY
            lastScrollY = scrollY
            if (abs(delta) < 8) return@setOnScrollChangeListener
            val show = delta < 0 || scrollY == 0
            if (show != barsVisible) {
                barsVisible = show
                animateBars(show)
            }
        }
    }

    private fun animateBars(show: Boolean) {
        val appBarOffset = if (show) 0f else -binding.appBarLayout.height.toFloat()
        val bottomOffset = if (show) 0f else (binding.root.height - binding.bottomBar.top).toFloat()

        binding.appBarLayout.animate().translationY(appBarOffset).setDuration(220).start()
        binding.bottomBar.animate().translationY(bottomOffset).setDuration(220).start()
    }

    private fun showBars(animate: Boolean) {
        barsVisible = true
        if (animate) {
            animateBars(true)
        } else {
            binding.appBarLayout.translationY = 0f
            binding.bottomBar.translationY = 0f
        }
    }

    // ── Bottom bar ────────────────────────────────────────────────────────────

    private fun setupBottomBar() {
        binding.btnStar.setOnClickListener {
            val newStarred = !article.isStarred
            article = article.copy(isStarred = newStarred)
            updateStarIcon()
            lifecycleScope.launch { repository.toggleStar(article.id, newStarred) }
        }

        binding.btnNext.setOnClickListener {
            val nextId = articleIds.getOrNull(position + 1) ?: return@setOnClickListener
            binding.btnNext.isEnabled = false
            lifecycleScope.launch {
                val entity = repository.getArticleById(nextId)
                binding.btnNext.isEnabled = true
                if (entity != null) {
                    position++
                    loadArticle(entity)
                }
            }
        }

        binding.btnFetch.setOnClickListener {
            if (showingFetchedContent) {
                showingFetchedContent = false
                renderContent()
            } else {
                fetchFullContent()
            }
        }

        binding.btnShare.setOnClickListener {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, article.url)
                putExtra(Intent.EXTRA_SUBJECT, article.title)
            }
            startActivity(Intent.createChooser(shareIntent, null))
        }
    }

    private fun updateStarIcon() {
        binding.btnStar.setImageResource(
            if (article.isStarred) R.drawable.ic_star_filled else R.drawable.ic_star_outline
        )
    }

    private fun updateNextButton() {
        val hasNext = articleIds.getOrNull(position + 1) != null
        binding.btnNext.isEnabled = hasNext
        binding.btnNext.alpha = if (hasNext) 1f else 0.3f
    }

    private fun updateFetchIcon() {
        binding.btnFetch.setImageResource(
            if (showingFetchedContent) R.drawable.ic_rss else R.drawable.ic_article
        )
    }

    // ── Content rendering ─────────────────────────────────────────────────────

    private fun resolveColors(): RendererColors {
        return when (prefs.theme) {
            ThemeHelper.THEME_OLED  -> RendererColors("#000000", "#e5e5ea", "#64a8ff")
            ThemeHelper.THEME_DARK  -> RendererColors("#121212", "#e5e5ea", "#64a8ff")
            ThemeHelper.THEME_LIGHT -> RendererColors("#ffffff", "#1c1c1e", "#1a6ed8")
            else -> {
                val nightMask = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                if (nightMask == Configuration.UI_MODE_NIGHT_YES) {
                    RendererColors("#121212", "#e5e5ea", "#64a8ff")
                } else {
                    RendererColors("#ffffff", "#1c1c1e", "#1a6ed8")
                }
            }
        }
    }

    private fun renderContent() {
        val useFetched = article.fetchedContent != null &&
                repository.isFetchCacheValid(article.fetchedAt)

        val colors = resolveColors()
        val heroEnabled = prefs.showHeroImage
        val html = if (useFetched) {
            showingFetchedContent = true
            renderer.renderWithFullContent(article, article.fetchedContent!!, colors, heroEnabled)
        } else {
            renderer.render(article, colors, heroEnabled)
        }

        binding.webView.loadDataWithBaseURL(
            "https://appassets.androidplatform.net/",
            html, "text/html", "UTF-8", null
        )
        updateFetchIcon()
        invalidateOptionsMenu()
    }

    private fun fetchFullContent() {
        binding.btnFetch.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            val success = repository.fetchAndCacheContent(article.id)
            binding.progressBar.visibility = View.GONE
            binding.btnFetch.isEnabled = true

            if (success) {
                val updated = repository.getArticleById(article.id)
                val rawHtml = updated?.fetchedContent
                if (rawHtml != null) {
                    showingFetchedContent = true
                    val html = renderer.renderWithFullContent(article, rawHtml, resolveColors(), prefs.showHeroImage)
                    binding.webView.loadDataWithBaseURL(
                        "https://appassets.androidplatform.net/",
                        html, "text/html", "UTF-8", null
                    )
                    updateFetchIcon()
                    invalidateOptionsMenu()
                } else {
                    Toast.makeText(this@ArticleReaderActivity, "Could not extract content", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this@ArticleReaderActivity, "Failed to fetch article", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ── Menu ──────────────────────────────────────────────────────────────────

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.reader_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_rss_view)?.isVisible = showingFetchedContent
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_open_browser -> {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(article.url)))
                } catch (_: Exception) {
                    Toast.makeText(this, "No browser found", Toast.LENGTH_SHORT).show()
                }
                true
            }
            R.id.action_rss_view -> {
                showingFetchedContent = false
                renderContent()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
