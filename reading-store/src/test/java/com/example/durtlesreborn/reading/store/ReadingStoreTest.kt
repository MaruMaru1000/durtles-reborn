package com.example.durtlesreborn.reading.store

import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReadingStoreTest {
    private lateinit var context: Context
    private lateinit var store: ReadingStore

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase(ReadingStore.DB_NAME)
        store = ReadingStore(context)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(ReadingStore.DB_NAME)
    }

    private fun article(sourceId: Long, url: String) = ArticleEntity(
        sourceId = sourceId,
        sourceName = "Test",
        url = url,
        title = "Article",
        text = "水を飲む。",
        encodedTokens = "",
        fetchedAt = System.currentTimeMillis(),
        knownPercent = 50,
        uncoveredPercent = 20,
        knownKanjiPercent = 100,
        readyLevel = 1,
        sizeBytes = 100,
    )

    @Test
    fun aMissingDatabaseIsCreatedOnFirstUse() = runBlocking {
        context.deleteDatabase(ReadingStore.DB_NAME)
        val fresh = ReadingStore(context)
        assertTrue(fresh.sources().isEmpty())
        fresh.addSource("Test", "https://example.com", "RSS")
        assertEquals(1, fresh.sources().size)
    }

    @Test
    fun aDamagedDatabaseIsRecreatedEmpty() = runBlocking {
        context.deleteDatabase(ReadingStore.DB_NAME)
        val path = context.getDatabasePath(ReadingStore.DB_NAME)
        path.parentFile?.mkdirs()
        path.writeText("this is not a sqlite database")
        val damaged = ReadingStore(context)
        // The first query fails, the store deletes the file and retries, and the app keeps working.
        assertTrue(damaged.sources().isEmpty())
        damaged.addSource("Test", "https://example.com", "RSS")
        assertEquals(1, damaged.sources().size)
    }

    @Test
    fun theSeedMarkerMeansRemovingEverySourceSticks() = runBlocking {
        assertFalse(store.seeded())
        val sourceId = store.addSource("Test", "https://example.com", "RSS")
        store.markSeeded()
        store.deleteSource(sourceId)
        assertTrue(store.sources().isEmpty())
        assertTrue(store.seeded())
    }

    @Test
    fun clearAllRemovesArticlesAndExamples() = runBlocking {
        val sourceId = store.addSource("Test", "https://example.com", "RSS")
        val articleId = store.insertArticle(article(sourceId, "https://example.com/a"))
        store.insertExamples(listOf(ExampleEntity(subjectId = 1, sentence = "水を飲む。", articleId = articleId, createdAt = 1)))
        assertTrue(store.examples(1, 3).isNotEmpty())
        store.clearAll()
        assertTrue(store.articles().isEmpty())
        assertTrue(store.examples(1, 3).isEmpty())
    }

    @Test
    fun examplesJoinTheArticleTitleAndSource() = runBlocking {
        val sourceId = store.addSource("NHK", "https://example.com", "RSS")
        val articleId = store.insertArticle(article(sourceId, "https://example.com/a"))
        store.insertExamples(listOf(ExampleEntity(subjectId = 7, sentence = "水を飲む。", articleId = articleId, createdAt = 1)))
        val rows = store.examples(7, 3)
        assertEquals(1, rows.size)
        assertEquals("Article", rows.first().articleTitle)
        assertEquals("Test", rows.first().sourceName)
    }

    @Test
    fun pruningKeepsTheCacheInsideItsBudget() = runBlocking {
        val sourceId = store.addSource("Test", "https://example.com", "RSS")
        for (i in 1..5) store.insertArticle(article(sourceId, "https://example.com/$i"))
        // The default budget is large, so a store with a tiny budget must prune the oldest.
        val tiny = ReadingStore(context, maxBytes = 250)
        tiny.prune()
        val count = store.articles().size
        assertTrue("articles left: $count", count <= 3)
    }
}
