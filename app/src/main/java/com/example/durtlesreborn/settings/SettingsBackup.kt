package com.example.durtlesreborn.settings

import com.example.durtlesreborn.answer.CloseEnoughAction
import com.example.durtlesreborn.notify.NotificationFrequency
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.SerializationException

class BackupFormatException(message: String) : Exception(message)

/**
 * Saves and restores the app's settings as one JSON file in the format of the original app's
 * backup: a map from setting key to `{"value": "...", "type": "Boolean|String|Integer|..."}`, using
 * the original's setting names. That makes a backup from the original loadable here, and the other
 * way round for the settings both apps have.
 *
 * The API token is never written, and a token found in a file is ignored: the token is only ever
 * entered by the user and checked against the server.
 */
object SettingsBackup {
    private val json = Json { prettyPrint = true }

    /** Keys that hold secrets in the original; never exported and never imported. */
    private val secretKeys = setOf("api_key", "web_password")

    /** One setting: how it is written, and how a value read from a file is applied (null if the value is invalid). */
    private class Spec(
        val key: String,
        /** The type the original app stores, so a file written here loads there. */
        val type: String,
        val read: (AppSettings) -> String,
        val write: (AppSettings, String) -> AppSettings?,
    )

    private fun bool(s: String): Boolean? = when (s.lowercase()) {
        "true" -> true
        "false" -> false
        else -> null
    }

    // The original keeps these sizes in text preferences, so they are exported as String.
    private fun size(s: String): Int? = s.trim().toIntOrNull()?.coerceIn(AppSettings.MIN_SESSION_SIZE, AppSettings.MAX_SESSION_SIZE)

    /** The original has six theme styles; two are WaniKani-coloured and map to the nearest of ours. */
    private fun themeName(s: String): String? = when (s) {
        "LIGHT" -> "LIGHT"
        "DARK", "DARK_WANIKANI" -> "DARK"
        "BLACK", "BLACK_WANIKANI", "BLACK_BREEZE" -> "BLACK"
        "NORD_POLAR_NIGHT", "MATCHA", "SANDSTONE", "FOREST" -> s
        "DYNAMIC" -> "DYNAMIC"
        "CRAB_CROC" -> "CRAB_CROC"
        "SAKURA" -> "SAKURA"
        else -> null
    }

    private val specs = listOf(
        Spec("close_enough_action", "String", { it.closeEnoughAction.name },
            { s, v -> CloseEnoughAction.entries.firstOrNull { it.name == v }?.let { s.copy(closeEnoughAction = it) } }),
        Spec("require_onyomi_in_katakana", "Boolean", { it.requireOnYomiInKatakana.toString() },
            { s, v -> bool(v)?.let { s.copy(requireOnYomiInKatakana = it) } }),
        Spec("show_answer_on_wrong", "Boolean", { it.showAnswerOnWrong.toString() },
            { s, v -> bool(v)?.let { s.copy(showAnswerOnWrong = it) } }),
        Spec("quick_answer_on_wrong", "Boolean", { it.quickAnswerOnWrong.toString() },
            { s, v -> bool(v)?.let { s.copy(quickAnswerOnWrong = it) } }),
        Spec("keep_keyboard_open", "Boolean", { it.keepKeyboardOpen.toString() },
            { s, v -> bool(v)?.let { s.copy(keepKeyboardOpen = it) } }),
        Spec("enable_background_sync", "Boolean", { it.enableBackgroundSync.toString() },
            { s, v -> bool(v)?.let { s.copy(enableBackgroundSync = it) } }),
        Spec("sync_on_open", "Boolean", { it.syncOnOpen.toString() },
            { s, v -> bool(v)?.let { s.copy(syncOnOpen = it) } }),
        Spec("delay_result_upload", "Boolean", { it.delayResultUpload.toString() },
            { s, v -> bool(v)?.let { s.copy(delayResultUpload = it) } }),
        Spec("max_review_session_size", "String", { it.maxReviewSessionSize.toString() },
            { s, v -> size(v)?.let { s.copy(maxReviewSessionSize = it) } }),
        Spec("review_order", "String", { it.reviewOrder },
            { s, v -> if (com.example.durtlesreborn.session.SubjectOrder.fromName(v) != null) s.copy(reviewOrder = v) else null }),
        Spec("review_order_reversed", "Boolean", { it.reviewOrderReversed.toString() },
            { s, v -> bool(v)?.let { s.copy(reviewOrderReversed = it) } }),
        Spec("review_order_overdue_first", "Boolean", { it.reviewOrderOverdueFirst.toString() },
            { s, v -> bool(v)?.let { s.copy(reviewOrderOverdueFirst = it) } }),
        Spec("review_order_priority", "String", { it.reviewOrderPriority },
            { s, v -> v.takeIf { it.isNotBlank() }?.let { s.copy(reviewOrderPriority = it) } }),
        Spec("review_back_to_back", "Boolean", { it.reviewBackToBack.toString() },
            { s, v -> bool(v)?.let { s.copy(reviewBackToBack = it) } }),
        Spec("review_reading_first", "Boolean", { it.reviewReadingFirst.toString() },
            { s, v -> bool(v)?.let { s.copy(reviewReadingFirst = it) } }),
        Spec("review_meaning_first", "Boolean", { it.reviewMeaningFirst.toString() },
            { s, v -> bool(v)?.let { s.copy(reviewMeaningFirst = it) } }),
        Spec("max_lesson_session_size", "String", { it.maxLessonSessionSize.toString() },
            { s, v -> size(v)?.let { s.copy(maxLessonSessionSize = it) } }),
        Spec("lesson_order", "String", { it.lessonOrder },
            { s, v -> if (com.example.durtlesreborn.session.SubjectOrder.fromName(v) != null) s.copy(lessonOrder = v) else null }),
        Spec("max_self_study_session_size", "String", { it.maxSelfStudySessionSize.toString() },
            { s, v -> size(v)?.let { s.copy(maxSelfStudySessionSize = it) } }),
        Spec("enable_notifications", "Boolean", { it.enableNotifications.toString() },
            { s, v -> bool(v)?.let { s.copy(enableNotifications = it) } }),
        Spec("notification_update_frequency", "String", { it.notificationFrequency },
            { s, v -> NotificationFrequency.entries.firstOrNull { it.name == v }?.let { s.copy(notificationFrequency = v) } }),
        Spec("notification_priority", "String", { it.notificationPriority },
            { s, v -> v.takeIf { it.isNotBlank() }?.let { s.copy(notificationPriority = it) } }),
        Spec("notification_category", "String", { it.notificationCategory },
            { s, v -> v.takeIf { it.isNotBlank() }?.let { s.copy(notificationCategory = it) } }),
        Spec("theme", "String", { it.theme }, { s, v -> themeName(v)?.let { s.copy(theme = it) } }),
        Spec("text_stage_labels", "Boolean", { it.textStageLabels.toString() },
            { s, v -> bool(v)?.let { s.copy(textStageLabels = it) } }),
        Spec("graded_reading", "Boolean", { it.gradedReading.toString() },
            { s, v -> bool(v)?.let { s.copy(gradedReading = it) } }),
    )

    /** How many settings a backup written now contains. */
    val supportedCount get() = specs.size

    fun export(settings: AppSettings): String {
        val entries = specs.associate { spec ->
            spec.key to JsonObject(mapOf("value" to JsonPrimitive(spec.read(settings)), "type" to JsonPrimitive(spec.type)))
        }
        return json.encodeToString(JsonObject.serializer(), JsonObject(entries))
    }

    class ImportResult(
        val settings: AppSettings,
        /** Settings that were read and applied. */
        val applied: Int,
        /** Keys this app has no setting for (the original has many more than are built here). */
        val unsupported: Int,
        /** Known settings whose value was not usable, by key. They keep their current value. */
        val invalid: List<String>,
        /** The file contained a token or password, which was deliberately not imported. */
        val ignoredSecrets: Boolean,
    )

    /**
     * Applies a backup on top of [base]. Anything missing, unknown or invalid leaves the current
     * value alone. Throws [BackupFormatException] if the file is not a settings backup at all.
     */
    fun import(text: String, base: AppSettings): ImportResult {
        val root = try {
            // Editors on Windows often add a byte order mark, which JSON parsers reject.
            Json.parseToJsonElement(text.removePrefix("﻿")).jsonObject
        } catch (e: SerializationException) {
            throw BackupFormatException("This file is not a settings backup.")
        } catch (e: IllegalArgumentException) {
            throw BackupFormatException("This file is not a settings backup.")
        }
        var settings = base
        var applied = 0
        var unsupported = 0
        var secrets = false
        val invalid = ArrayList<String>()
        val byKey = specs.associateBy { it.key }
        for ((key, entry) in root) {
            if (key in secretKeys) {
                secrets = true
                continue
            }
            val spec = byKey[key]
            if (spec == null) {
                unsupported++
                continue
            }
            val value = (entry as? JsonObject)?.get("value")?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }
            val next = value?.let { spec.write(settings, it) }
            if (next == null) {
                invalid += key
            } else {
                settings = next
                applied++
            }
        }
        if (applied == 0 && invalid.isEmpty() && unsupported == 0 && !secrets) {
            throw BackupFormatException("This file does not contain any settings.")
        }
        return ImportResult(settings, applied, unsupported, invalid, secrets)
    }
}
