package com.example.durtlesreborn

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.example.durtlesreborn.api.WaniKaniClient
import com.example.durtlesreborn.audio.AudioCache
import com.example.durtlesreborn.audio.AudioDownloader
import com.example.durtlesreborn.audio.AudioPlayer
import com.example.durtlesreborn.data.AppDatabase
import com.example.durtlesreborn.data.DashboardRepository
import com.example.durtlesreborn.data.FileSessionSnapshotStore
import com.example.durtlesreborn.data.MIGRATION_1_2
import com.example.durtlesreborn.data.PitchRepository
import com.example.durtlesreborn.data.SessionSnapshotStore
import com.example.durtlesreborn.data.SearchRepository
import com.example.durtlesreborn.data.SessionRepository
import com.example.durtlesreborn.data.StatsRepository
import com.example.durtlesreborn.data.SyncEngine
import com.example.durtlesreborn.notify.AndroidNotificationSink
import com.example.durtlesreborn.notify.NotificationSettings
import com.example.durtlesreborn.notify.NotificationUpdater
import com.example.durtlesreborn.notify.WidgetUpdater
import com.example.durtlesreborn.settings.KeystoreTokenCipher
import com.example.durtlesreborn.strokes.StrokeRepository
import com.example.durtlesreborn.settings.SettingsStore
import com.example.durtlesreborn.settings.TokenStore
import com.example.durtlesreborn.update.AppUpdates
import com.example.durtlesreborn.update.AppUpdatesFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

private val Context.tokenDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
private val Context.appSettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

/** Hand-wired dependencies, created once per process. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    /** The session in progress, if the user left one unfinished. */
    val sessionStore: SessionSnapshotStore = FileSessionSnapshotStore(java.io.File(appContext.filesDir, "session.json"))

    val database: AppDatabase = Room.databaseBuilder(appContext, AppDatabase::class.java, "durtles.db")
        .addMigrations(MIGRATION_1_2)
        .build()

    val strokeRepository = StrokeRepository { appContext.assets.open("strokes.txt") }

    /** Pitch accent data from Kanjium, looked up by word and reading. */
    val pitchRepository = PitchRepository { appContext.assets.open("pitch_accents.txt") }

    val tokenStore = TokenStore(appContext.tokenDataStore, KeystoreTokenCipher())

    val settingsStore = SettingsStore(appContext.appSettingsDataStore)

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** Self-update from GitHub releases; null in store builds, which their store updates. */
    val appUpdates: AppUpdates? = AppUpdatesFactory.create(appContext, http)

    /** The pronunciation clip store, shared by playback and the bulk download. */
    val audioCache = AudioCache(http, java.io.File(appContext.filesDir, "audio"))

    val audioPlayer = AudioPlayer(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
        cache = audioCache,
    )

    /** Fetches the whole vocabulary audio library a few clips at a time. */
    val audioDownloader = AudioDownloader(audioCache)

    /** The token in memory, so the client can read it without suspending. Set after loading or saving it. */
    @Volatile
    var token: String = ""

    val client = WaniKaniClient(http, token = { token })

    /** A client for a token that is not saved yet, to check it before accepting it. */
    fun clientFor(candidate: String) = WaniKaniClient(http, token = { candidate })

    val syncEngine = SyncEngine(
        client = client,
        pending = database.pendingReviews(),
        subjects = database.subjects(),
        assignments = database.assignments(),
        statistics = database.reviewStatistics(),
        materials = database.studyMaterials(),
        srsSystems = database.srsSystems(),
        state = database.syncState(),
    )

    val searchRepository = SearchRepository(
        search = database.search(),
        subjects = database.subjects(),
        assignments = database.assignments(),
        statistics = database.reviewStatistics(),
        state = database.syncState(),
        pitch = pitchRepository,
    )

    val statsRepository = StatsRepository(stats = database.stats(), alerts = database.alerts(), state = database.syncState())

    val dashboardRepository = DashboardRepository(
        stats = database.stats(),
        levels = database.levels(),
        alerts = database.alerts(),
        state = database.syncState(),
    )

    val notifier = NotificationUpdater(
        alerts = database.alerts(),
        state = database.syncState(),
        settings = {
            settingsStore.current().let {
                NotificationSettings(it.enableNotifications, it.updateFrequency, it.notificationPriority, it.notificationCategory)
            }
        },
        sink = AndroidNotificationSink(appContext),
    )

    /** Brings the notification and any home screen widget in line with what is due now. */
    suspend fun refreshAlerts() {
        runCatching { notifier.update() }
        runCatching { WidgetUpdater.update(appContext) }
    }

    val repository = SessionRepository(
        subjects = database.subjects(),
        assignments = database.assignments(),
        materials = database.studyMaterials(),
        srsSystems = database.srsSystems(),
        state = database.syncState(),
        pitch = pitchRepository,
    )
}
