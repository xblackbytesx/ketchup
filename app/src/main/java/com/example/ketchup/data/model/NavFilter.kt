package com.example.ketchup.data.model

sealed class NavFilter {
    object AllArticles : NavFilter()
    object Today : NavFilter()
    data class ByCategory(val label: String) : NavFilter()
    data class ByFeed(val feedId: String, val title: String, val faviconUrl: String?) : NavFilter()

    fun displayName(): String = when (this) {
        is AllArticles -> "All Articles"
        is Today -> "Today"
        is ByCategory -> label
        is ByFeed -> title
    }
}
