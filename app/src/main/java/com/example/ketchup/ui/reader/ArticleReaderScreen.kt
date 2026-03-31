package com.example.ketchup.ui.reader

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.webkit.WebViewAssetLoader
import com.example.ketchup.KetchupApplication
import com.example.ketchup.ui.theme.KetchupRed
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ArticleReaderScreen(
    app: KetchupApplication,
    articleId: String,
    filterId: String,
    onBack: () -> Unit,
) {
    val viewModel: ArticleReaderViewModel = viewModel(
        key = articleId,
        factory = ArticleReaderViewModelFactory(app, articleId, filterId),
    )
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current

    var barsVisible by remember { mutableStateOf(true) }

    // WebView CSS uses logical pixels (dp), not device pixels — compute once.
    val density = LocalDensity.current
    val statusBarPx = WindowInsets.statusBars.getTop(density)
    val topBarCssPx = remember(statusBarPx, density) {
        (with(density) { statusBarPx.toDp() } + 56.dp).value.toInt()
    }

    val article = uiState.article ?: return

    val renderer = remember { ArticleRenderer(context) }

    val colors = remember(app.prefsManager.theme, configuration.uiMode) {
        when (app.prefsManager.theme) {
            "oled" -> RendererColors("#000000", "#e5e5ea", "#64a8ff")
            "dark" -> RendererColors("#121212", "#e5e5ea", "#64a8ff")
            "light" -> RendererColors("#ffffff", "#1c1c1e", "#1a6ed8")
            else -> {
                val nightMask = configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                if (nightMask == Configuration.UI_MODE_NIGHT_YES) RendererColors("#121212", "#e5e5ea", "#64a8ff")
                else RendererColors("#ffffff", "#1c1c1e", "#1a6ed8")
            }
        }
    }
    val heroEnabled = app.prefsManager.showHeroImage

    // Bars overlay a full-screen WebView so the WebView never resizes during animation.
    Box(modifier = Modifier.fillMaxSize()) {
        val assetLoader = remember {
            WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
                .build()
        }

        val htmlContent = remember(article, uiState.showingFetchedContent, topBarCssPx) {
            val raw = if (uiState.showingFetchedContent && article.fetchedContent != null) {
                renderer.renderWithFullContent(article, article.fetchedContent, colors, heroEnabled)
            } else {
                renderer.render(article, colors, heroEnabled)
            }
            // Push content below the overlaid top bar so the title is never hidden
            raw.replace("</head>", "<style>body{padding-top:${topBarCssPx}px}</style></head>")
        }

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                        val delta = scrollY - oldScrollY
                        if (abs(delta) > 8) barsVisible = delta < 0
                    }
                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: WebView,
                            request: WebResourceRequest,
                        ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)

                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest,
                        ): Boolean {
                            val scheme = request.url.scheme
                            if (scheme == "http" || scheme == "https") {
                                try {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, request.url)
                                    )
                                } catch (_: Exception) {}
                            }
                            return true
                        }
                    }
                    settings.apply {
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
            },
            update = { webView ->
                webView.loadDataWithBaseURL(
                    "https://appassets.androidplatform.net/",
                    htmlContent,
                    "text/html",
                    "UTF-8",
                    null,
                )
            },
        )

        AnimatedVisibility(
            visible = barsVisible,
            enter = slideInVertically { -it },
            exit = slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
        ) {
            TopAppBar(
                title = {
                    Text(
                        text = article.feedTitle,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(article.url)))
                        } catch (_: Exception) {}
                    }) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open in browser")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        }

        AnimatedVisibility(
            visible = barsVisible,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.background,
                tonalElevation = 0.dp,
            ) {
                Column {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        thickness = 0.5.dp,
                    )
                    val hasNext = uiState.position + 1 < uiState.articleList.size
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .height(52.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        IconButton(onClick = { viewModel.toggleStar() }) {
                            Icon(
                                imageVector = if (article.isStarred) Icons.Filled.Star else Icons.Outlined.Star,
                                contentDescription = if (article.isStarred) "Unstar" else "Star",
                                tint = if (article.isStarred) KetchupRed else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        IconButton(
                            onClick = { viewModel.navigateNext() },
                            enabled = hasNext,
                        ) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "Next article")
                        }
                        if (uiState.isFetchingContent) {
                            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                            }
                        } else {
                            IconButton(onClick = { viewModel.toggleFetchedContent() }) {
                                Icon(
                                    imageVector = if (uiState.showingFetchedContent)
                                        Icons.Default.RssFeed else Icons.Default.Downloading,
                                    contentDescription = if (uiState.showingFetchedContent)
                                        "Show RSS content" else "Fetch full content",
                                    tint = if (uiState.showingFetchedContent) KetchupRed
                                    else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                        IconButton(onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, article.url)
                                putExtra(Intent.EXTRA_SUBJECT, article.title)
                            }
                            context.startActivity(Intent.createChooser(intent, null))
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Share")
                        }
                    }
                }
            }
        }
    }
}
