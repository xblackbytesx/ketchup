package com.example.ketchup.ui.reader

import android.content.Context
import com.example.ketchup.data.model.Article
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ArticleRenderer(private val context: Context) {
    private val template: String by lazy {
        context.assets.open("template.html").bufferedReader().readText()
    }
    private val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())

    fun render(article: Article, colors: RendererColors): String {
        return template
            .replace("{{title}}", article.title.escapeHtml())
            .replace("{{feed_name}}", article.feedTitle.escapeHtml())
            .replace("{{byline}}", buildByline(article).escapeHtml())
            .replace("{{body}}", article.summary ?: "<p class=\"no-content\">No article content in the feed.<br>Tap the button below to fetch the full article.</p>")
            .replace("{{color_bg}}", colors.bg)
            .replace("{{color_fg}}", colors.fg)
            .replace("{{color_accent}}", colors.accent)
            .replace("{{full_content_script}}", "")
    }

    fun renderWithFullContent(article: Article, rawHtml: String, colors: RendererColors): String {
        val script = "<script>displayFullContent(${JSONObject.quote(article.url)}, ${JSONObject.quote(rawHtml)});</script>"
        return template
            .replace("{{title}}", article.title.escapeHtml())
            .replace("{{feed_name}}", article.feedTitle.escapeHtml())
            .replace("{{byline}}", buildByline(article).escapeHtml())
            .replace("{{body}}", article.summary ?: "")
            .replace("{{color_bg}}", colors.bg)
            .replace("{{color_fg}}", colors.fg)
            .replace("{{color_accent}}", colors.accent)
            .replace("{{full_content_script}}", script)
    }

    private fun buildByline(article: Article): String {
        val dateStr = dateFormat.format(Date(article.publishedMs))
        return listOfNotNull(article.author, dateStr).joinToString(" · ")
    }
}

data class RendererColors(val bg: String, val fg: String, val accent: String)

private fun String.escapeHtml(): String =
    replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
