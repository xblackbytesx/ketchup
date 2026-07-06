package com.example.ketchup.network

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.net.URI
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
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

/**
 * Pull-parser for both Atom 1.0 (`<entry>`) and RSS 2.0 (`<item>`) feeds.
 *
 * Input is a raw stream so the XML parser can sniff the encoding from the BOM /
 * XML declaration instead of assuming UTF-8. The parser factory is injectable
 * so JVM unit tests can supply kxml2 directly (android.util.Xml is
 * framework-only); production code uses the default.
 *
 * All parse state is method-local and the date formatters are immutable, so a
 * single instance is safe to share across concurrent feed syncs.
 */
class FeedParser(private val newParser: () -> XmlPullParser = { Xml.newPullParser() }) {

    private companion object {
        const val MEDIA_NS = "http://search.yahoo.com/mrss/"
        const val CONTENT_NS = "http://purl.org/rss/1.0/modules/content/"
        const val DC_NS = "http://purl.org/dc/elements/1.1/"

        // RFC 822 dates with a named zone ("EST", "PDT") that RFC_1123_DATE_TIME
        // rejects. DateTimeFormatter instances are immutable and thread-safe,
        // unlike the SimpleDateFormat this replaces.
        val RFC822_NAMED_ZONE: DateTimeFormatter =
            DateTimeFormatter.ofPattern("[EEE, ]d MMM yyyy HH:mm[:ss] zzz", Locale.ENGLISH)
    }

    fun parse(input: InputStream, feedUrl: String): ParsedFeed {
        val parser = newParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        try {
            parser.setFeature("http://xmlpull.org/v1/doc/features.html#process-docdecl", false)
        } catch (_: Exception) { /* feature not supported by this parser impl */ }
        parser.setInput(input, null) // null charset → detect from BOM / XML declaration

        var feedTitle = ""
        val articles = mutableListOf<ParsedArticle>()
        var inEntry = false
        var inSource = false
        var inAuthor = false
        var inImage = false // RSS <channel><image> — its <title>/<link> must not leak into the feed's

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
                        (name == "entry" || name == "item") && !inEntry -> {
                            inEntry = true
                            entryId = ""; entryTitle = ""; entryUrl = ""; entryAuthor = null
                            entrySourceTitle = null; entrySummary = null; entryContent = null
                            entryThumbnailUrl = null; entryPublishedMs = 0L
                        }
                        name == "source" && inEntry -> inSource = true
                        name == "author" -> inAuthor = true
                        name == "image" && !inEntry -> inImage = true
                        // Atom link carries the URL in @href; RSS link is element text
                        // (handled in END_TAG below).
                        name == "link" && inEntry -> {
                            val rel = parser.getAttributeValue(null, "rel")
                            val href = parser.getAttributeValue(null, "href")
                            if ((rel == "alternate" || rel == null) && href != null) {
                                entryUrl = href
                            }
                        }
                        name == "thumbnail" && ns == MEDIA_NS && inEntry -> {
                            parser.getAttributeValue(null, "url")?.let { entryThumbnailUrl = it }
                        }
                        // media:content — only when explicitly an image; don't steal
                        // a media:thumbnail that was already found.
                        name == "content" && ns == MEDIA_NS && inEntry && entryThumbnailUrl == null -> {
                            val medium = parser.getAttributeValue(null, "medium")
                            val type = parser.getAttributeValue(null, "type") ?: ""
                            if (medium == "image" || type.startsWith("image/")) {
                                entryThumbnailUrl = parser.getAttributeValue(null, "url")
                            }
                        }
                        name == "enclosure" && inEntry && entryThumbnailUrl == null -> {
                            val type = parser.getAttributeValue(null, "type") ?: ""
                            if (type.startsWith("image/")) {
                                entryThumbnailUrl = parser.getAttributeValue(null, "url")
                            }
                        }
                    }
                }
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> currentText.append(parser.text)
                XmlPullParser.END_TAG -> {
                    val text = currentText.toString().trim()
                    when {
                        (name == "entry" || name == "item") && inEntry -> {
                            if (entryId.isNotBlank() || entryUrl.isNotBlank()) {
                                val id = entryId.ifBlank { entryUrl }
                                // An RSS guid is often not a URL — only use it as the
                                // link of last resort when it actually looks like one.
                                val url = absolutize(
                                    entryUrl.ifBlank { if (entryId.startsWith("http")) entryId else "" },
                                    feedUrl,
                                )
                                articles.add(
                                    ParsedArticle(
                                        id = id,
                                        title = entryTitle,
                                        url = url,
                                        author = entryAuthor,
                                        sourceTitle = entrySourceTitle,
                                        summary = entrySummary,
                                        content = entryContent,
                                        thumbnailUrl = entryThumbnailUrl?.let { absolutize(it, feedUrl) },
                                        publishedMs = entryPublishedMs,
                                        sourceFaviconUrl = faviconFor(url)
                                    )
                                )
                            }
                            inEntry = false
                            inSource = false
                            inAuthor = false
                        }
                        name == "source" -> {
                            // RSS: <source url="...">Name</source>; Atom uses a nested
                            // <title> captured by the inSource branch below.
                            if (inEntry && text.isNotBlank() && entrySourceTitle == null) {
                                entrySourceTitle = text
                            }
                            inSource = false
                        }
                        name == "author" -> {
                            // RSS author is element text; Atom nests <name> (below).
                            if (inEntry && text.isNotBlank() && entryAuthor == null) {
                                entryAuthor = text
                            }
                            inAuthor = false
                        }
                        name == "image" -> inImage = false
                        name == "title" && !inEntry && !inImage && feedTitle.isBlank() -> feedTitle = text
                        name == "title" && inEntry && inSource -> entrySourceTitle = text
                        name == "title" && inEntry && !inSource -> if (text.isNotBlank()) entryTitle = text
                        (name == "id" || name == "guid") && inEntry && !inSource -> entryId = text
                        name == "link" && inEntry && entryUrl.isBlank() && text.isNotBlank() -> entryUrl = text
                        name == "name" && inAuthor && inEntry -> entryAuthor = text.ifBlank { null }
                        (name == "summary" || name == "description") && inEntry -> entrySummary = text.ifBlank { null }
                        name == "creator" && ns == DC_NS && inEntry -> if (text.isNotBlank()) entryAuthor = text
                        name == "content" && ns != MEDIA_NS && inEntry -> entryContent = text.ifBlank { null }
                        name == "encoded" && ns == CONTENT_NS && inEntry -> entryContent = text.ifBlank { null }
                        (name == "published" || name == "pubDate") && inEntry -> entryPublishedMs = parseDate(text)
                        (name == "updated" || (name == "date" && ns == DC_NS)) && inEntry && entryPublishedMs == 0L ->
                            entryPublishedMs = parseDate(text)
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

    /** Resolves a relative article/thumbnail URL against the feed URL. */
    private fun absolutize(url: String, feedUrl: String): String {
        if (url.isBlank() || url.startsWith("http://") || url.startsWith("https://")) return url
        return try {
            URI(feedUrl).resolve(url.trim()).toString()
        } catch (_: Exception) {
            url
        }
    }

    private fun faviconFor(url: String): String? = try {
        URI(url).host?.let { "https://icons.duckduckgo.com/ip3/$it.ico" }
    } catch (_: Exception) {
        null
    }

    /**
     * Parses Atom RFC 3339 timestamps (including numeric offsets like +02:00,
     * which Instant.parse rejects) and RSS RFC 822/1123 pubDates.
     */
    internal fun parseDate(dateStr: String): Long {
        val s = dateStr.trim()
        if (s.isEmpty()) return 0L
        try {
            return OffsetDateTime.parse(s).toInstant().toEpochMilli()
        } catch (_: Exception) { }
        try {
            return OffsetDateTime.parse(s, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
        } catch (_: Exception) { }
        try {
            return ZonedDateTime.parse(s, RFC822_NAMED_ZONE).toInstant().toEpochMilli()
        } catch (_: Exception) { }
        return 0L
    }
}
