package com.example.ketchup.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.text.Html
import coil3.compose.AsyncImage
import com.example.ketchup.data.model.Article
import com.example.ketchup.ui.theme.TextMuted
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun String.stripHtml(): String =
    Html.fromHtml(this, Html.FROM_HTML_MODE_LEGACY).toString().trim()

// Html.fromHtml is expensive and cards recompose whenever the article list
// re-emits; cache the stripped text per article content.
@Composable
private fun rememberStripped(value: String): String =
    androidx.compose.runtime.remember(value) { value.stripHtml() }

// Hero card — full width, tall image, for every 7th article in featured layout
@Composable
fun HeroArticleCard(article: Article, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Box {
            if (article.thumbnailUrl != null) {
                AsyncImage(
                    model = article.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f),
                )
                // Gradient overlay
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color(0xCC000000)),
                                startY = 0.3f,
                            )
                        )
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
            ) {
                FeedSourceRow(article)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = rememberStripped(article.title),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = if (article.thumbnailUrl != null) Color.White else MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// Secondary card — vertical card with image on top, title below, used in side-by-side pairs
@Composable
fun SecondaryArticleCard(article: Article, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column {
            if (article.thumbnailUrl != null) {
                AsyncImage(
                    model = article.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
            Column(
                modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                FeedSourceRow(article)
                Text(
                    text = rememberStripped(article.title),
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = if (article.isRead)
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    else
                        MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// Standard card — horizontal row with thumbnail on right
@Composable
fun StandardArticleCard(article: Article, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
            ) {
                FeedSourceRow(article)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = rememberStripped(article.title),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (article.isRead)
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    else
                        MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!article.summary.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = rememberStripped(article.summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (article.thumbnailUrl != null) {
                Spacer(modifier = Modifier.width(12.dp))
                AsyncImage(
                    model = article.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(8.dp)),
                )
            }
        }
    }
}

@Composable
private fun FeedSourceRow(article: Article) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (article.sourceFaviconUrl != null) {
            AsyncImage(
                model = article.sourceFaviconUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape),
            )
        }
        Text(
            text = article.feedTitle,
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "·",
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
        )
        Text(
            text = formatRelativeTime(article.publishedMs),
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
        )
    }
}

private val monthDayFormat = SimpleDateFormat("MMM d", Locale.getDefault())

private fun formatRelativeTime(publishedMs: Long): String {
    val diffMs = System.currentTimeMillis() - publishedMs
    return when {
        diffMs < 60_000 -> "just now"
        diffMs < 3_600_000 -> "${diffMs / 60_000}m ago"
        diffMs < 86_400_000 -> "${diffMs / 3_600_000}h ago"
        diffMs < 7 * 86_400_000 -> "${diffMs / 86_400_000}d ago"
        else -> monthDayFormat.format(Date(publishedMs))
    }
}
