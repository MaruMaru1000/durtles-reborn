package com.example.durtlesreborn.reading.sources

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Robolectric supplies the real `org.json` implementation that the Android build uses. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SourceParsingTest {
    @Test
    fun nhkEasyLinksAreNewestFirst() {
        val json = """
            {
              "2026-10-29": [
                {"news_id": "k1000", "title": "Newer"},
                {"news_id": "k0900", "title": "Older"}
              ],
              "2026-10-28": [
                {"news_id": "k0800", "title": "Oldest"}
              ]
            }
        """.trimIndent()
        val links = parseNhkEasy(json)
        assertEquals(listOf("k1000", "k0900", "k0800"), links.map { it.url.substringAfterLast('/').substringBefore('.') })
        assertEquals("Newer", links.first().title)
    }

    @Test
    fun anRssFeedKeepsOnlyWebLinks() {
        val xml = """
            <rss><channel>
              <item><title>Good</title><link>https://example.com/a</link></item>
              <item><title>Bad</title><link>javascript:alert(1)</link></item>
            </channel></rss>
        """.trimIndent()
        val links = parseFeed(xml, "https://example.com/")
        assertEquals(1, links.size)
        assertEquals("https://example.com/a", links.first().url)
        assertEquals("Good", links.first().title)
    }

    @Test
    fun anAtomFeedResolvesRelativeLinks() {
        val xml = """
            <feed xmlns="http://www.w3.org/2005/Atom">
              <entry><title>Entry</title><link href="/news/1"/></entry>
            </feed>
        """.trimIndent()
        val links = parseFeed(xml, "https://example.com/feed.xml")
        assertEquals(listOf("https://example.com/news/1"), links.map { it.url })
    }

    @Test
    fun anIndexPageKeepsArticleLinksOnItsOwnHost() {
        val html = """
            <html><body>
              <a href="/a">A real article title</a>
              <a href="/a">A real article title</a>
              <a href="/b">短い</a>
              <a href="javascript:alert(1)">Not a web link</a>
              <a href="https://other.example.com/x">Another site page</a>
              <a href="/files/book.pdf">A PDF download</a>
              <a href="/c">Another real article</a>
            </body></html>
        """.trimIndent()
        val links = parseIndex(html, "https://example.com/news/", limit = 8)
        assertEquals(listOf("https://example.com/a", "https://example.com/c"), links.map { it.url })
    }

    @Test
    fun extractionPrefersTheDensestJapaneseBlock() {
        val html = """
            <html><body>
              <nav>menu menu menu</nav>
              <article>
                <h1>記事の見出し</h1>
                <p>これは日本語の文章です。</p>
                <p>もう一つの段落です。</p>
              </article>
            </body></html>
        """.trimIndent()
        val text = extractText(Jsoup.parse(html))
        assertTrue(text, text.contains("これは日本語の文章です。"))
        assertTrue(text, text.contains("もう一つの段落です。"))
        assertTrue(text, text.contains("\n"))
    }
}

class HttpSourceFetcherTest {
    @Test
    fun aFeedIsReadOverHttp() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setBody(
                """
                <rss><channel>
                  <item><title>Article</title><link>https://example.com/a</link></item>
                </channel></rss>
                """.trimIndent(),
            ),
        )
        server.start()
        try {
            val source = ReadingSource(1, "Test", server.url("/feed.xml").toString(), SourceKind.RSS)
            val links = HttpSourceFetcher().links(source)
            assertEquals(1, links.size)
            assertEquals("https://example.com/a", links.first().url)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun anArticleIsExtractedToPlainText() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/html; charset=UTF-8")
                .setBody(
                    """
                    <html><body>
                      <article>
                        <h1>日本語の記事</h1>
                        <p>これはテストのための日本語の文章です。</p>
                        <p>二つ目の段落も日本語で書かれています。</p>
                      </article>
                    </body></html>
                    """.trimIndent(),
                ),
        )
        server.start()
        try {
            val article = HttpSourceFetcher().extract(server.url("/a").toString())
            assertEquals("日本語の記事", article?.title)
            assertTrue(article?.text.orEmpty(), article?.text.orEmpty().contains("これはテストのための日本語の文章です。"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun nonWebUrlsAreNeverFetched() = runBlocking {
        assertNull(HttpSourceFetcher().extract("file:///etc/passwd"))
    }
}
