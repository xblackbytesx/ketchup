package com.example.ketchup.ui.feed

import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.ketchup.R
import com.example.ketchup.data.model.Article
import java.text.SimpleDateFormat
import java.util.*

class ArticleAdapter(
    private val useFeaturedLayout: Boolean,
    private val onArticleClick: (Article) -> Unit,
    private val onMarkUnread: (Article) -> Unit
) : ListAdapter<Article, RecyclerView.ViewHolder>(DIFF_CALLBACK) {

    var feedFaviconMap: Map<String, String?> = emptyMap()

    companion object {
        private const val VIEW_TYPE_FEATURED = 0
        private const val VIEW_TYPE_STANDARD = 1

        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Article>() {
            override fun areItemsTheSame(oldItem: Article, newItem: Article) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Article, newItem: Article) = oldItem == newItem
        }

        @Suppress("DEPRECATION")
        fun htmlToSnippet(html: String?): String {
            if (html.isNullOrBlank()) return ""
            val plain = Html.fromHtml(html).toString()
            return plain.replace(Regex("\\s+"), " ").trim()
        }

        fun formatTimeAgo(publishedMs: Long): String {
            val diff = System.currentTimeMillis() - publishedMs
            return when {
                diff < 60 * 60 * 1000L -> "${diff / 60_000}m"
                diff < 24 * 60 * 60 * 1000L -> "${diff / 3_600_000}h"
                diff < 7 * 24 * 60 * 60 * 1000L -> "${diff / 86_400_000}d"
                else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(publishedMs))
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (useFeaturedLayout && position == 0) VIEW_TYPE_FEATURED else VIEW_TYPE_STANDARD
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_FEATURED) {
            FeaturedViewHolder(inflater.inflate(R.layout.item_article_featured, parent, false))
        } else {
            StandardViewHolder(inflater.inflate(R.layout.item_article_standard, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val article = getItem(position)
        val timeStr = formatTimeAgo(article.publishedMs)
        val alpha = if (article.isRead) 0.6f else 1.0f
        val faviconUrl = feedFaviconMap[article.feedId]

        when (holder) {
            is FeaturedViewHolder -> holder.bind(article, timeStr, alpha, faviconUrl)
            is StandardViewHolder -> holder.bind(article, timeStr, alpha, faviconUrl)
        }

        holder.itemView.setOnClickListener { onArticleClick(article) }
        holder.itemView.setOnLongClickListener {
            onMarkUnread(article)
            true
        }
    }

    inner class FeaturedViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivThumbnail: ImageView = itemView.findViewById(R.id.iv_thumbnail)
        private val ivFavicon: ImageView = itemView.findViewById(R.id.iv_favicon)
        private val tvTitle: TextView = itemView.findViewById(R.id.tv_title)
        private val tvFeed: TextView = itemView.findViewById(R.id.tv_feed)
        private val tvDate: TextView = itemView.findViewById(R.id.tv_date)

        fun bind(article: Article, timeStr: String, alpha: Float, faviconUrl: String?) {
            itemView.alpha = alpha
            tvTitle.text = article.title
            tvFeed.text = article.feedTitle
            tvDate.text = timeStr
            if (faviconUrl != null) {
                ivFavicon.load(faviconUrl) {
                    crossfade(true)
                    error(R.drawable.ic_rss)
                }
            } else {
                ivFavicon.setImageResource(R.drawable.ic_rss)
            }
            if (article.thumbnailUrl != null) {
                ivThumbnail.visibility = View.VISIBLE
                ivThumbnail.load(article.thumbnailUrl)
            } else {
                ivThumbnail.visibility = View.GONE
            }
        }
    }

    inner class StandardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivThumbnail: ImageView = itemView.findViewById(R.id.iv_thumbnail)
        private val ivFavicon: ImageView = itemView.findViewById(R.id.iv_favicon)
        private val tvTitle: TextView = itemView.findViewById(R.id.tv_title)
        private val tvSnippet: TextView = itemView.findViewById(R.id.tv_snippet)
        private val tvFeed: TextView = itemView.findViewById(R.id.tv_feed)
        private val tvDate: TextView = itemView.findViewById(R.id.tv_date)

        fun bind(article: Article, timeStr: String, alpha: Float, faviconUrl: String?) {
            itemView.alpha = alpha
            tvTitle.text = article.title
            tvFeed.text = article.feedTitle
            tvDate.text = timeStr
            if (faviconUrl != null) {
                ivFavicon.load(faviconUrl) {
                    crossfade(true)
                    error(R.drawable.ic_rss)
                }
            } else {
                ivFavicon.setImageResource(R.drawable.ic_rss)
            }
            val snippet = htmlToSnippet(article.summary)
            tvSnippet.text = snippet
            tvSnippet.visibility = if (snippet.isNotBlank()) View.VISIBLE else View.GONE
            if (article.thumbnailUrl != null) {
                ivThumbnail.visibility = View.VISIBLE
                ivThumbnail.load(article.thumbnailUrl)
            } else {
                ivThumbnail.visibility = View.GONE
            }
        }
    }
}
