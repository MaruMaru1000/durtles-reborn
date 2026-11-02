package com.example.durtlesreborn.ui

import com.example.durtlesreborn.reading.bridge.ReadingExample
import com.example.durtlesreborn.reading.bridge.ReadingExamples
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

/** The item screen hides "Seen in your reading" unless examples actually arrive. */
class ReadingExamplesSectionTest {
    private class Failing : ReadingExamples {
        override suspend fun examplesFor(subjectId: Int, limit: Int): List<ReadingExample> = error("boom")
    }

    private class Slow : ReadingExamples {
        override suspend fun examplesFor(subjectId: Int, limit: Int): List<ReadingExample> {
            delay(5_000)
            return listOf(ReadingExample("水を飲む。", "Article", "Source", 1))
        }
    }

    private class Empty : ReadingExamples {
        override suspend fun examplesFor(subjectId: Int, limit: Int): List<ReadingExample> = emptyList()
    }

    private class Working : ReadingExamples {
        override suspend fun examplesFor(subjectId: Int, limit: Int): List<ReadingExample> =
            listOf(ReadingExample("水を飲む。", "Article", "Source", 1))
    }

    @Test
    fun aFailingProviderHidesTheSection() = runBlocking {
        assertTrue(loadReadingExamples(Failing(), 1).isEmpty())
    }

    @Test
    fun aSlowProviderTimesOutAndHidesTheSection() = runTest {
        assertTrue(loadReadingExamples(Slow(), 1, timeoutMillis = 50).isEmpty())
    }

    @Test
    fun anEmptyProviderHidesTheSection() = runBlocking {
        assertTrue(loadReadingExamples(Empty(), 1).isEmpty())
    }

    @Test
    fun examplesThatArriveAreShown() = runBlocking {
        assertTrue(loadReadingExamples(Working(), 1).isNotEmpty())
    }
}
