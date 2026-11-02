package com.example.durtlesreborn.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.durtlesreborn.answer.CloseEnoughAction
import com.example.durtlesreborn.notify.NotificationFrequency
import com.example.durtlesreborn.session.SessionPriority
import com.example.durtlesreborn.session.SubjectOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * The settings wired up so far. Keys and defaults are those of the original app, so a later
 * import of its settings can map one to one. The rest of the original's 178 settings are listed in
 * docs/parity-settings.md.
 */
data class AppSettings(
    val closeEnoughAction: CloseEnoughAction = CloseEnoughAction.SILENTLY_ACCEPT,
    val requireOnYomiInKatakana: Boolean = false,
    /**
     * After a wrong answer, reveal the accepted answer. Off by default: the answer stays hidden and
     * the details sheet (swipe up on the question) is how it is looked at.
     */
    val showAnswerOnWrong: Boolean = false,
    /**
     * Keep the answer field focused and the keyboard up while the answer is shown, so the next
     * answer needs no extra tap. Off collapses the keyboard after every answer.
     */
    val keepKeyboardOpen: Boolean = true,
    val enableBackgroundSync: Boolean = false,
    val syncOnOpen: Boolean = false,
    /** Hold results until the session is finished instead of sending each item as it is done. */
    val delayResultUpload: Boolean = true,
    /** Developer option: sessions run as normal but nothing is saved on the phone or sent to WaniKani. */
    val dryRun: Boolean = false,
    val maxReviewSessionSize: Int = 100,
    /** The original's name, e.g. "SHUFFLE" or "LEVEL_THEN_TYPE". */
    val reviewOrder: String = "SHUFFLE",
    val reviewOrderReversed: Boolean = false,
    val reviewOrderOverdueFirst: Boolean = false,
    val reviewOrderPriority: String = "NONE",
    /** Ask both questions for one item before moving on. */
    val reviewBackToBack: Boolean = false,
    /** Ask reading questions before meaning questions while both are pending. */
    val reviewReadingFirst: Boolean = false,
    /** Ask meaning questions before reading questions while both are pending. */
    val reviewMeaningFirst: Boolean = false,
    val maxLessonSessionSize: Int = 5,
    val maxSelfStudySessionSize: Int = 100,
    /** The original's lesson order name, e.g. "LEVEL_THEN_TYPE" or "RADICALS_THEN_LEVEL_THEN_TYPE". */
    val lessonOrder: String = "LEVEL_THEN_TYPE",
    val enableNotifications: Boolean = true,
    val notificationFrequency: String = "ONLY_NEW_REVIEWS",
    val notificationPriority: String = "DEFAULT",
    val notificationCategory: String = "RECOMMENDATION",
    /** The colour theme: an [com.example.durtlesreborn.ui.AppTheme] name. DYNAMIC follows the wallpaper and the system mode. */
    val theme: String = "DYNAMIC",
    /** Under the dashboard tiles, show stage names (A1…Burned) instead of the five-segment bars. */
    val textStageLabels: Boolean = false,
    /** Feature flag for Graded Reading. Off means no entry, no background work and no examples. */
    val gradedReading: Boolean = false,
) {
    /** The configured order, or shuffle if the stored name is not one the app knows. */
    val subjectOrder: SubjectOrder get() = SubjectOrder.fromName(reviewOrder) ?: SubjectOrder.SHUFFLE
    val priority: SessionPriority get() = SessionPriority.fromName(reviewOrderPriority)
    val lessonSubjectOrder: SubjectOrder get() = SubjectOrder.fromName(lessonOrder) ?: SubjectOrder.LEVEL_THEN_TYPE
    val updateFrequency: NotificationFrequency
        get() = NotificationFrequency.entries.firstOrNull { it.name == notificationFrequency } ?: NotificationFrequency.ONLY_NEW_REVIEWS

    companion object {
        const val MIN_SESSION_SIZE = 1
        const val MAX_SESSION_SIZE = 500
    }
}

class SettingsStore(private val dataStore: DataStore<Preferences>) {
    private object Keys {
        val closeEnough = stringPreferencesKey("close_enough_action")
        val onYomiKatakana = booleanPreferencesKey("require_onyomi_in_katakana")
        val showAnswerOnWrong = booleanPreferencesKey("show_answer_on_wrong")
        val keepKeyboardOpen = booleanPreferencesKey("keep_keyboard_open")
        val backgroundSync = booleanPreferencesKey("enable_background_sync")
        val syncOnOpen = booleanPreferencesKey("sync_on_open")
        val delayResultUpload = booleanPreferencesKey("delay_result_upload")
        val dryRun = booleanPreferencesKey("dev_dry_run")
        val maxReviews = intPreferencesKey("max_review_session_size")
        val order = stringPreferencesKey("review_order")
        val orderReversed = booleanPreferencesKey("review_order_reversed")
        val overdueFirst = booleanPreferencesKey("review_order_overdue_first")
        val priority = stringPreferencesKey("review_order_priority")
        val backToBack = booleanPreferencesKey("review_back_to_back")
        val readingFirst = booleanPreferencesKey("review_reading_first")
        val meaningFirst = booleanPreferencesKey("review_meaning_first")
        val maxLessons = intPreferencesKey("max_lesson_session_size")
        val maxSelfStudy = intPreferencesKey("max_self_study_session_size")
        val lessonOrder = stringPreferencesKey("lesson_order")
        val notifications = booleanPreferencesKey("enable_notifications")
        val notificationFrequency = stringPreferencesKey("notification_update_frequency")
        val notificationPriority = stringPreferencesKey("notification_priority")
        val notificationCategory = stringPreferencesKey("notification_category")
        val theme = stringPreferencesKey("theme")
        val textStageLabels = booleanPreferencesKey("text_stage_labels")
        val gradedReading = booleanPreferencesKey("graded_reading")
    }

    val settings: Flow<AppSettings> = dataStore.data.map { p ->
        val d = AppSettings()
        AppSettings(
            closeEnoughAction = p[Keys.closeEnough]?.let { name -> CloseEnoughAction.entries.firstOrNull { it.name == name } }
                ?: d.closeEnoughAction,
            requireOnYomiInKatakana = p[Keys.onYomiKatakana] ?: d.requireOnYomiInKatakana,
            showAnswerOnWrong = p[Keys.showAnswerOnWrong] ?: d.showAnswerOnWrong,
            keepKeyboardOpen = p[Keys.keepKeyboardOpen] ?: d.keepKeyboardOpen,
            enableBackgroundSync = p[Keys.backgroundSync] ?: d.enableBackgroundSync,
            syncOnOpen = p[Keys.syncOnOpen] ?: d.syncOnOpen,
            delayResultUpload = p[Keys.delayResultUpload] ?: d.delayResultUpload,
            dryRun = p[Keys.dryRun] ?: d.dryRun,
            maxReviewSessionSize = (p[Keys.maxReviews] ?: d.maxReviewSessionSize)
                .coerceIn(AppSettings.MIN_SESSION_SIZE, AppSettings.MAX_SESSION_SIZE),
            reviewOrder = p[Keys.order] ?: d.reviewOrder,
            reviewOrderReversed = p[Keys.orderReversed] ?: d.reviewOrderReversed,
            reviewOrderOverdueFirst = p[Keys.overdueFirst] ?: d.reviewOrderOverdueFirst,
            reviewOrderPriority = p[Keys.priority] ?: d.reviewOrderPriority,
            reviewBackToBack = p[Keys.backToBack] ?: d.reviewBackToBack,
            reviewReadingFirst = p[Keys.readingFirst] ?: d.reviewReadingFirst,
            reviewMeaningFirst = p[Keys.meaningFirst] ?: d.reviewMeaningFirst,
            maxLessonSessionSize = (p[Keys.maxLessons] ?: d.maxLessonSessionSize)
                .coerceIn(AppSettings.MIN_SESSION_SIZE, AppSettings.MAX_SESSION_SIZE),
            maxSelfStudySessionSize = (p[Keys.maxSelfStudy] ?: d.maxSelfStudySessionSize)
                .coerceIn(AppSettings.MIN_SESSION_SIZE, AppSettings.MAX_SESSION_SIZE),
            lessonOrder = p[Keys.lessonOrder] ?: d.lessonOrder,
            enableNotifications = p[Keys.notifications] ?: d.enableNotifications,
            notificationFrequency = p[Keys.notificationFrequency] ?: d.notificationFrequency,
            notificationPriority = p[Keys.notificationPriority] ?: d.notificationPriority,
            notificationCategory = p[Keys.notificationCategory] ?: d.notificationCategory,
            theme = p[Keys.theme] ?: d.theme,
            textStageLabels = p[Keys.textStageLabels] ?: d.textStageLabels,
            gradedReading = p[Keys.gradedReading] ?: d.gradedReading,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun update(change: (AppSettings) -> AppSettings) {
        val next = change(current())
        dataStore.edit { p ->
            p[Keys.closeEnough] = next.closeEnoughAction.name
            p[Keys.onYomiKatakana] = next.requireOnYomiInKatakana
            p[Keys.showAnswerOnWrong] = next.showAnswerOnWrong
            p[Keys.keepKeyboardOpen] = next.keepKeyboardOpen
            p[Keys.backgroundSync] = next.enableBackgroundSync
            p[Keys.syncOnOpen] = next.syncOnOpen
            p[Keys.delayResultUpload] = next.delayResultUpload
            p[Keys.dryRun] = next.dryRun
            p[Keys.maxReviews] = next.maxReviewSessionSize
            p[Keys.order] = next.reviewOrder
            p[Keys.orderReversed] = next.reviewOrderReversed
            p[Keys.overdueFirst] = next.reviewOrderOverdueFirst
            p[Keys.priority] = next.reviewOrderPriority
            p[Keys.backToBack] = next.reviewBackToBack
            p[Keys.readingFirst] = next.reviewReadingFirst
            p[Keys.meaningFirst] = next.reviewMeaningFirst
            p[Keys.maxLessons] = next.maxLessonSessionSize
            p[Keys.maxSelfStudy] = next.maxSelfStudySessionSize
            p[Keys.lessonOrder] = next.lessonOrder
            p[Keys.notifications] = next.enableNotifications
            p[Keys.notificationFrequency] = next.notificationFrequency
            p[Keys.notificationPriority] = next.notificationPriority
            p[Keys.notificationCategory] = next.notificationCategory
            p[Keys.theme] = next.theme
            p[Keys.textStageLabels] = next.textStageLabels
            p[Keys.gradedReading] = next.gradedReading
        }
    }
}
