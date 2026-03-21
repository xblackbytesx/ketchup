package com.example.ketchup.ui.reader

import android.content.Context
import com.example.ketchup.data.model.Article
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ArticleRenderer(private val context: Context) {
    private val template: String by lazy {
        context.assets.open("template.html").bufferedReader().readText()
    }
    private val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())

    fun render(article: Article, colors: RendererColors, showHeroImage: Boolean = true): String {
        val body = article.summary?.sanitizeHtml()
            ?: "<p class=\"no-content\">No article content in the feed.<br>Tap the button below to fetch the full article.</p>"
        val hero = if (showHeroImage) buildHeroImage(article.thumbnailUrl, body) else ""
        return template
            .replace("{{title}}", article.title.escapeHtml())
            .replace("{{feed_name}}", article.feedTitle.escapeHtml())
            .replace("{{byline}}", buildByline(article).escapeHtml())
            .replace("{{hero_image}}", hero)
            .replace("{{body}}", body)
            .replace("{{color_bg}}", colors.bg)
            .replace("{{color_fg}}", colors.fg)
            .replace("{{color_accent}}", colors.accent)
    }

    fun renderWithFullContent(article: Article, rawHtml: String, colors: RendererColors, showHeroImage: Boolean = true): String {
        val sanitized = rawHtml.sanitizeHtml()
        val hero = if (showHeroImage) buildHeroImage(article.thumbnailUrl, sanitized) else ""
        return template
            .replace("{{title}}", article.title.escapeHtml())
            .replace("{{feed_name}}", article.feedTitle.escapeHtml())
            .replace("{{byline}}", buildByline(article).escapeHtml())
            .replace("{{hero_image}}", hero)
            .replace("{{body}}", sanitized)
            .replace("{{color_bg}}", colors.bg)
            .replace("{{color_fg}}", colors.fg)
            .replace("{{color_accent}}", colors.accent)
    }

    private fun buildByline(article: Article): String {
        val dateStr = dateFormat.format(Date(article.publishedMs))
        return listOfNotNull(article.author, dateStr).joinToString(" · ")
    }

    /**
     * Builds a hero image tag from the article's thumbnail URL. Returns empty
     * string when there is no thumbnail or when the same URL already appears
     * in the body content (avoids showing the image twice).
     */
    private fun buildHeroImage(thumbnailUrl: String?, bodyHtml: String): String {
        if (thumbnailUrl.isNullOrBlank()) return ""
        if (bodyHtml.contains(thumbnailUrl)) return ""
        return "<img class=\"hero-image\" src=\"${thumbnailUrl.escapeHtml()}\" alt=\"\" />"
    }
}

data class RendererColors(val bg: String, val fg: String, val accent: String)

private fun String.escapeHtml(): String =
    replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

private val DANGEROUS_TAGS = Regex(
    "<\\s*/?(script|iframe|object|embed|form|applet|link|meta|base|svg|math)(\\s[^>]*)?>",
    RegexOption.IGNORE_CASE
)
private val EVENT_HANDLERS = Regex(
    "\\s+on\\w+\\s*=\\s*[\"'][^\"']*[\"']",
    RegexOption.IGNORE_CASE
)
private val STYLE_URL = Regex(
    "url\\s*\\([^)]*\\)",
    RegexOption.IGNORE_CASE
)

private fun String.sanitizeHtml(): String {
    var s = DANGEROUS_TAGS.replace(this, "")
    s = EVENT_HANDLERS.replace(s, "")
    // Strip url() from inline style attributes to prevent CSS exfiltration
    s = s.replace(Regex("style\\s*=\\s*\"[^\"]*\"", RegexOption.IGNORE_CASE)) { match ->
        STYLE_URL.replace(match.value, "url()")
    }
    return s
}
