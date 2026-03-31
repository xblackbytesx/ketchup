package com.example.ketchup.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.ketchup.data.model.FeedInfo
import com.example.ketchup.data.model.NavFilter
import com.example.ketchup.ui.theme.KetchupRed
import com.example.ketchup.ui.theme.TextMuted

@Composable
fun FeedNavDrawer(
    feeds: List<FeedInfo>,
    unreadCounts: Map<String, Int>,
    currentFilter: NavFilter,
    onFilterSelected: (NavFilter) -> Unit,
    onAddFeed: () -> Unit,
    onEditFeed: (FeedInfo) -> Unit,
    onDeleteFeed: (FeedInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard: ClipboardManager = LocalClipboardManager.current
    val categories = feeds.map { it.categoryLabel }.distinct().sorted()
    val expandedCategories = remember { mutableStateOf(setOf<String>()) }

    LazyColumn(modifier = modifier.padding(vertical = 8.dp)) {
        // Filters section
        item {
            NavigationDrawerItem(
                icon = { Icon(Icons.Default.AllInclusive, contentDescription = null) },
                label = { Text("All articles") },
                badge = {
                    val total = unreadCounts.values.sum()
                    if (total > 0) UnreadBadge(total)
                },
                selected = currentFilter == NavFilter.AllArticles,
                onClick = { onFilterSelected(NavFilter.AllArticles) },
                colors = drawerItemColors(),
            )
        }
        item {
            NavigationDrawerItem(
                icon = { Icon(Icons.Default.Today, contentDescription = null) },
                label = { Text("Today") },
                selected = currentFilter == NavFilter.Today,
                onClick = { onFilterSelected(NavFilter.Today) },
                colors = drawerItemColors(),
            )
        }
        item {
            NavigationDrawerItem(
                icon = { Icon(Icons.Default.Star, contentDescription = null, tint = KetchupRed) },
                label = { Text("Starred") },
                selected = currentFilter == NavFilter.Starred,
                onClick = { onFilterSelected(NavFilter.Starred) },
                colors = drawerItemColors(),
            )
        }

        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp))
        }

        // Feeds header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Feeds",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextMuted,
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(onClick = onAddFeed) {
                    Icon(Icons.Default.Add, contentDescription = "Add feed", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        // Uncategorized feeds
        val uncategorized = feeds.filter { it.categoryLabel.isBlank() }
        items(uncategorized) { feed ->
            FeedDrawerItem(
                feed = feed,
                unreadCount = unreadCounts[feed.id] ?: 0,
                selected = currentFilter is NavFilter.ByFeed && (currentFilter as NavFilter.ByFeed).feedId == feed.id,
                onClick = { onFilterSelected(NavFilter.ByFeed(feed.id, feed.title, feed.faviconUrl)) },
                onCopyUrl = { clipboard.setText(AnnotatedString(feed.feedUrl)) },
                onEdit = { onEditFeed(feed) },
                onDelete = { onDeleteFeed(feed) },
            )
        }

        // Categorized feeds grouped
        categories.filter { it.isNotBlank() }.forEach { category ->
            val categoryFeeds = feeds.filter { it.categoryLabel == category }
            val isExpanded = expandedCategories.value.contains(category)

            item(key = "cat_$category") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            expandedCategories.value = if (isExpanded) {
                                expandedCategories.value - category
                            } else {
                                expandedCategories.value + category
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = TextMuted,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = category,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    val catUnread = categoryFeeds.sumOf { unreadCounts[it.id] ?: 0 }
                    if (catUnread > 0) {
                        UnreadBadge(catUnread)
                    }
                }
            }

            item(key = "cat_content_$category") {
                AnimatedVisibility(visible = isExpanded) {
                    Column {
                        categoryFeeds.forEach { feed ->
                            FeedDrawerItem(
                                feed = feed,
                                unreadCount = unreadCounts[feed.id] ?: 0,
                                selected = currentFilter is NavFilter.ByFeed &&
                                        (currentFilter as NavFilter.ByFeed).feedId == feed.id,
                                onClick = { onFilterSelected(NavFilter.ByFeed(feed.id, feed.title, feed.faviconUrl)) },
                                onCopyUrl = { clipboard.setText(AnnotatedString(feed.feedUrl)) },
                                onEdit = { onEditFeed(feed) },
                                onDelete = { onDeleteFeed(feed) },
                                modifier = Modifier.padding(start = 16.dp),
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun FeedDrawerItem(
    feed: FeedInfo,
    unreadCount: Int,
    selected: Boolean,
    onClick: () -> Unit,
    onCopyUrl: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showMenu by remember { mutableStateOf(false) }

    NavigationDrawerItem(
        icon = {
            if (feed.faviconUrl != null) {
                AsyncImage(
                    model = feed.faviconUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape),
                )
            } else {
                Icon(Icons.Default.RssFeed, contentDescription = null, modifier = Modifier.size(20.dp))
            }
        },
        label = {
            Text(
                text = feed.title,
                maxLines = 1,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        badge = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (unreadCount > 0) UnreadBadge(unreadCount)
                // Context menu anchor
                TextButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(24.dp),
                ) {}
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = { showMenu = false; onEdit() },
                    )
                    DropdownMenuItem(
                        text = { Text("Copy URL") },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                        onClick = { showMenu = false; onCopyUrl() },
                    )
                    DropdownMenuItem(
                        text = { Text("Remove", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { showMenu = false; onDelete() },
                    )
                }
            }
        },
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        colors = drawerItemColors(),
    )
}

@Composable
private fun UnreadBadge(count: Int) {
    Text(
        text = if (count > 99) "99+" else count.toString(),
        style = MaterialTheme.typography.labelSmall,
        color = KetchupRed,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun drawerItemColors() = NavigationDrawerItemDefaults.colors(
    selectedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
)
