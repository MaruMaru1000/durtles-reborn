package com.example.durtlesreborn.reading.sources

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser
import java.util.concurrent.TimeUnit

/** How a source lists its articles. */
enum class SourceKind { NHK_EASY, RSS, HTML_INDEX }

data class ReadingSource(val id: Long, val name: String, val url: String, val kind: SourceKind)

data class ArticleLink(val title: String, val url: String)

data class ExtractedArticle(val title: String, val text: String)

/** Fetches a source's article list and turns an article page into plain text. */
interface SourceFetcher {
    suspend fun links(source: ReadingSource, limit: Int = 8): List<ArticleLink>
    suspend fun extract(url: String): ExtractedArticle?
}

/**
 * The default sources. NHK Easy publishes a JSON index; the other two are regular index pages that
 * are walked for article links. All fetching happens on the device, and failures simply produce an
 * empty list so the library can say "nothing new" instead of breaking.
 */
object DefaultSources {
    val all = listOf(
        // The news-list.json endpoint answers 401 now; the Easy index page lists the same articles.
        ReadingSource(0, "NHK News Web Easy", "https://www3.nhk.or.jp/news/easy/", SourceKind.HTML_INDEX),
        ReadingSource(0, "Tadoku Free Graded Readers", "https://tadoku.org/japanese/free-books/", SourceKind.HTML_INDEX),
        ReadingSource(0, "Aozora Bunko", "https://www.aozora.gr.jp/", SourceKind.HTML_INDEX),
    )
}

/** Downloads with OkHttp and extracts with jsoup. Nothing is sent away: only GETs are made. */
class HttpSourceFetcher(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build(),
) : SourceFetcher {

    override suspend fun links(source: ReadingSource, limit: Int): List<ArticleLink> = withContext(Dispatchers.IO) {
        runCatching {
            require(source.url.isWebUrl()) { "Not an http(s) source" }
            val body = get(source.url)
            when (source.kind) {
                SourceKind.NHK_EASY -> parseNhkEasy(body).take(limit)
                SourceKind.RSS -> parseFeed(body, source.url).take(limit)
                SourceKind.HTML_INDEX -> parseIndex(body, source.url, limit)
            }
        }.getOrDefault(emptyList())
    }

    override suspend fun extract(url: String): ExtractedArticle? = withContext(Dispatchers.IO) {
        if (!url.isWebUrl()) return@withContext null
        runCatching {
            val doc = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(25_000)
                .get()
            val title = doc.selectFirst("h1")?.text()?.takeIf { it.isNotBlank() } ?: doc.title()
            val text = extractText(doc)
            if (text.count { it.isJapanese() } < 20) null else ExtractedArticle(title, text)
        }.getOrNull()
    }

    private fun get(url: String): String {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code}" }
            return response.body?.string().orEmpty()
        }
    }
}

/** Parses NHK Easy's news-list.json into article links, newest first. */
internal fun parseNhkEasy(json: String): List<ArticleLink> {
    val root = JSONObject(json)
    val dates = root.keys().asSequence().sortedDescending()
    val out = ArrayList<ArticleLink>()
    for (date in dates) {
        val items = root.optJSONArray(date) ?: continue
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val id = item.optString("news_id").takeIf { it.isNotBlank() } ?: continue
            val title = item.optString("title").ifBlank { id }
            out += ArticleLink(title, "https://www3.nhk.or.jp/news/easy/$id/$id.html")
        }
    }
    return out
}

/** Parses RSS 2.0 and Atom feeds. */
internal fun parseFeed(xml: String, baseUrl: String): List<ArticleLink> {
    val doc = Jsoup.parse(xml, baseUrl, Parser.xmlParser())
    val rss = doc.select("item").mapNotNull { item ->
        val link = item.selectFirst("link")?.text()?.takeIf { it.isNotBlank() && it.isWebUrl() }
            ?: return@mapNotNull null
        ArticleLink(item.selectFirst("title")?.text().orEmpty().ifBlank { link }, link)
    }
    if (rss.isNotEmpty()) return rss
    return doc.select("entry").mapNotNull { entry ->
        val link = entry.selectFirst("link[href]")?.absUrl("href")?.takeIf { it.isWebUrl() }
            ?: return@mapNotNull null
        ArticleLink(entry.selectFirst("title")?.text().orEmpty().ifBlank { link }, link)
    }
}

/** Walks a normal index page for links that look like articles. */
internal fun parseIndex(html: String, baseUrl: String, limit: Int): List<ArticleLink> {
    val doc = Jsoup.parse(html, baseUrl)
    val baseHost = runCatching { java.net.URI(baseUrl).host }.getOrNull()
    val out = ArrayList<ArticleLink>()
    val seen = HashSet<String>()
    for (anchor in doc.select("a[href]")) {
        val url = anchor.absUrl("href").takeIf { it.isWebUrl() } ?: continue
        if (baseHost != null && runCatching { java.net.URI(url).host }.getOrNull() != baseHost) continue
        val lower = url.lowercase()
        if (lower.endsWith(".pdf") || lower.endsWith(".zip") || lower.endsWith(".mp3") ||
            lower.endsWith(".jpg") || lower.endsWith(".png") || lower.endsWith(".gif") ||
            lower.contains("/rss/") || lower.contains("#")
        ) {
            continue
        }
        val text = anchor.text().trim()
        if (text.length < 4 || text.length > 120) continue
        if (url == baseUrl || url == baseUrl.trimEnd('/')) continue
        if (url in seen) continue
        seen += url
        out += ArticleLink(text, url)
        if (out.size >= limit) break
    }
    return out
}

/** Picks the densest block of Japanese text and joins its paragraphs. */
internal fun extractText(doc: Document): String {
    val candidates = doc.select("article, main, [role=main], section, div")
    val best = candidates.maxByOrNull { element -> element.text().count { it.isJapanese() } }
    val paragraphs = (best ?: doc.body())
        .select("p, h1, h2, h3, li")
        .map { it.text().trim() }
        .filter { it.isNotBlank() }
    return if (paragraphs.size >= 2) paragraphs.joinToString("\n") else (best ?: doc.body()).text().trim()
}

private fun Char.isJapanese(): Boolean = this in '\u3040'..'\u30ff' || this in '\u4e00'..'\u9fff'

/** Only real web pages are ever fetched or opened in the WebView. */
internal fun String.isWebUrl(): Boolean = startsWith("https://") || startsWith("http://")

private const val USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126 Mobile Safari/537.36"
