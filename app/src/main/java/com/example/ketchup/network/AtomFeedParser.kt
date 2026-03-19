package com.example.ketchup.network

import android.net.Uri
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.text.SimpleDateFormat
import java.util.Locale

data class ParsedArticle(
    val id: String,
    val title: String,
    val url: String,
    val author: String?,
    val sourceTitle: String?,
    val summary: String?,
    val content: String?,
    val thumbnailUrl: String?,
    val publishedMs: Long,
    val sourceFaviconUrl: String?
)

data class ParsedFeed(val title: String, val articles: List<ParsedArticle>)

class AtomFeedParser {
    private val rssDateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.ENGLISH)

    fun parse(responseBody: String, feedUrl: String): ParsedFeed {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        try {
            parser.setFeature("http://xmlpull.org/v1/doc/features.html#process-docdecl", false)
        } catch (_: Exception) { /* feature not supported by this parser impl */ }
        parser.setInput(responseBody.reader())

        var feedTitle = ""
        val articles = mutableListOf<ParsedArticle>()
        var inEntry = false
        var inSource = false
        var inAuthor = false

        var entryId = ""
        var entryTitle = ""
        var entryUrl = ""
        var entryAuthor: String? = null
        var entrySourceTitle: String? = null
        var entrySummary: String? = null
        var entryContent: String? = null
        var entryThumbnailUrl: String? = null
        var entryPublishedMs = 0L

        val currentText = StringBuilder()

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            val name = parser.name ?: ""
            val ns = parser.namespace ?: ""

            when (eventType) {
                XmlPullParser.START_TAG -> {
                    currentText.clear()
                    when {
                        name == "entry" && !inEntry -> {
                            inEntry = true
                            entryId = ""; entryTitle = ""; entryUrl = ""; entryAuthor = null
                            entrySourceTitle = null; entrySummary = null; entryContent = null
                            entryThumbnailUrl = null; entryPublishedMs = 0L
                        }
                        name == "source" && inEntry -> inSource = true
                        name == "author" -> inAuthor = true
                        name == "link" && inEntry -> {
                            val rel = parser.getAttributeValue(null, "rel")
                            val href = parser.getAttributeValue(null, "href")
                            if ((rel == "alternate" || rel == null) && href != null) {
                                entryUrl = href
                            }
                        }
                        name == "thumbnail" && ns == "http://search.yahoo.com/mrss/" && inEntry -> {
                            entryThumbnailUrl = parser.getAttributeValue(null, "url")
                        }
                    }
                }
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> currentText.append(parser.text)
                XmlPullParser.END_TAG -> {
                    val text = currentText.toString().trim()
                    when {
                        name == "entry" && inEntry -> {
                            if (entryId.isNotBlank() || entryUrl.isNotBlank()) {
                                val id = entryId.ifBlank { entryUrl }
                                val url = entryUrl.ifBlank { entryId }
                                val faviconUrl = try {
                                    Uri.parse(url).host
                                        ?.let { "https://icons.duckduckgo.com/ip3/$it.ico" }
                                } catch (_: Exception) { null }
                                articles.add(
                                    ParsedArticle(
                                        id = id,
                                        title = entryTitle,
                                        url = url,
                                        author = entryAuthor,
                                        sourceTitle = entrySourceTitle,
                                        summary = entrySummary,
                                        content = entryContent,
                                        thumbnailUrl = entryThumbnailUrl,
                                        publishedMs = entryPublishedMs,
                                        sourceFaviconUrl = faviconUrl
                                    )
                                )
                            }
                            inEntry = false
                            inSource = false
                            inAuthor = false
                        }
                        name == "source" -> inSource = false
                        name == "author" -> inAuthor = false
                        name == "title" && !inEntry -> feedTitle = text
                        name == "title" && inEntry && inSource -> entrySourceTitle = text
                        name == "title" && inEntry && !inSource -> if (text.isNotBlank()) entryTitle = text
                        name == "id" && inEntry && !inSource -> entryId = text
                        name == "name" && inAuthor && inEntry -> entryAuthor = text.ifBlank { null }
                        name == "summary" && inEntry -> entrySummary = text.ifBlank { null }
                        name == "content" && inEntry -> entryContent = text.ifBlank { null }
                        name == "published" && inEntry -> entryPublishedMs = parseDate(text)
                        name == "updated" && inEntry && entryPublishedMs == 0L -> entryPublishedMs = parseDate(text)
                    }
                    currentText.clear()
                }
            }
            eventType = parser.next()
        }

        return ParsedFeed(
            title = feedTitle.ifBlank { feedUrl },
            articles = articles
        )
    }

    private fun parseDate(dateStr: String): Long {
        if (dateStr.isBlank()) return 0L
        return try {
            java.time.Instant.parse(dateStr).toEpochMilli()
        } catch (_: Exception) {
            try {
                rssDateFormat.parse(dateStr)?.time ?: 0L
            } catch (_: Exception) {
                0L
            }
        }
    }
}
