package com.example.ketchup.ui.feed

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
import com.example.ketchup.data.model.FeedInfo
import com.example.ketchup.data.model.NavFilter

class NavDrawerAdapter(
    private val onFilterSelected: (NavFilter) -> Unit,
    private val onCategoryToggle: (String) -> Unit,
    private val onFeedLongPress: (FeedInfo) -> Unit = {}
) : ListAdapter<NavItem, RecyclerView.ViewHolder>(DIFF_CALLBACK) {

    companion object {
        private const val TYPE_FILTER = 0
        private const val TYPE_CATEGORY_HEADER = 1
        private const val TYPE_FEED_ROW = 2
        private const val TYPE_SECTION_LABEL = 3

        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<NavItem>() {
            override fun areItemsTheSame(oldItem: NavItem, newItem: NavItem): Boolean {
                return when {
                    oldItem is NavItem.FilterItem && newItem is NavItem.FilterItem ->
                        oldItem.filter::class == newItem.filter::class &&
                            oldItem.filter == newItem.filter
                    oldItem is NavItem.CategoryHeader && newItem is NavItem.CategoryHeader ->
                        oldItem.label == newItem.label
                    oldItem is NavItem.FeedRow && newItem is NavItem.FeedRow ->
                        oldItem.feed.id == newItem.feed.id
                    oldItem is NavItem.SectionLabel && newItem is NavItem.SectionLabel ->
                        oldItem.label == newItem.label
                    else -> false
                }
            }

            override fun areContentsTheSame(oldItem: NavItem, newItem: NavItem): Boolean {
                return oldItem == newItem
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is NavItem.FilterItem -> TYPE_FILTER
            is NavItem.CategoryHeader -> TYPE_CATEGORY_HEADER
            is NavItem.FeedRow -> TYPE_FEED_ROW
            is NavItem.SectionLabel -> TYPE_SECTION_LABEL
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_FILTER -> FilterViewHolder(inflater.inflate(R.layout.item_nav_filter, parent, false))
            TYPE_CATEGORY_HEADER -> CategoryViewHolder(inflater.inflate(R.layout.item_nav_category, parent, false))
            TYPE_SECTION_LABEL -> SectionLabelViewHolder(inflater.inflate(R.layout.item_nav_section, parent, false))
            else -> FeedViewHolder(inflater.inflate(R.layout.item_nav_feed, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is NavItem.FilterItem -> (holder as FilterViewHolder).bind(item)
            is NavItem.CategoryHeader -> (holder as CategoryViewHolder).bind(item)
            is NavItem.FeedRow -> (holder as FeedViewHolder).bind(item)
            is NavItem.SectionLabel -> (holder as SectionLabelViewHolder).bind(item)
        }
    }

    inner class FilterViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivIcon: ImageView = itemView.findViewById(R.id.iv_icon)
        private val tvLabel: TextView = itemView.findViewById(R.id.tv_label)
        private val tvCount: TextView = itemView.findViewById(R.id.tv_count)

        fun bind(item: NavItem.FilterItem) {
            ivIcon.setImageResource(item.iconRes)
            tvLabel.text = item.label
            if (item.unreadCount > 0) {
                tvCount.visibility = View.VISIBLE
                tvCount.text = item.unreadCount.toString()
            } else {
                tvCount.visibility = View.GONE
            }
            if (item.isSelected) {
                itemView.setBackgroundResource(R.color.nav_selected_bg)
            } else {
                itemView.background = null
            }
            itemView.setOnClickListener { onFilterSelected(item.filter) }
        }
    }

    inner class CategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvLabel: TextView = itemView.findViewById(R.id.tv_label)
        private val tvCount: TextView = itemView.findViewById(R.id.tv_count)
        private val ivExpand: ImageView = itemView.findViewById(R.id.iv_expand)

        fun bind(item: NavItem.CategoryHeader) {
            tvLabel.text = item.label
            if (item.unreadCount > 0) {
                tvCount.visibility = View.VISIBLE
                tvCount.text = item.unreadCount.toString()
            } else {
                tvCount.visibility = View.GONE
            }
            ivExpand.rotation = if (item.isExpanded) 180f else 0f
            itemView.setOnClickListener { onCategoryToggle(item.label) }
        }
    }

    inner class SectionLabelViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvLabel: TextView = itemView.findViewById(R.id.tv_section_label)
        fun bind(item: NavItem.SectionLabel) {
            tvLabel.text = item.label
        }
    }

    inner class FeedViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivFavicon: ImageView = itemView.findViewById(R.id.iv_favicon)
        private val tvTitle: TextView = itemView.findViewById(R.id.tv_title)
        private val tvCount: TextView = itemView.findViewById(R.id.tv_count)

        fun bind(item: NavItem.FeedRow) {
            tvTitle.text = item.feed.title
            if (item.unreadCount > 0) {
                tvCount.visibility = View.VISIBLE
                tvCount.text = item.unreadCount.toString()
            } else {
                tvCount.visibility = View.GONE
            }
            if (item.feed.faviconUrl != null) {
                ivFavicon.load(item.feed.faviconUrl) {
                    crossfade(true)
                    error(R.drawable.ic_rss)
                }
            } else {
                ivFavicon.setImageResource(R.drawable.ic_rss)
            }
            if (item.isSelected) {
                itemView.setBackgroundResource(R.color.nav_selected_bg)
            } else {
                itemView.background = null
            }
            itemView.setOnClickListener {
                onFilterSelected(
                    NavFilter.ByFeed(item.feed.id, item.feed.title, item.feed.faviconUrl)
                )
            }
            itemView.setOnLongClickListener {
                onFeedLongPress(item.feed)
                true
            }
        }
    }
}
