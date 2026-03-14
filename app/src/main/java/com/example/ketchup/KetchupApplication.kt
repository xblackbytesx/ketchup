package com.example.ketchup

import android.app.Application
import coil.Coil
import coil.ImageLoader
import coil.decode.SvgDecoder
import com.example.ketchup.network.ArticleFetcher

class KetchupApplication : Application() {
    @Volatile
    var isAuthenticated: Boolean = false

    val fetcher: ArticleFetcher by lazy { ArticleFetcher() }

    override fun onCreate() {
        super.onCreate()
        Coil.setImageLoader(
            ImageLoader.Builder(this)
                .components { add(SvgDecoder.Factory()) }
                .build()
        )
    }
}
