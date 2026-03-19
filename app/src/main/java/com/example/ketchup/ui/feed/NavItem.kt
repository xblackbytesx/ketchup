package com.example.ketchup.ui.feed

import com.example.ketchup.data.model.FeedInfo
import com.example.ketchup.data.model.NavFilter

sealed class NavItem {
    data class FilterItem(
        val filter: NavFilter,
        val label: String,
        val iconRes: Int,
        val isSelected: Boolean,
        val unreadCount: Int
    ) : NavItem()

    data class CategoryHeader(
        val label: String,
        val isExpanded: Boolean,
        val unreadCount: Int
    ) : NavItem()

    data class FeedRow(
        val feed: FeedInfo,
        val isSelected: Boolean,
        val unreadCount: Int
    ) : NavItem()

    data class SectionLabel(val label: String) : NavItem()
}
