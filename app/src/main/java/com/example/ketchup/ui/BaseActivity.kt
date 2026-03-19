package com.example.ketchup.ui

import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.ketchup.data.PreferencesManager
import com.example.ketchup.data.SecureStorage

abstract class BaseActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = PreferencesManager(this)
        ThemeHelper.applyTheme(this, prefs)
        if (prefs.fullscreen) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
        }
        if (SecureStorage(this).isPinConfigured()) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        super.onCreate(savedInstanceState)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && PreferencesManager(this).fullscreen) {
            applyFullscreen()
        }
    }

    protected fun applyFullscreen() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            // Only hide the status bar — not the navigation bar.
            // Hiding the nav bar on gesture-navigation devices causes the first
            // back-swipe to merely reveal the bar instead of navigating back.
            // The nav bar is already transparent (set in themes.xml) and content
            // draws behind it (setDecorFitsSystemWindows=false), so it's invisible.
            hide(WindowInsetsCompat.Type.statusBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
