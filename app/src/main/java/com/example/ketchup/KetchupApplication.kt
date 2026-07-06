package com.example.ketchup

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import coil3.ComponentRegistry
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.svg.SvgDecoder
import com.example.ketchup.auth.AuthManager
import com.example.ketchup.data.ArticleRepository
import com.example.ketchup.data.PreferencesManager
import com.example.ketchup.data.SecureStorage
import com.example.ketchup.data.db.AppDatabase
import com.example.ketchup.network.ArticleFetcher
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class KetchupApplication : Application(), SingletonImageLoader.Factory {
    @Volatile
    var isAuthenticated: Boolean = false

    @Volatile
    private var backgroundedAt: Long = 0L

    val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    val fetcher: ArticleFetcher by lazy { ArticleFetcher(httpClient) }

    val repository: ArticleRepository by lazy {
        ArticleRepository(
            db = AppDatabase.getInstance(this),
            fetcher = fetcher,
            prefs = prefsManager,
            httpClient = httpClient
        )
    }

    val prefsManager: PreferencesManager by lazy { PreferencesManager(this) }

    val secureStorage: SecureStorage by lazy { SecureStorage(this) }

    val authManager: AuthManager by lazy { AuthManager(secureStorage) }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val components = ComponentRegistry.Builder()
            .add(SvgDecoder.Factory())
            .add(OkHttpNetworkFetcherFactory(callFactory = { httpClient }))
            .build()
        return ImageLoader.Builder(context)
            .components(components)
            .build()
    }

    override fun onCreate() {
        super.onCreate()

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                backgroundedAt = System.currentTimeMillis()
            }

            override fun onStart(owner: LifecycleOwner) {
                expireAuthIfTimedOut()
                backgroundedAt = 0L
            }
        })
    }

    /**
     * Drops the authenticated state when the app has been backgrounded past
     * the lock timeout. Also called from the UI's foreground observer, which
     * navigates to the lock screen — this method is what makes the check
     * order-independent between the process observer and the UI observer.
     */
    fun expireAuthIfTimedOut() {
        if (isAuthenticated && backgroundedAt > 0L &&
            System.currentTimeMillis() - backgroundedAt > LOCK_TIMEOUT_MS
        ) {
            isAuthenticated = false
        }
    }

    companion object {
        const val LOCK_TIMEOUT_MS = 60_000L
    }
}
