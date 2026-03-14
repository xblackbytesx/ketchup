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
    useFeaturedLayout: Boolean,
    private val onArticleClick: (Article) -> Unit,
    private val onMarkUnread: (Article) -> Unit
) : ListAdapter<Article, RecyclerView.ViewHolder>(DIFF_CALLBACK) {

    var useFeaturedLayout: Boolean = useFeaturedLayout
        set(value) {
            field = value
            notifyDataSetChanged()
        }



    companion object {
        const val VIEW_TYPE_HERO = 0
        const val VIEW_TYPE_SECONDARY = 1
        const val VIEW_TYPE_STANDARD = 2

        /**
         * Repeating group layout when featured mode is on:
         *   0        → HERO     (full-width, large image + big title + snippet)
         *   1, 2     → SECONDARY (half-width, side-by-side via GridLayoutManager)
         *   3 … N-1  → STANDARD
         */
        const val GROUP_SIZE = 7

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
        if (!useFeaturedLayout) return VIEW_TYPE_STANDARD
        return when (position % GROUP_SIZE) {
            0 -> VIEW_TYPE_HERO
            1, 2 -> VIEW_TYPE_SECONDARY
            else -> VIEW_TYPE_STANDARD
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_HERO -> HeroViewHolder(inflater.inflate(R.layout.item_article_hero, parent, false))
            VIEW_TYPE_SECONDARY -> SecondaryViewHolder(inflater.inflate(R.layout.item_article_secondary, parent, false))
            else -> StandardViewHolder(inflater.inflate(R.layout.item_article_standard, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val article = getItem(position)
        val timeStr = formatTimeAgo(article.publishedMs)
        val alpha = if (article.isRead) 0.6f else 1.0f

        when (holder) {
            is HeroViewHolder -> holder.bind(article, timeStr, alpha, article.sourceFaviconUrl)
            is SecondaryViewHolder -> holder.bind(article, timeStr, alpha, article.sourceFaviconUrl)
            is StandardViewHolder -> holder.bind(article, timeStr, alpha, article.sourceFaviconUrl)
        }

        holder.itemView.setOnClickListener { onArticleClick(article) }
        holder.itemView.setOnLongClickListener {
            onMarkUnread(article)
            true
        }
    }

    // ── Hero ─────────────────────────────────────────────────────────────────

    inner class HeroViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivThumbnail: ImageView = itemView.findViewById(R.id.iv_thumbnail)
        private val ivFallback: ImageView = itemView.findViewById(R.id.iv_fallback)
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

            loadFavicon(ivFavicon, faviconUrl)

            if (article.thumbnailUrl != null) {
                ivFallback.visibility = View.GONE
                ivThumbnail.load(article.thumbnailUrl) { crossfade(true) }
            } else {
                ivFallback.visibility = View.VISIBLE
                ivThumbnail.load(null as String?)  // cancels any pending request and clears the view
            }

            val snippet = htmlToSnippet(article.summary)
            tvSnippet.text = snippet
            tvSnippet.visibility = if (snippet.isNotBlank()) View.VISIBLE else View.GONE
        }
    }

    // ── Secondary (half-width) ────────────────────────────────────────────────

    inner class SecondaryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivThumbnail: ImageView = itemView.findViewById(R.id.iv_thumbnail)
        private val ivFallback: ImageView = itemView.findViewById(R.id.iv_fallback)
        private val ivFavicon: ImageView = itemView.findViewById(R.id.iv_favicon)
        private val tvTitle: TextView = itemView.findViewById(R.id.tv_title)
        private val tvFeed: TextView = itemView.findViewById(R.id.tv_feed)
        private val tvDate: TextView = itemView.findViewById(R.id.tv_date)

        fun bind(article: Article, timeStr: String, alpha: Float, faviconUrl: String?) {
            itemView.alpha = alpha
            tvTitle.text = article.title
            tvFeed.text = article.feedTitle
            tvDate.text = timeStr

            loadFavicon(ivFavicon, faviconUrl)

            if (article.thumbnailUrl != null) {
                ivFallback.visibility = View.GONE
                ivThumbnail.load(article.thumbnailUrl) { crossfade(true) }
            } else {
                ivFallback.visibility = View.VISIBLE
                ivThumbnail.load(null as String?)  // cancels any pending request and clears the view
            }
        }
    }

    // ── Standard ─────────────────────────────────────────────────────────────

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

            loadFavicon(ivFavicon, faviconUrl)

            val snippet = htmlToSnippet(article.summary)
            tvSnippet.text = snippet
            tvSnippet.visibility = if (snippet.isNotBlank()) View.VISIBLE else View.GONE

            if (article.thumbnailUrl != null) {
                ivThumbnail.visibility = View.VISIBLE
                ivThumbnail.load(article.thumbnailUrl) { crossfade(true) }
            } else {
                ivThumbnail.visibility = View.GONE
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun loadFavicon(iv: ImageView, url: String?) {
        if (url != null) {
            iv.load(url) {
                crossfade(true)
                error(R.drawable.ic_rss)
            }
        } else {
            iv.setImageResource(R.drawable.ic_rss)
        }
    }
}
