package com.example.durtlesreborn.settings

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.durtlesreborn.answer.CloseEnoughAction
import com.example.durtlesreborn.session.SessionPriority
import com.example.durtlesreborn.session.SortKey
import com.example.durtlesreborn.session.SubjectOrder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsStoreTest {
    private fun dataStore() = InMemoryDataStore()

    @Test
    fun defaultsMatchTheOriginal() = runBlocking {
        val s = SettingsStore(dataStore()).current()
        assertEquals(CloseEnoughAction.SILENTLY_ACCEPT, s.closeEnoughAction)
        assertFalse(s.enableBackgroundSync)
        assertFalse(s.syncOnOpen)
        assertFalse(s.dryRun)
        assertFalse(s.requireOnYomiInKatakana)
        assertFalse(s.showAnswerOnWrong)
        assertTrue(s.quickAnswerOnWrong)
        assertFalse(s.textStageLabels)
        assertFalse(s.reviewBackToBack)
        assertFalse(s.reviewReadingFirst)
        assertFalse(s.reviewMeaningFirst)
        assertEquals(100, s.maxReviewSessionSize)
        assertEquals(SubjectOrder.SHUFFLE.keys, s.subjectOrder.keys)
        assertEquals(SessionPriority.None, s.priority)
    }

    @Test
    fun changesArePersisted() = runBlocking {
        val store = SettingsStore(dataStore())
        store.update { it.copy(closeEnoughAction = CloseEnoughAction.REJECT, maxReviewSessionSize = 25, reviewOrder = "LEVEL_THEN_TYPE", reviewOrderPriority = "RADICALS_FIRST", showAnswerOnWrong = true, textStageLabels = true, reviewBackToBack = true, reviewMeaningFirst = true) }
        val s = store.current()
        assertEquals(CloseEnoughAction.REJECT, s.closeEnoughAction)
        assertEquals(25, s.maxReviewSessionSize)
        assertTrue(s.showAnswerOnWrong)
        assertTrue(s.textStageLabels)
        assertTrue(s.reviewBackToBack)
        assertTrue(s.reviewMeaningFirst)
        assertEquals(listOf(SortKey.LEVEL, SortKey.TYPE), s.subjectOrder.keys)
        assertEquals(SessionPriority.RadicalsFirst, s.priority)
    }

    @Test
    fun sessionSizeIsKeptInRange() = runBlocking {
        val store = SettingsStore(dataStore())
        store.update { it.copy(maxReviewSessionSize = 0) }
        assertEquals(1, store.current().maxReviewSessionSize)
        store.update { it.copy(maxReviewSessionSize = 100_000) }
        assertEquals(500, store.current().maxReviewSessionSize)
    }

    @Test
    fun unknownStoredValuesFallBackToDefaults() = runBlocking {
        val ds = dataStore()
        ds.edit {
            it[stringPreferencesKey("close_enough_action")] = "NO_SUCH_ACTION"
            it[stringPreferencesKey("review_order")] = "BANANA"
            it[stringPreferencesKey("review_order_priority")] = "BANANA"
        }
        val s = SettingsStore(ds).current()
        assertEquals(CloseEnoughAction.SILENTLY_ACCEPT, s.closeEnoughAction)
        assertEquals(SubjectOrder.SHUFFLE.keys, s.subjectOrder.keys)
        assertEquals(SessionPriority.None, s.priority)
    }

    @Test
    fun themeAndNotificationSettingsArePersisted() = runBlocking {
        val store = SettingsStore(dataStore())
        val defaults = store.current()
        assertEquals("DYNAMIC", defaults.theme)
        assertTrue(defaults.enableNotifications)
        assertEquals("ONLY_NEW_REVIEWS", defaults.notificationFrequency)
        store.update { it.copy(theme = "MATCHA", enableNotifications = false, notificationPriority = "HIGH") }
        val s = store.current()
        assertEquals("MATCHA", s.theme)
        assertFalse(s.enableNotifications)
        assertEquals("HIGH", s.notificationPriority)
    }

    @Test
    fun orderNamesParseLikeTheOriginals() {
        assertEquals(listOf(SortKey.SRS, SortKey.TYPE, SortKey.LEVEL), SubjectOrder.fromName("SRS_THEN_TYPE_THEN_LEVEL")!!.keys)
        assertEquals(listOf(SortKey.TYPE), SubjectOrder.fromName("TYPE")!!.keys)
        assertTrue(SubjectOrder.fromName("SHUFFLE")!!.shuffle)
        assertNull(SubjectOrder.fromName("LEVEL_THEN_LEVEL"))
        assertNull(SubjectOrder.fromName("LEVEL_THEN_FOO"))
    }
}
