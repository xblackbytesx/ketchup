package com.example.ketchup

import android.app.Application
import com.example.ketchup.network.ArticleFetcher
import com.example.ketchup.network.FreshRssApi

class KetchupApplication : Application() {
    @Volatile
    var isAuthenticated: Boolean = false

    // Shared singletons — one OkHttpClient each, reused across the app
    val api: FreshRssApi by lazy { FreshRssApi() }
    val fetcher: ArticleFetcher by lazy { ArticleFetcher() }
}
