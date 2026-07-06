package com.example.ketchup.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("ketchup_prefs", Context.MODE_PRIVATE)

    var theme: String
        get() = prefs.getString(KEY_THEME, "system") ?: "system"
        set(value) = prefs.edit().putString(KEY_THEME, value).apply()

    var showReadArticles: Boolean
        get() = prefs.getBoolean(KEY_SHOW_READ, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_READ, value).apply()

    var featuredLayout: Boolean
        get() = prefs.getBoolean(KEY_FEATURED_LAYOUT, false)
        set(value) = prefs.edit().putBoolean(KEY_FEATURED_LAYOUT, value).apply()

    var cacheTtlHours: Int
        get() = prefs.getInt(KEY_CACHE_TTL_HOURS, 24)
        set(value) = prefs.edit().putInt(KEY_CACHE_TTL_HOURS, value).apply()

    var autoMarkRead: Boolean
        get() = prefs.getBoolean(KEY_AUTO_MARK_READ, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_MARK_READ, value).apply()

    var sortOrder: String
        get() = prefs.getString(KEY_SORT_ORDER, "newest_first") ?: "newest_first"
        set(value) = prefs.edit().putString(KEY_SORT_ORDER, value).apply()

    /**
     * Emits the current sort order and every subsequent change. The feed list
     * combines this so a change made on the settings screen applies as soon as
     * the user returns, instead of waiting for the next filter change.
     */
    fun observeSortOrder(): Flow<String> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_SORT_ORDER) trySend(sortOrder)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(sortOrder)
        // awaitClose keeps a strong reference to the listener; SharedPreferences
        // itself only holds it weakly.
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    // Max articles kept per feed; pruning only touches articles that have
    // dropped out of the source feed and are not starred. 0 = unlimited.
    var retentionMaxArticles: Int
        get() = prefs.getInt(KEY_RETENTION_MAX_ARTICLES, 200)
        set(value) = prefs.edit().putInt(KEY_RETENTION_MAX_ARTICLES, value).apply()

    var fullscreen: Boolean
        get() = prefs.getBoolean(KEY_FULLSCREEN, true)
        set(value) = prefs.edit().putBoolean(KEY_FULLSCREEN, value).apply()

    var showHeroImage: Boolean
        get() = prefs.getBoolean(KEY_SHOW_HERO_IMAGE, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_HERO_IMAGE, value).apply()

    var swipeNavigation: Boolean
        get() = prefs.getBoolean(KEY_SWIPE_NAVIGATION, true)
        set(value) = prefs.edit().putBoolean(KEY_SWIPE_NAVIGATION, value).apply()

    companion object {
        private const val KEY_THEME = "theme"
        private const val KEY_SHOW_READ = "show_read_articles"
        private const val KEY_FEATURED_LAYOUT = "featured_layout"
        private const val KEY_CACHE_TTL_HOURS = "cache_ttl_hours"
        private const val KEY_AUTO_MARK_READ = "auto_mark_read"
        private const val KEY_SORT_ORDER = "sort_order"
        private const val KEY_RETENTION_MAX_ARTICLES = "retention_max_articles"
        private const val KEY_FULLSCREEN = "fullscreen"
        private const val KEY_SHOW_HERO_IMAGE = "show_hero_image"
        private const val KEY_SWIPE_NAVIGATION = "swipe_navigation"
    }
}
