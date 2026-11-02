package com.example.durtlesreborn.reading.ui

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.example.durtlesreborn.reading.core.ArticleScore
import com.example.durtlesreborn.reading.core.AssignmentLite
import com.example.durtlesreborn.reading.core.JapaneseTokenizer
import com.example.durtlesreborn.reading.core.KuromojiTokenizer
import com.example.durtlesreborn.reading.core.Matcher
import com.example.durtlesreborn.reading.core.ReadingPalette
import com.example.durtlesreborn.reading.core.Scorer
import com.example.durtlesreborn.reading.core.SubjectLite
import com.example.durtlesreborn.reading.core.SubjectType
import com.example.durtlesreborn.reading.core.Token
import com.example.durtlesreborn.reading.core.TokenCodec
import com.example.durtlesreborn.reading.core.TokenMatch
import com.example.durtlesreborn.reading.core.WaniKaniReadOnly
import com.example.durtlesreborn.reading.sources.ArticleLink
import com.example.durtlesreborn.reading.sources.DefaultSources
import com.example.durtlesreborn.reading.sources.HttpSourceFetcher
import com.example.durtlesreborn.reading.sources.ReadingSource
import com.example.durtlesreborn.reading.sources.SourceFetcher
import com.example.durtlesreborn.reading.sources.SourceKind
import com.example.durtlesreborn.reading.store.ArticleEntity
import com.example.durtlesreborn.reading.store.ArticleSummary
import com.example.durtlesreborn.reading.store.ExampleEntity
import com.example.durtlesreborn.reading.store.ReadingStore
import com.example.durtlesreborn.reading.store.SourceEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** What the library shows. */
data class LibraryState(
    val loading: Boolean = false,
    val articles: List<ArticleSummary> = emptyList(),
    val sources: List<ReadingSource> = emptyList(),
    val message: String? = null,
)

/** One article opened in the reader. */
data class ReaderData(
    val id: Long,
    val title: String,
    val sourceName: String,
    val url: String,
    val text: String,
    val tokens: List<Token>,
    val matches: List<TokenMatch?>,
) {
    val score: ArticleScore get() = Scorer.score(com.example.durtlesreborn.reading.core.TokenizedArticle(tokens, matches))
}

enum class HighlightMode(val label: String) { KNOWN("Known"), UNKNOWN("Unknown"), ALL("All"), OFF("Off") }

/**
 * Owns the reading data flow: loads sources, fetches and scores articles, answers the reader and
 * re-scores cached articles whenever the WaniKani assignments change. Everything is persisted in
 * `reading.db`; the app's database is only read through [WaniKaniReadOnly].
 */
class ReadingController(
    private val context: Context,
    private val wanikani: WaniKaniReadOnly,
    private val store: ReadingStore,
    private val scope: CoroutineScope,
    @Volatile var palette: ReadingPalette,
    private val fetcher: SourceFetcher = HttpSourceFetcher(),
    private val tokenizer: JapaneseTokenizer = KuromojiTokenizer(),
) {
    private val _library = MutableStateFlow(LibraryState())
    val library: StateFlow<LibraryState> = _library.asStateFlow()

    /** Web-reader text settings, shared across articles while the feature is alive. */
    var webFontZoom by mutableIntStateOf(100)
    var webLineWidth by mutableIntStateOf(40)

    private val seedMutex = Mutex()
    private val refreshMutex = Mutex()

    @Volatile private var subjects: List<SubjectLite> = emptyList()
    @Volatile private var assignments: List<AssignmentLite> = emptyList()

    fun start() {
        launchSafely {
            combine(wanikani.subjects(), wanikani.assignments()) { s, a -> s to a }
                .collect { (s, a) ->
                    subjects = s
                    assignments = a
                    rescoreCached()
                }
        }
        refreshLibrary()
    }

    /** Reading is optional: a failed job must never take the rest of the app down. */
    private fun launchSafely(block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The library keeps its last good state; the next refresh retries.
            }
        }
    }

    /** Stops the WaniKani collectors and any refresh in flight; the feature is inert after this. */
    fun close() {
        scope.cancel()
    }

    /** Reloads the list from the store and makes sure the default sources exist. */
    fun refreshLibrary() {
        launchSafely {
            ensureSources()
            store.prune()
            val sources = store.sources().map(::toSource)
            _library.value = LibraryState(
                loading = _library.value.loading,
                articles = store.articles().map(::toSummary),
                sources = sources,
                message = _library.value.message,
            )
        }
    }

    /**
     * Seeds the default sources once. Called from the feature start and from the library entry, so
     * the check and insert have to be atomic or the defaults are added more than once.
     */
    private suspend fun ensureSources() = seedMutex.withLock {
        val stored = store.sources()
        stored.groupBy { it.url }.values
            .filter { it.size > 1 }
            .forEach { copies -> copies.drop(1).forEach { store.deleteSource(it.id) } }
        if (!store.seeded()) {
            if (stored.isEmpty()) {
                for (default in DefaultSources.all) {
                    store.addSource(default.name, default.url, default.kind.name)
                }
            }
            // The marker makes "remove every source" stick across launches.
            store.markSeeded()
        }
        // Repair a default the user kept if its URL or kind changed between app versions.
        for (default in DefaultSources.all) {
            val existing = store.sources().firstOrNull { it.name == default.name } ?: continue
            if (existing.url != default.url || existing.kind != default.kind.name) {
                store.updateSource(existing.id, default.url, default.kind.name)
            }
        }
    }

    /** Fetches every source, extracts new articles, scores them and stores them. */
    fun refreshInBackground() {
        scope.launch {
            _library.value = _library.value.copy(loading = true, message = null)
            try {
                val added = refreshNow()
                _library.value = _library.value.copy(
                    loading = false,
                    articles = store.articles().map(::toSummary),
                    sources = store.sources().map(::toSource),
                    message = if (added == 0) "No new articles." else "Added $added articles.",
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Refresh failed", e)
                _library.value = _library.value.copy(loading = false, message = "Could not fetch articles.")
            }
        }
    }

    /** The suspend form, used by the background worker. Never throws. */
    suspend fun refreshNow(): Int = refreshMutex.withLock { refreshLocked() }

    private suspend fun refreshLocked(): Int = try {
        // The worker may run in a fresh process before the UI exists, so do not assume start() has
        // finished: seed the sources and wait for the first read-only database snapshot.
        ensureSources()
        if (subjects.isEmpty()) subjects = wanikani.subjects().first()
        if (assignments.isEmpty()) assignments = wanikani.assignments().first()
        var added = 0
        for (source in store.sources()) {
            val links = try {
                fetcher.links(toSource(source))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Could not list ${source.name} (${source.url})", e)
                emptyList()
            }
            Log.i(TAG, "${source.name}: ${links.size} article links")
            var addedHere = 0
            for (link in links) {
                try {
                    if (store.articleAt(source.id, link.url) != null) continue
                    val article = fetcher.extract(link.url)
                    if (article == null) {
                        Log.w(TAG, "No Japanese text extracted from ${link.url}")
                        continue
                    }
                    if (saveArticle(source, link, article.title, article.text)) {
                        added++
                        addedHere++
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Could not save ${link.url}", e)
                }
            }
            Log.i(TAG, "${source.name}: added $addedHere")
        }
        store.prune()
        added
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "Refresh failed", e)
        0
    }

    fun addSource(name: String, url: String) {
        launchSafely {
            val clean = url.trim()
            if (name.isBlank() || !clean.isHttpUrl()) return@launchSafely
            val kind = if (clean.looksLikeFeed()) SourceKind.RSS else SourceKind.HTML_INDEX
            store.addSource(name.trim(), clean, kind.name)
            refreshLibrary()
        }
    }

    fun removeSource(id: Long) {
        launchSafely {
            store.deleteSource(id)
            refreshLibrary()
        }
    }

    fun clearAll() {
        launchSafely {
            store.clearAll()
            refreshLibrary()
        }
    }

    suspend fun openArticle(id: Long): ReaderData? = withContext(Dispatchers.Default) {
        val article = store.article(id) ?: return@withContext null
        val tokens = TokenCodec.decode(article.encodedTokens)
        val matcher = Matcher(subjects, assignments)
        ReaderData(
            id = article.id,
            title = article.title,
            sourceName = article.sourceName,
            url = article.url,
            text = article.text,
            tokens = tokens,
            matches = matcher.match(tokens),
        )
    }

    /** The current read-only copies, for the web reader's tap popup. */
    fun subject(id: Int): SubjectLite? = subjects.firstOrNull { it.id == id }

    fun stageOf(subjectId: Int): Int? = assignments.firstOrNull { it.subjectId == subjectId }?.srsStage

    /**
     * Spans for one text node of the web reader: JSON objects with start, end, CSS colour and the
     * subject id. Tokenizing happens here on the device; the page never leaves the WebView.
     */
    fun webSpans(text: String, mode: HighlightMode): String {
        val tokens = tokenizer.tokenize(text)
        val matches = Matcher(subjects, assignments).match(tokens)
        val out = org.json.JSONArray()
        for (match in matches) {
            if (match == null) continue
            val color = highlightColor(match, mode, palette) ?: continue
            val span = org.json.JSONObject()
            span.put("s", match.token.start)
            span.put("e", match.token.end)
            span.put("c", cssColor(color))
            span.put("sid", match.subject.id)
            out.put(span)
        }
        return out.toString()
    }

    private fun cssColor(color: Color): String {
        val argb = color.toArgb()
        val alpha = ((argb ushr 24) and 0xFF) / 255f
        val red = (argb shr 16) and 0xFF
        val green = (argb shr 8) and 0xFF
        val blue = argb and 0xFF
        return "rgba($red,$green,$blue,${String.format(java.util.Locale.ROOT, "%.2f", alpha * 0.45f)})"
    }

    private suspend fun saveArticle(source: SourceEntity, link: ArticleLink, title: String, text: String): Boolean {
        val tokens = try {
            tokenizer.tokenize(text)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Tokenizing failed (${text.length} chars, ${link.url})", e)
            throw e
        }
        val matcher = Matcher(subjects, assignments)
        val matches = matcher.match(tokens)
        val tokenized = com.example.durtlesreborn.reading.core.TokenizedArticle(tokens, matches)
        val score = Scorer.score(tokenized)
        val ready = Scorer.readyLevel(tokenized)
        val articleId = store.insertArticle(
            ArticleEntity(
                sourceId = source.id,
                sourceName = source.name,
                url = link.url,
                title = title.ifBlank { link.title },
                text = text,
                encodedTokens = TokenCodec.encode(tokens),
                fetchedAt = System.currentTimeMillis(),
                knownPercent = score.knownPercent,
                uncoveredPercent = score.uncoveredPercent,
                knownKanjiPercent = score.knownKanjiPercent,
                readyLevel = ready,
                sizeBytes = (text.toByteArray().size + tokens.size * 24).toLong(),
            ),
        )
        val now = System.currentTimeMillis()
        val ranges = sentenceRanges(text)
        val examples = LinkedHashMap<String, ExampleEntity>()
        for (match in matches.filterNotNull()) {
            val sentence = ranges.firstOrNull { match.token.start in it }?.let { text.substring(it) }
                ?: continue
            val key = "${match.subject.id}:$sentence"
            if (examples.containsKey(key)) continue
            examples[key] = ExampleEntity(
                subjectId = match.subject.id,
                sentence = sentence,
                articleId = articleId,
                createdAt = now,
            )
            if (examples.size >= 300) break
        }
        if (examples.isNotEmpty()) store.insertExamples(examples.values.toList())
        return true
    }

    private suspend fun rescoreCached() {
        withContext(Dispatchers.Default) {
            val matcher = Matcher(subjects, assignments)
            for (article in store.articles()) {
                val tokens = TokenCodec.decode(article.encodedTokens)
                val scored = com.example.durtlesreborn.reading.core.TokenizedArticle(tokens, matcher.match(tokens))
                val score = Scorer.score(scored)
                val ready = Scorer.readyLevel(scored)
                store.updateScore(article.id, score.knownPercent, score.uncoveredPercent, score.knownKanjiPercent, ready)
            }
            _library.value = _library.value.copy(articles = store.articles().map(::toSummary))
        }
    }

    private fun toSource(source: SourceEntity) = ReadingSource(
        id = source.id,
        name = source.name,
        url = source.url,
        kind = runCatching { SourceKind.valueOf(source.kind) }.getOrDefault(SourceKind.RSS),
    )

    private fun toSummary(article: ArticleEntity) = ArticleSummary(
        id = article.id,
        title = article.title,
        sourceName = article.sourceName,
        url = article.url,
        knownPercent = article.knownPercent,
        uncoveredPercent = article.uncoveredPercent,
        knownKanjiPercent = article.knownKanjiPercent,
        readyLevel = article.readyLevel,
        fetchedAt = article.fetchedAt,
    )

    private fun sentenceRanges(text: String): List<IntRange> {
        val ranges = ArrayList<IntRange>()
        var start = 0
        for (index in text.indices) {
            val c = text[index]
            if (c == '。' || c == '！' || c == '？' || c == '\n') {
                if (index + 1 > start) ranges += start..index
                start = index + 1
            }
        }
        if (start < text.length) ranges += start until text.length
        return ranges
    }
}

/** Stage colour for a token, or null when it should not be highlighted. */
fun highlightColor(match: TokenMatch?, mode: HighlightMode, palette: ReadingPalette): Color? {
    if (match == null) return null
    val stage = match.srsStage
    val show = when (mode) {
        HighlightMode.OFF -> false
        HighlightMode.ALL -> true
        HighlightMode.KNOWN -> stage != null && stage >= 5
        HighlightMode.UNKNOWN -> stage == null || stage < 5
    }
    if (!show) return null
    val argb = when {
        stage == null -> palette.locked
        stage >= 9 -> palette.burned
        stage >= 8 -> palette.enlightened
        stage >= 7 -> palette.master
        stage >= 5 -> palette.guru
        else -> palette.apprentice
    }
    return Color(argb)
}

/** Readable name for an SRS stage, or "Locked" when the item has no assignment yet. */
fun stageLabel(stage: Int?): String = when {
    stage == null -> "Locked"
    stage >= 9 -> "Burned"
    stage == 8 -> "Enlightened"
    stage == 7 -> "Master"
    stage >= 5 -> "Guru ${stage - 4}"
    else -> "Apprentice $stage"
}

fun subjectTypeLabel(type: SubjectType): String = when (type) {
    SubjectType.RADICAL -> "Radical"
    SubjectType.KANJI -> "Kanji"
    SubjectType.VOCABULARY -> "Vocabulary"
    SubjectType.KANA_VOCABULARY -> "Kana vocabulary"
}

/** Default source list, exposed for the UI's "add source" helper. */
val defaultReadingSources: List<ReadingSource> get() = DefaultSources.all

private const val TAG = "GradedReading"

private fun String.isHttpUrl(): Boolean = startsWith("https://") || startsWith("http://")

/** A user-entered URL is a feed when it names one; anything else is treated as an index page. */
private fun String.looksLikeFeed(): Boolean =
    Regex("(?i)(rss|atom|\\.xml|/feed)").containsMatchIn(this)
