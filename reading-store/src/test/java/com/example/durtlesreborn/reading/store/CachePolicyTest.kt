package com.example.durtlesreborn.reading.store

import org.junit.Assert.assertEquals
import org.junit.Test

class CachePolicyTest {
    private val day = 24 * 3_600_000L

    @Test
    fun articlesOlderThanTheWindowAreDroppedFirst() {
        val now = 100 * day
        val old = CachedArticle(1, now - 40 * day, 10)
        val fresh = CachedArticle(2, now - 5 * day, 10)
        val drop = CachePolicy.deletions(listOf(old, fresh), now, 30 * day, 1_000)
        assertEquals(listOf(1L), drop)
    }

    @Test
    fun theOldestAreDroppedUntilTheSizeBudgetFits() {
        val now = 100 * day
        val a = CachedArticle(1, now - 3 * day, 40)
        val b = CachedArticle(2, now - 2 * day, 40)
        val c = CachedArticle(3, now - day, 40)
        val drop = CachePolicy.deletions(listOf(a, b, c), now, 30 * day, 50)
        assertEquals(listOf(1L, 2L), drop)
    }

    @Test
    fun nothingIsDroppedWhenTheCacheIsSmallAndFresh() {
        val now = 100 * day
        val drop = CachePolicy.deletions(listOf(CachedArticle(1, now - day, 10)), now, 30 * day, 50)
        assertEquals(emptyList<Long>(), drop)
    }
}
