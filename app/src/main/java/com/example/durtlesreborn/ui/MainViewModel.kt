package com.example.durtlesreborn.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.durtlesreborn.AppContainer
import com.example.durtlesreborn.api.ApiResult
import com.example.durtlesreborn.data.Dashboard
import com.example.durtlesreborn.data.LevelContent
import com.example.durtlesreborn.data.LessonInfo
import com.example.durtlesreborn.data.ItemDetail
import com.example.durtlesreborn.data.SearchCriteria
import com.example.durtlesreborn.data.StageGroup
import com.example.durtlesreborn.session.SubjectKind
import com.example.durtlesreborn.data.SearchResult
import com.example.durtlesreborn.data.Stats
import com.example.durtlesreborn.data.SessionRecorder
import com.example.durtlesreborn.data.SyncStep
import com.example.durtlesreborn.kana.Kana
import com.example.durtlesreborn.session.PriorityContext
import com.example.durtlesreborn.settings.AppSettings
import com.example.durtlesreborn.settings.BackupFormatException
import com.example.durtlesreborn.settings.SettingsBackup
import com.example.durtlesreborn.session.QuizCard
import com.example.durtlesreborn.session.QuizController
import com.example.durtlesreborn.session.QuizFeedback
import com.example.durtlesreborn.session.QuizProgress
import com.example.durtlesreborn.session.SessionConfig
import com.example.durtlesreborn.session.SessionEngine
import com.example.durtlesreborn.session.SessionPlanner
import com.example.durtlesreborn.session.SessionSnapshot
import com.example.durtlesreborn.session.SrsSystem
import com.example.durtlesreborn.session.SessionSubject
import com.example.durtlesreborn.session.SessionType
import com.example.durtlesreborn.session.StageMove
import com.example.durtlesreborn.session.SubjectOrder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** What the home screen shows about a session that can be resumed. */
data class SavedSession(val type: SessionType, val doneItems: Int, val totalItems: Int)

private fun SessionType.label() = when (this) {
    SessionType.REVIEW -> "reviews"
    SessionType.LESSON -> "lessons"
    SessionType.SELF_STUDY -> "self-study"
}

sealed interface UiState {
    data object Loading : UiState

    data class NeedsToken(val error: String? = null, val checking: Boolean = false) : UiState

    data class Home(
        val dashboard: Dashboard? = null,
        /** A review or lesson session that was left unfinished. */
        val saved: SavedSession? = null,
        /** Developer dry run is on: sessions are not saved or sent. */
        val dryRun: Boolean = false,
        /** While syncing: how far the download has got. */
        val progress: String? = null,
        val syncing: Boolean = false,
        val message: String? = null,
        /** Show stage names under the tiles instead of the five-segment bars. */
        val textStageLabels: Boolean = false,
    ) : UiState

    /** The level browser. [contents] fills in as levels are viewed. */
    data class Levels(
        val selectedLevel: Int,
        val maxLevel: Int,
        val contents: Map<Int, LevelContent> = emptyMap(),
    ) : UiState

    data class Quiz(
        val card: QuizCard,
        val input: String = "",
        /** Set after an answer was checked; [QuizFeedback.retry] means it was not recorded. */
        val feedback: QuizFeedback? = null,
        val answered: Boolean = false,
        val progress: QuizProgress,
        val canUndo: Boolean = false,
        /** Reveal the accepted answer on a wrong answer, from the `show_answer_on_wrong` setting. */
        val showAnswerOnWrong: Boolean = false,
        /** Reveal the accepted answer above the item by itself on a wrong answer, from `quick_answer_on_wrong`. */
        val quickAnswerOnWrong: Boolean = true,
        /** Ask the keyboard for Japanese on reading questions, from `japanese_keyboard_on_readings`. */
        val japaneseKeyboardOnReadings: Boolean = false,
        /** Keep the field focused and the keyboard up while the answer is shown, from `keep_keyboard_open`. */
        val keepKeyboardOpen: Boolean = true,
        /** Set when this answer completed a review item: the stage it moved from and to. */
        val stageMove: StageMove? = null,
    ) : UiState

    data class Lesson(
        val info: LessonInfo,
        val index: Int,
        val total: Int,
        val isFirst: Boolean,
        val isLast: Boolean,
    ) : UiState

    data class Summary(val totalItems: Int, val perfectItems: Int, val type: SessionType = SessionType.REVIEW, val dryRun: Boolean = false) : UiState

    data class Settings(val settings: AppSettings, val message: String? = null) : UiState

    data class Search(
        val criteria: SearchCriteria = SearchCriteria(),
        val results: List<SearchResult> = emptyList(),
        val searching: Boolean = false,
    ) : UiState

    data class Detail(val detail: ItemDetail) : UiState

    /** Every item in one SRS group; [items] is null while they load. */
    data class StageItems(
        val group: StageGroup,
        val items: List<SearchResult>? = null,
        val kind: SubjectKind? = null,
    ) : UiState

    data class StatsView(val stats: Stats?) : UiState

    /** The audio manager: the space each level takes, newest first. */
    data class AudioLevels(val levels: List<LevelAudio>, val currentLevel: Int) : UiState
}

/** One level's audio, as the manager screen shows it. */
data class LevelAudio(val level: Int, val clips: Int, val bytes: Long)

/** Progress and storage of the automatic pronunciation download, shown in settings and the manager. */
data class AudioDownload(
    val running: Boolean = false,
    /** The level being downloaded, or 0 when idle. */
    val level: Int = 0,
    val done: Int = 0,
    val total: Int = 0,
    val failed: Int = 0,
    val storedCount: Int = 0,
    val storedBytes: Long = 0,
)

class MainViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var quiz: QuizController? = null
    private var recorder: SessionRecorder? = null
    private var sessionType = SessionType.REVIEW
    private var sessionDryRun = false
    private var sessionShowAnswerOnWrong = false
    private var sessionQuickAnswerOnWrong = true
    private var sessionJapaneseKeyboardOnReadings = false
    private var sessionKeepKeyboardOpen = true
    private var audioJob: Job? = null

    private val _audioDownload = MutableStateFlow(AudioDownload())
    val audioDownload: StateFlow<AudioDownload> = _audioDownload.asStateFlow()

    /** Settings as a flow, so screens can react to the graded reading flag. */
    val settings: StateFlow<AppSettings> = container.settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    /** A write of the saved session, or null to remove it. Requests run in the order they were made. */
    private class SaveRequest(val snapshot: SessionSnapshot?, val done: CompletableDeferred<Unit> = CompletableDeferred())

    private val saveRequests = Channel<SaveRequest>(Channel.UNLIMITED)

    init {
        viewModelScope.launch {
            for (request in saveRequests) {
                withContext(NonCancellable + Dispatchers.IO) {
                    runCatching {
                        if (request.snapshot == null) container.sessionStore.clear() else container.sessionStore.save(request.snapshot)
                    }
                }
                request.done.complete(Unit)
            }
        }
    }

    /** Saves the session in progress so it survives leaving the app. Self-study is never saved. */
    private fun persist(): SaveRequest? {
        val controller = quiz ?: return null
        if (sessionType == SessionType.SELF_STUDY || sessionDryRun) return null
        return SaveRequest(controller.snapshot(System.currentTimeMillis())).also { saveRequests.trySend(it) }
    }

    /** Removes the saved session and waits until that is done. */
    private suspend fun clearSaved() {
        val request = SaveRequest(null)
        saveRequests.trySend(request)
        request.done.await()
    }

    private suspend fun savedSnapshot(): SessionSnapshot? = withContext(Dispatchers.IO) { container.sessionStore.load() }

    init {
        viewModelScope.launch {
            // Clips from the old flat cache layout cannot be tied to a level; the per-level download refetches them.
            withContext(Dispatchers.IO) { container.audioCache.clearLegacyLayout() }
        }
    }

    init {
        viewModelScope.launch {
            val token = container.tokenStore.current()
            if (token.isNullOrBlank()) {
                _state.value = UiState.NeedsToken()
            } else {
                container.token = token
                _state.value = UiState.Home()
                container.syncEngine.onProgress = ::onSyncProgress
                refreshHomeSafely()
                // Sync on open if asked to, and always the first time, when there is no data yet.
                val firstRun = container.database.syncState().get(com.example.durtlesreborn.data.SyncEngine.KEY_LEVEL) == null
                if (container.settingsStore.current().syncOnOpen || firstRun) sync()
            }
        }
    }

    private var searchState = UiState.Search()
    private var searchJob: Job? = null

    fun openSearch() {
        _state.value = searchState
        runSearch(searchState.criteria, immediately = true)
    }

    fun closeSearch() {
        searchJob?.cancel()
        viewModelScope.launch { refreshHome() }
    }

    fun onSearchCriteria(criteria: SearchCriteria) {
        searchState = searchState.copy(criteria = criteria)
        _state.value = searchState.copy(searching = true)
        runSearch(criteria, immediately = false)
    }

    /** Searches after a short pause in typing, so each key press does not start a query. */
    private fun runSearch(criteria: SearchCriteria, immediately: Boolean) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (!immediately) delay(SEARCH_DEBOUNCE_MILLIS)
            val results = container.searchRepository.find(criteria, SEARCH_LIMIT)
            searchState = searchState.copy(criteria = criteria, results = results, searching = false)
            if (_state.value is UiState.Search) _state.value = searchState
        }
    }

    /** Where each open detail page came from, newest last, so Back retraces the pages opened from links. */
    private val detailOrigins = ArrayDeque<UiState>()

    fun openDetail(subjectId: Long) {
        val origin = _state.value
        viewModelScope.launch {
            val detail = container.searchRepository.detail(subjectId) ?: return@launch
            detailOrigins.addLast(origin)
            _state.value = UiState.Detail(detail)
        }
    }

    fun closeDetail() {
        _state.value = detailOrigins.removeLastOrNull() ?: searchState
    }

    /** Full details of a subject, for the sheet that swipes up over the quiz. */
    suspend fun lessonInfo(subjectId: Long): LessonInfo? = container.repository.lessonInfoFor(subjectId)

    /** Plays the item's WaniKani pronunciation audio, downloading it once and keeping it on the phone. */
    fun playAudio(subjectId: Long) {
        viewModelScope.launch {
            val clip = container.repository.audioFor(subjectId) ?: return@launch
            container.audioPlayer.play(clip.url, clip.level)
        }
    }

    /** Re-reads how many clips are stored, for the settings summary. */
    fun refreshAudioStorage() {
        viewModelScope.launch {
            val (count, bytes) = withContext(Dispatchers.IO) { container.audioCache.stored() }
            _audioDownload.update { it.copy(storedCount = count, storedBytes = bytes) }
        }
    }

    /**
     * Downloads every level up to the user's current one that is not on the phone yet, a level at a
     * time. Run after every home refresh, so reaching a new level fetches just that level. A level
     * that fails completely (offline) is not marked done and is tried again next time.
     */
    fun ensureAudioUpToCurrentLevel() {
        if (audioJob?.isActive == true) return
        audioJob = viewModelScope.launch {
            val current = container.repository.userLevel()
            val through = withContext(Dispatchers.IO) { container.audioCache.downloadedThrough() }
            if (current <= through) return@launch
            _audioDownload.update { it.copy(running = true, level = 0, done = 0, total = 0, failed = 0) }
            try {
                for (level in through + 1..current) {
                    val urls = container.repository.audioUrlsForLevel(level)
                    _audioDownload.update { it.copy(level = level, done = 0, total = urls.size) }
                    val failed = container.audioDownloader.downloadAll(urls, level) { done, total, failedNow ->
                        _audioDownload.update { it.copy(level = level, done = done, total = total, failed = failedNow) }
                    }
                    if (urls.isNotEmpty() && failed == urls.size) break
                    withContext(Dispatchers.IO) { container.audioCache.setDownloadedThrough(level) }
                    refreshAudioStorage()
                }
            } finally {
                withContext(NonCancellable + Dispatchers.IO) {
                    val (count, bytes) = container.audioCache.stored()
                    _audioDownload.update { it.copy(running = false, level = 0, storedCount = count, storedBytes = bytes) }
                }
            }
        }
    }

    /** Opens the audio manager: the space each level uses, newest level first. */
    fun openAudioLevels() {
        viewModelScope.launch {
            val current = container.repository.userLevel()
            _state.value = audioLevelsState(current)
            refreshAudioStorage()
        }
    }

    fun closeAudioLevels() {
        viewModelScope.launch { _state.value = UiState.Settings(container.settingsStore.current()) }
    }

    /** Frees the clips of one level; the automatic download does not fetch it again. */
    fun deleteLevelAudio(level: Int) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { container.audioCache.deleteLevel(level) }
            refreshAudioStorage()
            refreshAudioLevelsState()
        }
    }

    /** Fetches one level again, for a level that was deleted or only partly downloaded. */
    fun downloadLevelAudio(level: Int) {
        if (audioJob?.isActive == true) return
        audioJob = viewModelScope.launch {
            val urls = container.repository.audioUrlsForLevel(level)
            _audioDownload.update { it.copy(running = true, level = level, done = 0, total = urls.size, failed = 0) }
            try {
                container.audioDownloader.downloadAll(urls, level) { done, total, failedNow ->
                    _audioDownload.update { it.copy(level = level, done = done, total = total, failed = failedNow) }
                }
            } finally {
                refreshAudioStorage()
                refreshAudioLevelsState()
            }
        }
    }

    private suspend fun refreshAudioLevelsState() {
        val current = _state.value as? UiState.AudioLevels ?: return
        _state.value = audioLevelsState(current.currentLevel)
    }

    private suspend fun audioLevelsState(currentLevel: Int): UiState.AudioLevels {
        val stored = withContext(Dispatchers.IO) { container.audioCache.storedByLevel() }
        val levels = (1..currentLevel).map { level ->
            val stats = stored[level]
            LevelAudio(level, stats?.clips ?: 0, stats?.bytes ?: 0L)
        }.reversed()
        return UiState.AudioLevels(levels, currentLevel)
    }

    fun cancelAudioDownload() {
        audioJob?.cancel()
    }

    fun dismissMessage() {
        _state.update { if (it is UiState.Home) it.copy(message = null) else it }
    }

    /** Opens the level browser at [level], or the user's level. */
    fun openLevels(level: Int?) {
        viewModelScope.launch {
            val max = container.dashboardRepository.maxLevel()
            val start = (level ?: container.repository.userLevel()).coerceIn(1, max)
            _state.value = UiState.Levels(start, max)
            ensureLevel(start)
        }
    }

    /** Loads one level for the browser if it is not loaded yet. */
    fun ensureLevel(level: Int) {
        val current = _state.value as? UiState.Levels ?: return
        if (level in current.contents || level !in 1..current.maxLevel) return
        viewModelScope.launch {
            val content = container.dashboardRepository.level(level)
            _state.update { s -> if (s is UiState.Levels) s.copy(contents = s.contents + (level to content)) else s }
        }
    }

    fun openStage(group: StageGroup) {
        _state.value = UiState.StageItems(group)
        viewModelScope.launch {
            val items = container.searchRepository.find(SearchCriteria(stage = group), limit = STAGE_ITEM_LIMIT)
            _state.update { if (it is UiState.StageItems && it.group == group) it.copy(items = items) else it }
        }
    }

    fun setStageKind(kind: SubjectKind?) {
        _state.update { if (it is UiState.StageItems) it.copy(kind = kind) else it }
    }

    fun closeStage() {
        viewModelScope.launch { refreshHomeSafely() }
    }

    fun closeLevels() {
        viewModelScope.launch { refreshHome() }
    }

    /** From settings: back to the dashboard, then a sync. */
    fun syncNow() {
        viewModelScope.launch {
            refreshHome()
            sync()
        }
    }

    fun openStats() {
        _state.value = UiState.StatsView(null)
        viewModelScope.launch { _state.value = UiState.StatsView(container.statsRepository.load()) }
    }

    fun closeStats() {
        viewModelScope.launch { refreshHome() }
    }

    fun openSettings() {
        viewModelScope.launch {
            _state.value = UiState.Settings(container.settingsStore.current())
            refreshAudioStorage()
        }
    }

    fun closeSettings() {
        viewModelScope.launch { refreshHome() }
    }

    /** The settings as backup text. The API token is never part of it. */
    suspend fun settingsBackup(): String = SettingsBackup.export(container.settingsStore.current())

    fun showSettingsMessage(message: String) {
        val current = _state.value as? UiState.Settings ?: return
        _state.value = current.copy(message = message)
    }

    fun restoreSettings(text: String) {
        viewModelScope.launch {
            val message = try {
                val result = SettingsBackup.import(text, container.settingsStore.current())
                container.settingsStore.update { result.settings }
                describeRestore(result)
            } catch (e: BackupFormatException) {
                e.message ?: "This file is not a settings backup."
            }
            _state.value = UiState.Settings(container.settingsStore.current(), message)
        }
    }

    private fun describeRestore(r: SettingsBackup.ImportResult): String = buildString {
        append(if (r.applied == 1) "Restored 1 setting." else "Restored ${r.applied} settings.")
        if (r.unsupported > 0) append(" ${r.unsupported} from the file are not available in this app yet.")
        if (r.invalid.isNotEmpty()) append(" ${r.invalid.size} had unusable values and were left as they were.")
        if (r.ignoredSecrets) append(" The token in the file was not imported; enter it by hand if you need it.")
    }

    fun updateSettings(change: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            container.settingsStore.update(change)
            if (_state.value is UiState.Settings) _state.value = UiState.Settings(container.settingsStore.current())
        }
    }

    /** Checks the token against the API before keeping it. */
    fun submitToken(raw: String) {
        val candidate = raw.trim()
        if (candidate.isEmpty()) return
        _state.value = UiState.NeedsToken(checking = true)
        viewModelScope.launch {
            when (val result = container.clientFor(candidate).user()) {
                is ApiResult.Success -> {
                    container.tokenStore.save(candidate)
                    container.token = candidate
                    _state.value = UiState.Home()
                    container.syncEngine.onProgress = ::onSyncProgress
                    sync()
                }
                is ApiResult.Unauthorized -> _state.value = UiState.NeedsToken("That token was not accepted.")
                is ApiResult.NetworkError -> _state.value = UiState.NeedsToken("No connection. Check the network and try again.")
                else -> _state.value = UiState.NeedsToken("The server answered with an unexpected error ($result).")
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            container.tokenStore.clear()
            container.token = ""
            quiz = null
            _state.value = UiState.NeedsToken()
        }
    }

    fun sync() {
        val current = _state.value as? UiState.Home ?: return
        if (current.syncing) return
        _state.value = current.copy(syncing = true, progress = null, message = null)
        viewModelScope.launch {
            // Whatever goes wrong, the screen ends with a result or a message, never an endless spinner.
            val message = try {
                val report = container.syncEngine.sync()
                val failure = report.steps.values.filterIsInstance<SyncStep.Failed>().firstOrNull()?.result
                    ?: report.steps["reviews"]?.let { (it as? SyncStep.Failed)?.result }
                when (failure) {
                    null -> null
                    is ApiResult.Unauthorized -> "Your token was rejected. Sign out and enter a new one."
                    is ApiResult.NetworkError -> "Offline. Your reviews are saved and will be sent later."
                    is ApiResult.RateLimited -> "WaniKani asked us to slow down. Try again in a minute."
                    else -> "Sync did not finish. Pull down to try again."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                "Sync failed: ${e.message ?: e.javaClass.simpleName}. Pull down to try again."
            }
            if (_state.value is UiState.Home) refreshHomeSafely(message)
        }
    }

    /** Shows how far a download has got, e.g. "Downloading subjects: 3,000 of 9,452". */
    private fun onSyncProgress(name: String, fetched: Int, total: Int) {
        if (fetched == 0) return
        val what = name.replace('_', ' ')
        val text = if (total > 0) "Downloading $what: %,d of %,d".format(fetched, total) else "Downloading $what: %,d".format(fetched)
        _state.update { if (it is UiState.Home && it.syncing) it.copy(progress = text) else it }
    }

    /** Like [refreshHome], but a failure to read the local data becomes a message instead of a crash. */
    private suspend fun refreshHomeSafely(message: String? = null) {
        try {
            refreshHome(message)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update {
                if (it is UiState.Home) it.copy(syncing = false, progress = null, message = "Could not show your data: ${e.message ?: e.javaClass.simpleName}") else it
            }
        }
    }

    /** Keeps the "reviews available" notification in line with what is due now. Never lets a failure reach the screen. */
    private suspend fun refreshNotification() {
        container.refreshAlerts()
    }

    private suspend fun refreshHome(message: String? = null) {
        val saved = savedSnapshot()?.let { s -> s.sessionType?.let { SavedSession(it, s.doneItems, s.totalItems) } }
        val settings = container.settingsStore.current()
        _state.value = UiState.Home(
            container.dashboardRepository.load(),
            saved = saved,
            dryRun = settings.dryRun,
            message = message,
            textStageLabels = settings.textStageLabels,
        )
        refreshNotification()
        // Reaching a new level (or a fresh install) fetches the levels that are not on the phone yet.
        ensureAudioUpToCurrentLevel()
    }

    fun startReviews() {
        viewModelScope.launch {
            if (continueSaved(SessionType.REVIEW)) return@launch
            val subjects = container.repository.dueReviews()
            if (subjects.isEmpty()) {
                refreshHome("No reviews are due.")
                return@launch
            }
            val settings = container.settingsStore.current()
            val config = SessionConfig(
                type = SessionType.REVIEW,
                order = settings.subjectOrder,
                orderReversed = settings.reviewOrderReversed,
                overdueFirst = settings.reviewOrderOverdueFirst,
                priority = settings.priority,
                priorityContext = priorityContext(),
                maxSize = settings.maxReviewSessionSize,
                backToBack = settings.reviewBackToBack,
                readingFirst = settings.reviewReadingFirst,
                meaningFirst = settings.reviewMeaningFirst,
            )
            beginSession(subjects, config, settings)
        }
    }

    fun startLessons() {
        viewModelScope.launch {
            if (continueSaved(SessionType.LESSON)) return@launch
            val subjects = container.repository.availableLessons()
            if (subjects.isEmpty()) {
                refreshHome("No lessons are available.")
                return@launch
            }
            val settings = container.settingsStore.current()
            val config = SessionConfig(
                type = SessionType.LESSON,
                order = settings.lessonSubjectOrder,
                maxSize = settings.maxLessonSessionSize,
            )
            beginSession(subjects, config, settings)
        }
    }

    /**
     * Quizzes the subjects the current search matches. Results stay on the phone: a self-study
     * session never changes progress or sends anything to WaniKani.
     */
    fun startSelfStudy() {
        viewModelScope.launch {
            val matches = container.searchRepository.find(searchState.criteria, SELF_STUDY_CANDIDATES)
            val subjects = container.repository.selfStudySubjects(matches.map { it.id })
            if (subjects.isEmpty()) return@launch
            val settings = container.settingsStore.current()
            // The planner shuffles first, so trimming to the size limit picks a random selection.
            val config = SessionConfig(
                type = SessionType.SELF_STUDY,
                order = SubjectOrder.SHUFFLE,
                maxSize = settings.maxSelfStudySessionSize,
            )
            beginSession(subjects, config, settings)
        }
    }

    private suspend fun priorityContext() = PriorityContext(
        userLevel = container.repository.userLevel(),
        maxLevel = container.repository.maxLevel(),
        levelUpIds = container.repository.levelUpIds(),
    )

    private fun newRecorder(type: SessionType, dryRun: Boolean, srsFor: (Long) -> SrsSystem): SessionRecorder {
        val db = container.database
        return SessionRecorder(
            scope = viewModelScope,
            type = type,
            pending = db.pendingReviews(),
            assignments = db.assignments(),
            statistics = db.reviewStatistics(),
            srsFor = { assignment -> srsFor(assignment.subjectId) },
            dryRun = dryRun,
        )
    }

    /** If a session of [type] was left unfinished, resumes it and returns true; if another kind is saved, says so. */
    private suspend fun continueSaved(type: SessionType): Boolean {
        val saved = savedSnapshot()?.sessionType ?: return false
        if (saved == type) resumeSession() else refreshHome("Finish or discard your saved ${saved.label()} session first.")
        return true
    }

    fun resumeSession() {
        viewModelScope.launch {
            val snapshot = savedSnapshot()
            val type = snapshot?.sessionType
            if (snapshot == null || type == null) return@launch refreshHome("There is no saved session.")
            val systems = snapshot.items.associate { it.id to it.srs.toSystem() }
            val dryRun = container.settingsStore.current().dryRun
            val sessionRecorder = newRecorder(type, dryRun) { systems.getValue(it) }
            val engine = SessionEngine.restore(snapshot, random = { Random.nextInt(it) }, listener = sessionRecorder)
            if (engine == null) {
                clearSaved()
                sessionRecorder.finish()
                return@launch refreshHome("The saved session could not be restored, so it was discarded.")
            }
            attach(engine, sessionRecorder, type, container.settingsStore.current())
        }
    }

    /** Throws away the saved session. Nothing in it was sent, and the items stay due. */
    fun discardSavedSession() {
        viewModelScope.launch {
            clearSaved()
            refreshHome("Saved session discarded.")
        }
    }

    /** Leaves the current review or lesson session. It is saved and can be resumed from the home screen. */
    fun onLeaveSession() {
        val sessionRecorder = recorder
        val saving = persist()
        quiz = null
        recorder = null
        viewModelScope.launch {
            sessionRecorder?.finish()
            saving?.done?.await()
            refreshHome()
        }
    }

    private suspend fun beginSession(subjects: List<SessionSubject>, config: SessionConfig, settings: AppSettings) {
        val sessionSubjects: Map<Long, SessionSubject> = subjects.associateBy { it.id }
        val sessionRecorder = newRecorder(config.type, settings.dryRun) { sessionSubjects.getValue(it).srs }
        val delayed = settings.delayResultUpload && config.type != SessionType.SELF_STUDY
        val engine = SessionPlanner.plan(subjects = subjects, config = config.copy(delayed = delayed), listener = sessionRecorder)
        attach(engine, sessionRecorder, config.type, settings)
    }

    private suspend fun attach(engine: SessionEngine, sessionRecorder: SessionRecorder, type: SessionType, settings: AppSettings) {
        val answers = engine.items.mapNotNull { item ->
            container.repository.answersFor(item.id)?.let { item.id to it }
        }.toMap()
        recorder = sessionRecorder
        quiz = QuizController(engine, answers, settings.closeEnoughAction, settings.requireOnYomiInKatakana)
        sessionType = type
        sessionDryRun = settings.dryRun
        sessionShowAnswerOnWrong = settings.showAnswerOnWrong
        sessionQuickAnswerOnWrong = settings.quickAnswerOnWrong
        sessionJapaneseKeyboardOnReadings = settings.japaneseKeyboardOnReadings
        sessionKeepKeyboardOpen = settings.keepKeyboardOpen
        persist()
        if (quiz?.inPresentation == true) showLessonCard() else showNextCard()
    }

    private fun showLessonCard() {
        val controller = quiz ?: return
        val id = controller.presentedItemId ?: return showNextCard()
        val (index, total) = controller.presentationPosition
        viewModelScope.launch {
            val info = container.repository.lessonInfoFor(id) ?: return@launch
            _state.value = UiState.Lesson(info, index, total, controller.isOnFirstLessonItem, controller.isOnLastLessonItem)
        }
    }

    fun onLessonNext() {
        val controller = quiz ?: return
        if (controller.isOnLastLessonItem) {
            controller.startQuiz()
            showNextCard()
        } else {
            controller.nextLessonItem()
            persist()
            showLessonCard()
        }
    }

    fun onLessonPrevious() {
        val controller = quiz ?: return
        controller.previousLessonItem()
        persist()
        showLessonCard()
    }

    private fun showNextCard() {
        val controller = quiz ?: return
        val card = controller.card()
        if (card == null) {
            finishSession()
            return
        }
        persist()
        _state.value = UiState.Quiz(
            card,
            progress = controller.progress,
            canUndo = controller.canUndo,
            showAnswerOnWrong = sessionShowAnswerOnWrong,
            quickAnswerOnWrong = sessionQuickAnswerOnWrong,
            japaneseKeyboardOnReadings = sessionJapaneseKeyboardOnReadings,
            keepKeyboardOpen = sessionKeepKeyboardOpen,
        )
    }

    fun onInputChange(text: String) {
        _state.update { current ->
            if (current !is UiState.Quiz || current.answered) return@update current
            val next = if (current.card.kanaInput) Kana.typed(current.input, text) else text
            current.copy(input = next, feedback = null)
        }
    }

    /** Checks the answer, or moves on if it was already shown. */
    fun onSubmit() {
        val current = _state.value as? UiState.Quiz ?: return
        val controller = quiz ?: return
        if (current.answered) {
            controller.next()
            showNextCard()
            return
        }
        // A trailing lone "n" is a pending ん; the engine converts it for kana questions.
        val feedback = controller.submit(current.input)
        persist()
        _state.value = current.copy(
            feedback = feedback,
            answered = controller.answered,
            progress = controller.progress,
            canUndo = controller.canUndo,
            stageMove = stageMoveAfterAnswer(controller),
        )
    }

    fun onDontKnow() {
        val current = _state.value as? UiState.Quiz ?: return
        if (current.answered) return
        val controller = quiz ?: return
        val feedback = controller.dontKnow()
        persist()
        _state.value = current.copy(
            feedback = feedback,
            answered = true,
            progress = controller.progress,
            canUndo = controller.canUndo,
            stageMove = stageMoveAfterAnswer(controller),
        )
    }

    fun onSkip() {
        val current = _state.value as? UiState.Quiz ?: return
        if (current.answered) return
        quiz?.skip()
        showNextCard()
    }

    fun onUndo() {
        val controller = quiz ?: return
        if (!controller.canUndo) return
        controller.undo()
        showNextCard()
    }

    /** Accepts an answer the app judged wrong. */
    fun onIgnoreMistake() {
        val current = _state.value as? UiState.Quiz ?: return
        val controller = quiz ?: return
        if (!current.answered || current.feedback?.correct == true) return
        controller.ignoreMistake()
        persist()
        _state.value = current.copy(
            feedback = QuizFeedback(current.feedback!!.verdict, retry = false, correct = true),
            progress = controller.progress,
            canUndo = controller.canUndo,
            stageMove = stageMoveAfterAnswer(controller),
        )
    }

    /** The bubble is only shown for real reviews, and only when this answer completed the item. */
    private fun stageMoveAfterAnswer(controller: QuizController): StageMove? =
        if (sessionType == SessionType.REVIEW) controller.finishedStageMove() else null

    fun onWrapUp() {
        quiz?.wrapUp()
        persist()
        val current = _state.value as? UiState.Quiz ?: return
        if (current.answered) return
        showNextCard()
    }

    private fun finishSession() {
        val controller = quiz ?: return
        val sessionRecorder = recorder
        controller.finish()
        val summary = controller.summary()
        val finishedType = sessionType
        val finishedDryRun = sessionDryRun
        quiz = null
        recorder = null
        _state.value = UiState.Summary(summary.first, summary.second, sessionType, sessionDryRun)
        viewModelScope.launch {
            sessionRecorder?.finish()
            if (finishedType != SessionType.SELF_STUDY && !finishedDryRun) clearSaved()
        }
    }

    /** Leaves the summary and refreshes the home screen, sending the new results. */
    fun closeSummary() {
        viewModelScope.launch {
            refreshHome()
            sync()
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(container) as T
    }

    override fun onCleared() {
        container.audioPlayer.stop()
    }

}

private const val SEARCH_DEBOUNCE_MILLIS = 250L

/** Larger than any SRS group can be (a full account has under 10,000 subjects). */
private const val STAGE_ITEM_LIMIT = 10_000

/** How many matching subjects a self-study quiz picks from. */
private const val SELF_STUDY_CANDIDATES = 2000
