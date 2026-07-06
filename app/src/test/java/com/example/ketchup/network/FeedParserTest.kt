package com.example.ketchup.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.kxml2.io.KXmlParser
import java.io.ByteArrayInputStream

class FeedParserTest {

    private fun parse(xml: String, feedUrl: String = "https://example.com/feed"): ParsedFeed =
        FeedParser { KXmlParser() }.parse(ByteArrayInputStream(xml.toByteArray()), feedUrl)

    // ---- Atom ----

    @Test
    fun atomFeedParses() {
        val feed = parse(
            """<?xml version="1.0" encoding="utf-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom" xmlns:media="http://search.yahoo.com/mrss/">
              <title>Atom Blog</title>
              <entry>
                <id>https://example.com/a1</id>
                <title>First Post</title>
                <link rel="alternate" href="https://example.com/a1.html"/>
                <author><name>Alice</name></author>
                <summary>A summary</summary>
                <content type="html">&lt;p&gt;Body&lt;/p&gt;</content>
                <published>2026-07-01T10:00:00Z</published>
                <media:thumbnail url="https://example.com/t.jpg"/>
              </entry>
            </feed>"""
        )
        assertEquals("Atom Blog", feed.title)
        assertEquals(1, feed.articles.size)
        val a = feed.articles[0]
        assertEquals("https://example.com/a1", a.id)
        assertEquals("First Post", a.title)
        assertEquals("https://example.com/a1.html", a.url)
        assertEquals("Alice", a.author)
        assertEquals("A summary", a.summary)
        assertEquals("<p>Body</p>", a.content)
        assertEquals("https://example.com/t.jpg", a.thumbnailUrl)
        assertTrue(a.publishedMs > 0)
    }

    @Test
    fun atomDateWithNumericOffsetParses() {
        val feed = parse(
            """<feed xmlns="http://www.w3.org/2005/Atom"><title>T</title>
              <entry><id>x</id><title>t</title><link href="https://e.com/x"/>
                <published>2026-07-06T12:00:00+02:00</published>
              </entry></feed>"""
        )
        assertEquals(
            java.time.Instant.parse("2026-07-06T10:00:00Z").toEpochMilli(),
            feed.articles[0].publishedMs,
        )
    }

    @Test
    fun atomSourceTitleIsUsedForAggregatedEntries() {
        val feed = parse(
            """<feed xmlns="http://www.w3.org/2005/Atom"><title>Aggregator</title>
              <entry><id>x</id><title>t</title>
                <source><id>https://orig.example</id><title>Original Site</title></source>
              </entry></feed>"""
        )
        assertEquals("Original Site", feed.articles[0].sourceTitle)
        assertEquals("t", feed.articles[0].title)
    }

    // ---- RSS 2.0 ----

    @Test
    fun rssFeedParses() {
        val feed = parse(
            """<?xml version="1.0"?>
            <rss version="2.0" xmlns:content="http://purl.org/rss/1.0/modules/content/"
                 xmlns:dc="http://purl.org/dc/elements/1.1/">
              <channel>
                <title>RSS Site</title>
                <link>https://example.com</link>
                <description>A site</description>
                <image><title>Logo title</title><url>https://example.com/logo.png</url></image>
                <item>
                  <title>News One</title>
                  <link>https://example.com/news/1</link>
                  <guid isPermaLink="false">news-1</guid>
                  <description>Item summary</description>
                  <content:encoded><![CDATA[<p>Full body</p>]]></content:encoded>
                  <dc:creator>Bob</dc:creator>
                  <pubDate>Mon, 06 Jul 2026 12:00:00 GMT</pubDate>
                  <enclosure url="https://example.com/pic.jpg" type="image/jpeg" length="1000"/>
                </item>
              </channel>
            </rss>"""
        )
        // Channel title wins — not the <image> title, not the item title.
        assertEquals("RSS Site", feed.title)
        assertEquals(1, feed.articles.size)
        val a = feed.articles[0]
        assertEquals("news-1", a.id)
        assertEquals("News One", a.title)
        assertEquals("https://example.com/news/1", a.url)
        assertEquals("Bob", a.author)
        assertEquals("Item summary", a.summary)
        assertEquals("<p>Full body</p>", a.content)
        assertEquals("https://example.com/pic.jpg", a.thumbnailUrl)
        assertEquals(java.time.Instant.parse("2026-07-06T12:00:00Z").toEpochMilli(), a.publishedMs)
    }

    @Test
    fun rssGuidThatIsNotAUrlDoesNotBecomeTheLink() {
        val feed = parse(
            """<rss version="2.0"><channel><title>T</title>
              <item><title>x</title><guid>tag:internal-id-123</guid></item>
            </channel></rss>"""
        )
        assertEquals("tag:internal-id-123", feed.articles[0].id)
        assertEquals("", feed.articles[0].url)
    }

    @Test
    fun rssRelativeLinkIsResolvedAgainstFeedUrl() {
        val feed = parse(
            """<rss version="2.0"><channel><title>T</title>
              <item><title>x</title><guid>g1</guid><link>/news/relative</link></item>
            </channel></rss>""",
            feedUrl = "https://site.example/feeds/all.rss"
        )
        assertEquals("https://site.example/news/relative", feed.articles[0].url)
    }

    @Test
    fun rssMediaContentImageUsedWhenNoThumbnail() {
        val feed = parse(
            """<rss version="2.0" xmlns:media="http://search.yahoo.com/mrss/"><channel><title>T</title>
              <item><title>x</title><guid>g</guid>
                <media:content url="https://e.com/video.mp4" medium="video"/>
                <media:content url="https://e.com/img.jpg" medium="image"/>
              </item>
            </channel></rss>"""
        )
        assertEquals("https://e.com/img.jpg", feed.articles[0].thumbnailUrl)
    }

    @Test
    fun htmlPageYieldsNoArticles() {
        val feed = parse(
            """<html><head><title>Just a homepage</title></head>
            <body><p>hello</p></body></html>"""
        )
        assertTrue(feed.articles.isEmpty())
    }

    @Test
    fun encodingIsSniffedFromXmlDeclaration() {
        val xml = """<?xml version="1.0" encoding="ISO-8859-1"?>
            <rss version="2.0"><channel><title>Café</title>
            <item><title>Crème</title><guid>g</guid></item>
            </channel></rss>"""
        val parser = FeedParser { KXmlParser() }
        val bytes = xml.toByteArray(Charsets.ISO_8859_1)
        val feed = parser.parse(ByteArrayInputStream(bytes), "https://e.com/f")
        assertEquals("Café", feed.title)
        assertEquals("Crème", feed.articles[0].title)
    }

    // ---- Dates ----

    @Test
    fun parseDateHandlesCommonFormats() {
        val p = FeedParser { KXmlParser() }
        // RFC 3339 UTC and offset forms must agree
        assertEquals(
            p.parseDate("2026-07-06T10:00:00Z"),
            p.parseDate("2026-07-06T12:00:00+02:00"),
        )
        // RFC 1123 with GMT and numeric offset
        assertEquals(
            p.parseDate("Mon, 06 Jul 2026 10:00:00 GMT"),
            p.parseDate("Mon, 06 Jul 2026 12:00:00 +0200"),
        )
        // RFC 822 with a named zone
        assertTrue(p.parseDate("Mon, 06 Jul 2026 12:00:00 EST") > 0)
        // Garbage degrades to 0, never throws
        assertEquals(0L, p.parseDate("not a date"))
        assertEquals(0L, p.parseDate(""))
    }

    @Test
    fun feedLevelAuthorDoesNotLeakIntoEntries() {
        val feed = parse(
            """<feed xmlns="http://www.w3.org/2005/Atom"><title>T</title>
              <author><name>Feed Author</name></author>
              <entry><id>x</id><title>t</title></entry></feed>"""
        )
        assertNull(feed.articles[0].author)
    }
}
