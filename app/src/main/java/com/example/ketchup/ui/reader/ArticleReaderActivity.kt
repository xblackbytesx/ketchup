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
import com.example.ketchup.data.ArticleRepository
import com.example.ketchup.data.PreferencesManager
import com.example.ketchup.data.SecureStorage
import com.example.ketchup.data.db.AppDatabase
import com.example.ketchup.data.model.Article
import com.example.ketchup.databinding.ActivityArticleReaderBinding
import com.example.ketchup.ui.BaseActivity
import com.example.ketchup.ui.ThemeHelper
import kotlinx.coroutines.launch

class ArticleReaderActivity : BaseActivity() {
    companion object {
        const val EXTRA_ARTICLE = "extra_article"
    }

    private lateinit var binding: ActivityArticleReaderBinding
    private lateinit var article: Article
    private lateinit var repository: ArticleRepository
    private lateinit var prefs: PreferencesManager
    private lateinit var renderer: ArticleRenderer
    private lateinit var assetLoader: WebViewAssetLoader
    private var showingFetchedContent = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityArticleReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        @Suppress("DEPRECATION")
        article = intent.getParcelableExtra(EXTRA_ARTICLE)
            ?: run { finish(); return }

        prefs = PreferencesManager(this)
        renderer = ArticleRenderer(this)
        val app = application as KetchupApplication
        repository = ArticleRepository(
            db = AppDatabase.getInstance(this),
            api = app.api,
            fetcher = app.fetcher,
            secureStorage = SecureStorage(this),
            prefs = prefs
        )

        supportActionBar?.title = article.feedTitle
        supportActionBar?.subtitle = article.title.take(60)

        setupWebView()
        renderContent()

        binding.fabFetch.setOnClickListener { fetchFullContent() }
    }

    override fun onStop() {
        super.onStop()
        if (!::article.isInitialized || !::repository.isInitialized) return
        if (prefs.autoMarkRead) {
            lifecycleScope.launch { repository.markRead(article.id) }
        }
    }

    override fun onDestroy() {
        binding.webView.destroy()
        super.onDestroy()
    }

    private fun setupWebView() {
        assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        binding.webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                return assetLoader.shouldInterceptRequest(request.url)
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, request.url))
                } catch (_: Exception) {}
                return true
            }
        }

        binding.webView.settings.apply {
            javaScriptEnabled = true      // required for Mercury parser
            domStorageEnabled = true      // required for Mercury parser
            allowFileAccess = false       // assets served via AssetLoader, not file://
            allowContentAccess = false
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            loadWithOverviewMode = true
            useWideViewPort = true
        }
    }

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
        val ttlMs = prefs.cacheTtlHours * 3600_000L
        val useFetched = article.fetchedContent != null &&
                repository.isFetchCacheValid(article.fetchedAt, ttlMs)

        val colors = resolveColors()
        val html = if (useFetched) {
            showingFetchedContent = true
            renderer.renderWithFullContent(article, article.fetchedContent!!, colors)
        } else {
            renderer.render(article, colors)
        }

        binding.webView.loadDataWithBaseURL(
            "https://appassets.androidplatform.net/",
            html, "text/html", "UTF-8", null
        )
        invalidateOptionsMenu()
    }

    private fun fetchFullContent() {
        binding.fabFetch.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            val success = repository.fetchAndCacheContent(article.id)
            binding.progressBar.visibility = View.GONE
            binding.fabFetch.isEnabled = true

            if (success) {
                val entity = AppDatabase.getInstance(this@ArticleReaderActivity)
                    .articleDao().getById(article.id)
                val rawHtml = entity?.fetchedContent
                if (rawHtml != null) {
                    showingFetchedContent = true
                    val html = renderer.renderWithFullContent(article, rawHtml, resolveColors())
                    binding.webView.loadDataWithBaseURL(
                        "https://appassets.androidplatform.net/",
                        html, "text/html", "UTF-8", null
                    )
                    invalidateOptionsMenu()
                } else {
                    Toast.makeText(this@ArticleReaderActivity, "Could not extract content", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this@ArticleReaderActivity, "Failed to fetch article", Toast.LENGTH_SHORT).show()
            }
        }
    }

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
