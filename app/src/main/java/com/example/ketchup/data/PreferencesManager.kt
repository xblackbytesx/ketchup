package com.example.ketchup.data

import android.content.Context
import android.content.SharedPreferences

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

    var fullscreen: Boolean
        get() = prefs.getBoolean(KEY_FULLSCREEN, true)
        set(value) = prefs.edit().putBoolean(KEY_FULLSCREEN, value).apply()

    var showHeroImage: Boolean
        get() = prefs.getBoolean(KEY_SHOW_HERO_IMAGE, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_HERO_IMAGE, value).apply()

    companion object {
        private const val KEY_THEME = "theme"
        private const val KEY_SHOW_READ = "show_read_articles"
        private const val KEY_FEATURED_LAYOUT = "featured_layout"
        private const val KEY_CACHE_TTL_HOURS = "cache_ttl_hours"
        private const val KEY_AUTO_MARK_READ = "auto_mark_read"
        private const val KEY_SORT_ORDER = "sort_order"
        private const val KEY_FULLSCREEN = "fullscreen"
        private const val KEY_SHOW_HERO_IMAGE = "show_hero_image"
    }
}
