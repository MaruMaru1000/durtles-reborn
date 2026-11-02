package com.example.durtlesreborn.reading.bridge

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.durtlesreborn.reading.core.ReadingPalette
import com.example.durtlesreborn.reading.core.WaniKaniReadOnly
import com.example.durtlesreborn.reading.store.ReadingStore
import com.example.durtlesreborn.reading.ui.LibraryState
import com.example.durtlesreborn.reading.ui.ReaderData
import com.example.durtlesreborn.reading.ui.ReadingArticleScreen
import com.example.durtlesreborn.reading.ui.ReadingController
import com.example.durtlesreborn.reading.ui.ReadingLibraryScreen
import com.example.durtlesreborn.reading.ui.ReadingWebScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeUnit

/** One example sentence for a WaniKani item, as the item screen shows it. */
data class ReadingExample(
    val sentence: String,
    val articleTitle: String,
    val sourceName: String,
    val articleId: Long,
)

/** The item screen's only window into the reading feature. */
interface ReadingExamples {
    suspend fun examplesFor(subjectId: Int, limit: Int = 3): List<ReadingExample>
}

/** Used when the feature flag is off or the feature could not start. */
object NoOpReadingExamples : ReadingExamples {
    override suspend fun examplesFor(subjectId: Int, limit: Int): List<ReadingExample> = emptyList()
}

/** Navigation the reading screens need from the app. */
interface ReadingNavigator {
    fun openWaniKaniItem(subjectId: Long)
    fun closeReading()
}

/** Where the feature is: the library, an article, or nowhere. */
sealed interface ReadingRoute {
    data object Library : ReadingRoute
    data class Article(val id: Long) : ReadingRoute
}

/** The app's way into the feature. */
interface ReadingEntryPoint {
    fun openLibrary(navigator: ReadingNavigator)
    fun openArticle(articleId: Long, navigator: ReadingNavigator)
}

/**
 * The single object the app holds. It owns the reading controller (when enabled), exposes the
 * item-screen examples, and routes navigation. When disabled everything is inert: no UI, no work,
 * empty examples.
 */
class ReadingFeature internal constructor(
    val enabled: Boolean,
    internal val controller: ReadingController?,
    val examples: ReadingExamples,
    @Volatile var palette: ReadingPalette,
) {
    private val emptyLibrary = MutableStateFlow(LibraryState())
    private val _route = MutableStateFlow<ReadingRoute?>(null)
    private var navigator: ReadingNavigator? = null

    val route: StateFlow<ReadingRoute?> = _route.asStateFlow()
    internal val library: StateFlow<LibraryState> get() = controller?.library ?: emptyLibrary.asStateFlow()

    val entryPoint: ReadingEntryPoint = object : ReadingEntryPoint {
        override fun openLibrary(navigator: ReadingNavigator) {
            if (!enabled) return
            this@ReadingFeature.navigator = navigator
            controller?.refreshLibrary()
            _route.value = ReadingRoute.Library
        }

        override fun openArticle(articleId: Long, navigator: ReadingNavigator) {
            if (!enabled) return
            this@ReadingFeature.navigator = navigator
            _route.value = ReadingRoute.Article(articleId)
        }
    }

    fun updatePalette(palette: ReadingPalette) {
        this.palette = palette
        controller?.palette = palette
    }

    internal fun backToLibrary() {
        _route.value = ReadingRoute.Library
    }

    /** Opens an article from inside the library, reusing the navigator the app supplied. */
    internal fun openArticleFromLibrary(articleId: Long) {
        if (navigator == null) return
        _route.value = ReadingRoute.Article(articleId)
    }

    fun closeReading() {
        navigator?.closeReading()
        navigator = null
        _route.value = null
    }

    /**
     * Releases the controller's collectors. The app calls this when it leaves composition or the
     * setting is switched off, so a disabled feature really is inert.
     */
    fun close() {
        controller?.close()
        navigator = null
        _route.value = null
        if (ReadingRuntime.current === this) ReadingRuntime.current = null
    }

    internal fun openItem(subjectId: Long) {
        navigator?.openWaniKaniItem(subjectId)
    }

    internal suspend fun readArticle(id: Long): ReaderData? = controller?.openArticle(id)

    internal fun refresh() = controller?.refreshInBackground()

    internal fun addSource(name: String, url: String) = controller?.addSource(name, url) ?: Unit

    internal fun removeSource(id: Long) = controller?.removeSource(id) ?: Unit

    internal fun clearAll() = controller?.clearAll() ?: Unit

    /** Periodic refresh under its own unique work name, so nothing else is disturbed. */
    fun scheduleWork(context: Context) {
        if (!enabled) {
            cancelWork(context)
            return
        }
        val request = PeriodicWorkRequestBuilder<ReadingSyncWorker>(6, TimeUnit.HOURS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun cancelWork(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    companion object {
        const val WORK_NAME = "graded-reading-sync"

        internal fun disabled(palette: ReadingPalette): ReadingFeature =
            ReadingFeature(enabled = false, controller = null, examples = NoOpReadingExamples, palette = palette)
    }
}

/** The real item-screen examples, read from reading.db only. */
private class StoreReadingExamples(private val store: ReadingStore) : ReadingExamples {
    override suspend fun examplesFor(subjectId: Int, limit: Int): List<ReadingExample> = try {
        store.examples(subjectId, limit).map {
            ReadingExample(it.sentence, it.articleTitle, it.sourceName, it.articleId)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        emptyList()
    }
}

/** Builds the real feature when the flag is on, the inert one when it is off. */
object ReadingFeatureFactory {
    /** Background work never draws anything, so it does not need the theme's colours. */
    fun createForBackground(context: Context, wanikani: WaniKaniReadOnly, enabled: Boolean): ReadingFeature =
        create(context, wanikani, ReadingPalette(0, 0, 0, 0, 0, 0), enabled)

    fun create(
        context: Context,
        wanikani: WaniKaniReadOnly,
        palette: ReadingPalette,
        enabled: Boolean,
    ): ReadingFeature {
        if (!enabled) return ReadingFeature.disabled(palette)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        return runCatching {
            val app = context.applicationContext
            val store = ReadingStore(app)
            val controller = ReadingController(app, wanikani, store, scope, palette)
            controller.start()
            ReadingFeature(
                enabled = true,
                controller = controller,
                examples = StoreReadingExamples(store),
                palette = palette,
            ).also { ReadingRuntime.current = it }
        }.getOrElse {
            scope.cancel()
            ReadingFeature.disabled(palette)
        }
    }
}

/** Lets the worker reach the live feature without WorkManager reflection tricks. */
object ReadingRuntime {
    @Volatile
    var current: ReadingFeature? = null

    /**
     * Builds a feature for the worker when the UI has not created one (fresh process after boot,
     * or the activity was destroyed). The app installs this in `Application.onCreate`; without it
     * periodic work can only refresh while a screen is alive.
     */
    @Volatile
    var backgroundFactory: (suspend () -> ReadingFeature?)? = null
}

/** Refreshes the reading sources in the background. Never returns a failure: the feature is optional. */
class ReadingSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val existing = ReadingRuntime.current
        val feature = existing ?: try {
            ReadingRuntime.backgroundFactory?.invoke()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } ?: return Result.success()
        try {
            feature.controller?.refreshNow()
        } finally {
            // Only close a feature this worker created; a live UI still owns its own instance.
            if (feature !== existing) feature.close()
        }
        return Result.success()
    }
}

/**
 * Renders the reading feature's current screen. The app shows this when [ReadingFeature.route] is
 * non-null; it is the only composable from the reading modules that the app calls.
 */
@Composable
fun ReadingRouteHost(feature: ReadingFeature) {
    val route by feature.route.collectAsState()
    when (val current = route) {
        null -> Unit
        ReadingRoute.Library -> LibraryRoute(feature)
        is ReadingRoute.Article -> ArticleRoute(feature, current.id)
    }
}

@Composable
private fun LibraryRoute(feature: ReadingFeature) {
    val state by feature.library.collectAsState()
    ReadingLibraryScreen(
        state = state,
        onRefresh = feature::refresh,
        onOpenArticle = feature::openArticleFromLibrary,
        onClose = feature::closeReading,
        onAddSource = { name, url -> feature.addSource(name, url) },
        onRemoveSource = feature::removeSource,
        onClearAll = feature::clearAll,
    )
}

@Composable
private fun ArticleRoute(feature: ReadingFeature, id: Long) {
    var data by remember(id) { mutableStateOf<ReaderData?>(null) }
    var attempted by remember(id) { mutableStateOf(false) }
    var web by remember(id) { mutableStateOf(false) }
    LaunchedEffect(id) {
        data = feature.readArticle(id)
        attempted = true
    }

    val reader = data
    if (reader == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (!attempted) {
                CircularProgressIndicator()
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(24.dp),
                ) {
                    Text("This article is no longer cached.", style = MaterialTheme.typography.bodyLarge)
                    TextButton(onClick = feature::backToLibrary) { Text("Back to library") }
                }
            }
        }
        return
    }
    val controller = feature.controller
    if (web && controller != null) {
        ReadingWebScreen(
            url = reader.url,
            controller = controller,
            onBack = { web = false },
            onOpenItem = feature::openItem,
        )
    } else {
        ReadingArticleScreen(
            data = reader,
            palette = feature.palette,
            onBack = feature::backToLibrary,
            onOpenItem = feature::openItem,
            onOpenWeb = { web = true },
        )
    }
}
