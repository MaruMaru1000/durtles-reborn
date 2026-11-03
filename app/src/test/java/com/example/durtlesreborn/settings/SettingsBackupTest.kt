package com.example.durtlesreborn.settings

import com.example.durtlesreborn.answer.CloseEnoughAction
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SettingsBackupTest {
    private val custom = AppSettings(
        closeEnoughAction = CloseEnoughAction.REJECT,
        requireOnYomiInKatakana = true,
        showAnswerOnWrong = true,
        quickAnswerOnWrong = false,
        japaneseKeyboardOnReadings = true,
        enableBackgroundSync = true,
        syncOnOpen = true,
        maxReviewSessionSize = 40,
        reviewOrder = "LEVEL_THEN_TYPE",
        reviewOrderReversed = true,
        reviewOrderOverdueFirst = true,
        reviewOrderPriority = "RADICALS_FIRST",
        maxLessonSessionSize = 7,
        lessonOrder = "RADICALS_THEN_LEVEL_THEN_TYPE",
        maxSelfStudySessionSize = 55,
        enableNotifications = false,
        notificationFrequency = "ONCE_PER_HOUR",
        notificationPriority = "HIGH",
        notificationCategory = "REMINDER",
        theme = "FOREST",
        textStageLabels = true,
        reviewBackToBack = true,
        reviewReadingFirst = true,
        reviewMeaningFirst = true,
    )

    @Test
    fun everythingExportedComesBackUnchanged() {
        val result = SettingsBackup.import(SettingsBackup.export(custom), AppSettings())
        assertEquals(custom, result.settings)
        assertEquals(SettingsBackup.supportedCount, result.applied)
        assertEquals(0, result.unsupported)
        assertTrue(result.invalid.isEmpty())
        assertFalse(result.ignoredSecrets)
    }

    @Test
    fun theFormatIsTheOriginalsKeyToValueAndTypeMap() {
        val root = Json.parseToJsonElement(SettingsBackup.export(custom)).jsonObject
        val entry = root["enable_background_sync"]!!.jsonObject
        assertEquals("true", entry["value"]!!.jsonPrimitive.content)
        assertEquals("Boolean", entry["type"]!!.jsonPrimitive.content)
        // The original keeps session sizes in text preferences, so they must be typed String to load there.
        assertEquals("String", root["max_review_session_size"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("40", root["max_review_session_size"]!!.jsonObject["value"]!!.jsonPrimitive.content)
    }

    @Test
    fun theTokenIsNeverExported() {
        val text = SettingsBackup.export(custom)
        assertFalse(text.contains("api_key"))
        assertFalse(text.contains("token", ignoreCase = true))
        assertFalse(text.contains("web_password"))
    }

    @Test
    fun aTokenInAFileIsIgnoredAndReported() {
        val file = """
            {"api_key": {"value": "secret-token", "type": "String"},
             "web_password": {"value": "pw", "type": "String"},
             "sync_on_open": {"value": "true", "type": "Boolean"}}
        """.trimIndent()
        val r = SettingsBackup.import(file, AppSettings())
        assertTrue(r.ignoredSecrets)
        assertEquals(1, r.applied)
        assertEquals(0, r.unsupported)
        assertTrue(r.settings.syncOnOpen)
    }

    @Test
    fun anOriginalAppBackupIsReadAndTheRestCounted() {
        val file = """
            {"theme": {"value": "BLACK_WANIKANI", "type": "String"},
             "nightTheme": {"value": "DARK_WANIKANI", "type": "String"},
             "max_review_session_size": {"value": "250", "type": "String"},
             "close_enough_action": {"value": "SHAKE_AND_RETRY", "type": "String"},
             "anki_buttons_height": {"value": "50", "type": "Integer"},
             "swap_anki_buttons": {"value": "true", "type": "Boolean"},
             "api_key": {"value": "abc", "type": "String"}}
        """.trimIndent()
        val r = SettingsBackup.import(file, AppSettings())
        assertEquals("BLACK", r.settings.theme)
        assertEquals(250, r.settings.maxReviewSessionSize)
        assertEquals(CloseEnoughAction.SHAKE_AND_RETRY, r.settings.closeEnoughAction)
        // The original's separate night theme has no counterpart here: one theme applies in both modes.
        assertEquals(3, r.applied)
        assertEquals(3, r.unsupported)
        assertTrue(r.ignoredSecrets)
    }

    @Test
    fun invalidValuesKeepTheCurrentSettingAndAreListed() {
        val base = AppSettings(maxReviewSessionSize = 60, theme = "LIGHT")
        val file = """
            {"max_review_session_size": {"value": "lots", "type": "String"},
             "theme": {"value": "PURPLE", "type": "String"},
             "sync_on_open": {"value": "maybe", "type": "Boolean"},
             "review_order": {"value": "BANANA", "type": "String"},
             "close_enough_action": {"value": "NOPE", "type": "String"},
             "enable_notifications": {"value": "false", "type": "Boolean"}}
        """.trimIndent()
        val r = SettingsBackup.import(file, base)
        assertEquals(60, r.settings.maxReviewSessionSize)
        assertEquals("LIGHT", r.settings.theme)
        assertEquals(setOf("max_review_session_size", "theme", "sync_on_open", "review_order", "close_enough_action"), r.invalid.toSet())
        assertEquals(1, r.applied)
        assertFalse(r.settings.enableNotifications)
    }

    @Test
    fun sessionSizesAreKeptInRange() {
        val r = SettingsBackup.import("""{"max_lesson_session_size": {"value": "100000", "type": "String"}}""", AppSettings())
        assertEquals(AppSettings.MAX_SESSION_SIZE, r.settings.maxLessonSessionSize)
        val low = SettingsBackup.import("""{"max_lesson_session_size": {"value": "0", "type": "Integer"}}""", AppSettings())
        assertEquals(AppSettings.MIN_SESSION_SIZE, low.settings.maxLessonSessionSize)
    }

    @Test
    fun settingsMissingFromTheFileKeepTheirValues() {
        val r = SettingsBackup.import("""{"sync_on_open": {"value": "true", "type": "Boolean"}}""", custom)
        assertEquals(custom.copy(syncOnOpen = true), r.settings)
    }

    @Test
    fun aByteOrderMarkAtTheStartOfTheFileIsTolerated() {
        val file = "﻿" + """{"sync_on_open": {"value": "true", "type": "Boolean"}}"""
        assertTrue(SettingsBackup.import(file, AppSettings()).settings.syncOnOpen)
    }

    @Test
    fun filesThatAreNotBackupsAreRefused() {
        for (bad in listOf("not json at all", "[1,2,3]", "{}", "\"text\"")) {
            try {
                SettingsBackup.import(bad, AppSettings())
                fail("expected a refusal for: $bad")
            } catch (e: BackupFormatException) {
                assertTrue(e.message!!.isNotBlank())
            }
        }
    }
}
