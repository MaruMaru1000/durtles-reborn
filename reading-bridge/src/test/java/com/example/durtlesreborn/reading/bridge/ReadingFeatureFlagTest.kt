package com.example.durtlesreborn.reading.bridge

import com.example.durtlesreborn.reading.core.ReadingPalette
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingFeatureFlagTest {
    private val palette = ReadingPalette(0, 0, 0, 0, 0, 0)

    private class Recorder : ReadingNavigator {
        var items = 0
        var closed = 0
        override fun openWaniKaniItem(subjectId: Long) { items++ }
        override fun closeReading() { closed++ }
    }

    @Test
    fun theDisabledFeatureIsInert() = runBlocking {
        val feature = ReadingFeature.disabled(palette)
        assertFalse(feature.enabled)
        assertNull(feature.route.value)

        feature.entryPoint.openLibrary(Recorder())
        feature.entryPoint.openArticle(1, Recorder())
        assertNull("no route should open", feature.route.value)
        assertTrue(feature.examples.examplesFor(1).isEmpty())
    }

    @Test
    fun theNoOpExamplesNeverReturnAnything() = runBlocking {
        assertTrue(NoOpReadingExamples.examplesFor(42).isEmpty())
    }

    @Test
    fun backStepsFromAnArticleToTheLibraryAndThenCloses() {
        val feature = ReadingFeature(
            enabled = true,
            controller = null,
            examples = NoOpReadingExamples,
            palette = palette,
        )
        val navigator = Recorder()
        feature.entryPoint.openLibrary(navigator)
        feature.openArticleFromLibrary(7)
        assertEquals(ReadingRoute.Article(7), feature.route.value)

        feature.back()
        assertEquals("an article goes back to the library", ReadingRoute.Library, feature.route.value)
        assertEquals(0, navigator.closed)

        feature.back()
        assertNull("the library closes the feature", feature.route.value)
        assertEquals(1, navigator.closed)
    }

    @Test
    fun backFromAnArticleOpenedByAnItemReturnsToTheItem() {
        val feature = ReadingFeature(
            enabled = true,
            controller = null,
            examples = NoOpReadingExamples,
            palette = palette,
        )
        val navigator = Recorder()
        feature.entryPoint.openArticle(9, navigator)
        assertEquals(ReadingRoute.Article(9), feature.route.value)

        feature.back()
        assertNull("no library was under it", feature.route.value)
        assertEquals(1, navigator.closed)
    }
}
