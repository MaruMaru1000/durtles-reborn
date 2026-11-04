package com.example.durtlesreborn.ui

import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.example.durtlesreborn.data.LessonInfo
import com.example.durtlesreborn.kana.Kana
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.produceState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.durtlesreborn.data.HomeCounts
import com.example.durtlesreborn.DurtlesApp
import com.example.durtlesreborn.data.AppWaniKaniReadOnly
import com.example.durtlesreborn.reading.bridge.ReadingFeatureFactory
import com.example.durtlesreborn.reading.bridge.ReadingNavigator
import com.example.durtlesreborn.reading.bridge.ReadingRouteHost
import com.example.durtlesreborn.reading.core.ReadingPalette
import com.example.durtlesreborn.session.QuestionKind
import com.example.durtlesreborn.session.StageMove
import com.example.durtlesreborn.session.QuizCard
import com.example.durtlesreborn.session.SessionType
import kotlinx.coroutines.delay

private const val TOKEN_PAGE = "https://www.wanikani.com/settings/personal_access_tokens"

@Composable
fun AppRoot(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsState()
    val audioDownload by viewModel.audioDownload.collectAsState()
    val settings by viewModel.settings.collectAsState()

    // Graded Reading lives in its own modules; the app only builds the bridge object and renders
    // its host when a route is open. When the flag is off the feature is inert: no UI, no work.
    val context = LocalContext.current
    val stageColors = LocalStageColors.current
    val actionColors = LocalActionColors.current
    val palette = remember(stageColors, actionColors) {
        ReadingPalette(
            apprentice = stageColors.apprentice.toArgb(),
            guru = stageColors.guru.toArgb(),
            master = stageColors.master.toArgb(),
            enlightened = stageColors.enlightened.toArgb(),
            burned = stageColors.burned.toArgb(),
            locked = actionColors.locked.toArgb(),
        )
    }
    val feature = remember(settings.gradedReading) {
        val app = context.applicationContext as DurtlesApp
        ReadingFeatureFactory.create(app, AppWaniKaniReadOnly(app.container.database), palette, settings.gradedReading)
    }
    LaunchedEffect(feature, palette) { feature.updatePalette(palette) }
    LaunchedEffect(feature, settings.gradedReading) {
        if (settings.gradedReading) feature.scheduleWork(context) else feature.cancelWork(context)
    }
    // A new feature object (flag switched, or the screen recreated) replaces the old one; release
    // its database collectors so the old instance cannot keep working in the background.
    DisposableEffect(feature) {
        onDispose { feature.close() }
    }
    val readingRoute by feature.route.collectAsState()
    val navigator = remember(feature) {
        object : ReadingNavigator {
            override fun openWaniKaniItem(subjectId: Long) {
                feature.closeReading()
                viewModel.openDetail(subjectId)
            }

            override fun closeReading() = Unit
        }
    }

    // Back from a sub-screen goes to where it was opened from instead of closing the app.
    BackHandler(enabled = state is UiState.Settings || state is UiState.AudioLevels || state is UiState.Search || state is UiState.Detail || state is UiState.StatsView || state is UiState.Levels || state is UiState.StageItems) {
        when (state) {
            is UiState.Settings -> viewModel.closeSettings()
            is UiState.AudioLevels -> viewModel.closeAudioLevels()
            is UiState.Search -> viewModel.closeSearch()
            is UiState.Detail -> viewModel.closeDetail()
            is UiState.StatsView -> viewModel.closeStats()
            is UiState.Levels -> viewModel.closeLevels()
            is UiState.StageItems -> viewModel.closeStage()
            else -> Unit
        }
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.safeDrawingPadding()) {
            if (readingRoute != null) {
                BackHandler { feature.back() }
                ReadingRouteHost(feature)
            } else {
            when (val s = state) {
                UiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                is UiState.NeedsToken -> TokenScreen(s, viewModel::submitToken)
                is UiState.Home -> DashboardScreen(
                    s,
                    DashboardActions(
                        startReviews = viewModel::startReviews,
                        startLessons = viewModel::startLessons,
                        openSearch = viewModel::openSearch,
                        openLevels = viewModel::openLevels,
                        openSettings = viewModel::openSettings,
                        openStats = viewModel::openStats,
                        openStage = viewModel::openStage,
                        openItem = viewModel::openDetail,
                        refresh = viewModel::sync,
                        dismissMessage = viewModel::dismissMessage,
                        resumeSession = viewModel::resumeSession,
                        discardSession = viewModel::discardSavedSession,
                        // A failed start leaves the feature disabled even with the setting on.
                        openReading = if (feature.enabled) {
                            { feature.entryPoint.openLibrary(navigator) }
                        } else {
                            null
                        },
                    ),
                )
                is UiState.StageItems -> StageScreen(s, viewModel::setStageKind, viewModel::openDetail, viewModel::closeStage)
                is UiState.Levels -> LevelsScreen(s, viewModel::ensureLevel, viewModel::openDetail, viewModel::closeLevels)
                is UiState.Lesson -> LessonScreen(s, viewModel::onLessonNext, viewModel::onLessonPrevious, viewModel::onLeaveSession, viewModel::lessonInfo, viewModel::playAudio)
                is UiState.Settings -> SettingsScreen(
                    s, viewModel::updateSettings, viewModel::closeSettings, viewModel::settingsBackup, viewModel::restoreSettings, viewModel::showSettingsMessage,
                    viewModel::syncNow, viewModel::signOut, audioDownload, viewModel::openAudioLevels,
                )
                is UiState.AudioLevels -> AudioLevelsScreen(
                    s, audioDownload, viewModel::closeAudioLevels, viewModel::deleteLevelAudio,
                    viewModel::downloadLevelAudio, viewModel::cancelAudioDownload,
                )
                is UiState.Search -> SearchScreen(s, viewModel::onSearchCriteria, viewModel::openDetail, viewModel::startSelfStudy, viewModel::closeSearch)
                is UiState.Detail -> ItemDetailScreen(
                    s, viewModel::closeDetail, viewModel::openDetail, viewModel::playAudio,
                    readingExamples = feature.examples,
                    onOpenReadingArticle = { id -> feature.entryPoint.openArticle(id, navigator) },
                )
                is UiState.StatsView -> StatsScreen(s.stats, viewModel::closeStats)
                is UiState.Quiz -> QuizScreen(
                    s, viewModel::onInputChange, viewModel::onSubmit, viewModel::onDontKnow,
                    viewModel::onSkip, viewModel::onUndo, viewModel::onIgnoreMistake, viewModel::onWrapUp,
                    viewModel::onLeaveSession, viewModel::lessonInfo, viewModel::playAudio,
                )
                is UiState.Summary -> SummaryScreen(s, viewModel::closeSummary)
            }
            }
        }
    }
}

@Composable
private fun TokenScreen(state: UiState.NeedsToken, onSubmit: (String) -> Unit) {
    var token by remember { mutableStateOf("") }
    val uriHandler = LocalUriHandler.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text("Durtles Reborn", style = MaterialTheme.typography.headlineLarge)
        Text(
            "An unofficial, third-party app for WaniKani. It is not made or endorsed by WaniKani.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            "Create a personal access token on the WaniKani website with the permissions to start assignments " +
                "and create reviews, then paste it here. The app never asks for your password.",
            style = MaterialTheme.typography.bodyMedium,
        )
        TextButton(onClick = { uriHandler.openUri(TOKEN_PAGE) }) { Text("Open the token page") }
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("API token") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSubmit(token) }),
            isError = state.error != null,
            supportingText = state.error?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { onSubmit(token) },
            enabled = token.isNotBlank() && !state.checking,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text(if (state.checking) "Checking…" else "Continue")
        }
    }
}

@Composable
private fun QuizScreen(
    state: UiState.Quiz,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDontKnow: () -> Unit,
    onSkip: () -> Unit,
    onUndo: () -> Unit,
    onIgnoreMistake: () -> Unit,
    onWrapUp: () -> Unit,
    onLeave: () -> Unit,
    loadInfo: suspend (Long) -> LessonInfo?,
    onPlayAudio: (Long) -> Unit,
) {
    // Back always leaves the session (and saves it, so it can be resumed). Undo is deliberate:
    // the Undo button or Backspace on the shown answer, never a stray back swipe.
    BackHandler(onBack = onLeave)
    val card = state.card
    var showDetails by rememberSaveable(card.itemId, card.kind) { mutableStateOf(false) }
    // Tapping the wrong-answer banner reveals the accepted answer above the characters instead of
    // opening the full details; undo and a new question hide it again. With `quick_answer_on_wrong`
    // on, a wrong answer reveals it by itself; tapping the banner still works either way.
    var revealed by rememberSaveable(card.itemId, card.kind) { mutableStateOf(false) }
    val wrong = state.answered && state.feedback?.correct == false
    LaunchedEffect(state.answered, state.feedback?.correct, state.quickAnswerOnWrong, state.showAnswerOnWrong) {
        if (!state.answered) {
            revealed = false
        } else if (autoRevealQuickAnswer(state.feedback?.correct, state.quickAnswerOnWrong, state.showAnswerOnWrong)) {
            revealed = true
        }
    }
    val progress = state.progress
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val keepKeyboard = state.keepKeyboardOpen
    // The stage bubble shows once per completed item and clears itself, like a toast; pressing
    // Next or undo clears it early because the state's move goes away.
    var stageBubble by remember { mutableStateOf<StageMove?>(null) }
    // Keyed by card as well as by the move: two different items can legitimately make the same
    // move (Guru 1 → Guru 2 twice), and the bubble must appear for both.
    LaunchedEffect(card.itemId, card.kind, state.stageMove) {
        val move = state.stageMove
        if (move == null) {
            stageBubble = null
        } else {
            stageBubble = move
            delay(2000)
            stageBubble = null
        }
    }
    // Keep the cursor at the end: the input method rewrites the text as the user types. The value
    // the IME sends is kept as-is (including its composing region) in onValueChange; this only
    // pulls in changes made elsewhere (undo, a resumed session).
    var field by remember(card.itemId, card.kind) { mutableStateOf(TextFieldValue("")) }
    if (field.text != state.input) field = TextFieldValue(state.input, TextRange(state.input.length))
    // With the setting on, the field is focused for every question so typing can start at once.
    // When the language hint changes (reading <-> meaning), the input session is restarted so the
    // keyboard reads the new hint; focusing an already focused field would not re-send it.
    var lastJapaneseHint by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(card.itemId, card.kind, keepKeyboard, state.japaneseKeyboardOnReadings) {
        val japaneseHint = state.japaneseKeyboardOnReadings && card.kanaInput
        if (keepKeyboard) {
            if (state.japaneseKeyboardOnReadings && lastJapaneseHint != null && lastJapaneseHint != japaneseHint) {
                runCatching { focusManager.clearFocus() }
            }
            runCatching { focus.requestFocus() }
        }
        lastJapaneseHint = japaneseHint
    }
    // Answering either keeps the keyboard up (the field stays focused, so the next answer needs no
    // extra tap) or collapses it, from the `keep_keyboard_open` setting.
    LaunchedEffect(state.answered) {
        if (state.answered) {
            if (keepKeyboard) {
                runCatching { focus.requestFocus() }
                keyboard?.show()
            } else {
                focusManager.clearFocus()
                keyboard?.hide()
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)) {
        val done = progress.answeredItems.toFloat() / progress.totalItems.coerceAtLeast(1)
        LinearProgressIndicator(
            progress = { done },
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "${progress.answeredItems} of ${progress.totalItems} items done" },
        )
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                (if (progress.wrappingUp) "Wrap-up: " else "") + "${progress.answeredItems} / ${progress.totalItems}",
                style = MaterialTheme.typography.labelMedium,
            )
            Row {
                TextButton(onClick = onLeave) { Text("Leave") }
                TextButton(onClick = onWrapUp, enabled = !state.answered) { Text("Wrap up") }
            }
        }

        // Details reveal the answer, so they open only after answering: swipe up, or tap the hint.
        val swipeThreshold = with(LocalDensity.current) { 64.dp.toPx() }
        BoxWithConstraints(
            Modifier.weight(1f).fillMaxWidth().pointerInput(state.answered) {
                var travelled = 0f
                detectVerticalDragGestures(
                    onDragStart = { travelled = 0f },
                    onDragEnd = { if (state.answered && travelled < -swipeThreshold) showDetails = true },
                    onVerticalDrag = { _, delta -> travelled += delta },
                )
            },
        ) {
            // With the keyboard up and the feedback panel shown, the question area is short. Scale
            // the item down so the reading and the two chips always fit instead of sliding under
            // the reveal button.
            val compact = maxHeight < 360.dp
            Column(
                Modifier.align(Alignment.Center).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                val playable = state.answered && card.kind.hasAudio
                // Furigana: the accepted reading above the characters, but only after a correct
                // answer. A wrong answer keeps it hidden until the banner is tapped; then the
                // accepted answer itself is shown there instead of opening the details sheet.
                val revealedAnswer = if (state.answered && state.feedback?.correct == false && revealed) {
                    acceptedAnswers(card)
                } else {
                    null
                }
                val confirmation = revealedAnswer
                    ?: confirmationReading(state.answered, state.feedback?.correct, card.answers.confirmationReading)
                if (confirmation != null) {
                    Text(
                        text = confirmation,
                        fontSize = if (compact) 17.sp else 22.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        modifier = Modifier.semantics {
                            contentDescription = if (revealedAnswer != null) "Answer: $confirmation" else "Reading: $confirmation"
                        },
                    )
                }
                Text(
                    text = card.characters,
                    fontSize = when {
                        card.characters.length > 3 -> if (compact) 34.sp else 56.sp
                        compact -> 60.sp
                        else -> 96.sp
                    },
                    color = kindColor(card.kind),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.clickable(enabled = playable) { onPlayAudio(card.itemId) },
                )
                val subjectColor = kindColor(card.kind)
                val answerColors = answerTypeColors(MaterialTheme.colorScheme, card.kind.reading)
                // Two chips spell out the question: the subject on the left in its own colour, and
                // what has to be typed on the right, so a reading is never confused with a meaning.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    QuizChip(subjectLabel(card.kind), subjectColor, contentColorOn(subjectColor), compact)
                    QuizChip(answerLabel(card.kind), answerColors.background, answerColors.content, compact)
                }
            }
            // Toast-like bubble above the characters: fades in when an answer completes a review
            // item, then leaves on its own. It is anchored to the top of the question area rather
            // than offset from the centre, so it stays on screen when the keyboard shrinks it.
            StageBubbleToast(
                move = stageBubble,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
            )
        }
        if (state.answered) {
            // The swipe-up gesture over the question still opens the details; the button is the
            // visible way in, replacing the old drag handle and "Swipe up for details" hint. It
            // spans the screen and is thin so it reads as a control at a glance. Once a wrong
            // answer has been revealed, its label points at the full details instead.
            OutlinedButton(
                onClick = { showDetails = true },
                modifier = Modifier.fillMaxWidth().height(40.dp)
                    .semantics { contentDescription = "Reveal the answer and the full details of this item" },
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
            ) {
                Text(
                    if (wrong && (revealed || state.showAnswerOnWrong)) {
                        "Tap for the full item details"
                    } else {
                        "Tap to reveal answer"
                    },
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            QuizAction("Undo", state.canUndo, onUndo, Modifier.weight(1f))
            QuizAction("Skip", !state.answered, onSkip, Modifier.weight(1f))
            QuizAction("Don't know", !state.answered, onDontKnow, Modifier.weight(1f))
            QuizAction("I was right", wrong, onIgnoreMistake, Modifier.weight(1f))
        }

        OutlinedTextField(
            value = field,
            // Typing after the answer is ignored, but the field keeps focus so the IME never closes.
            onValueChange = { value ->
                if (!state.answered) {
                    // Direct kana input arrives already converted. Keep the IME's composing region
                    // so it can still form small kana and dakuten/handakuten (ひ -> び, は -> ぱ);
                    // rebuilding the value with a collapsed cursor ends the composition and those
                    // transformations then do nothing. Only a romaji conversion replaces it.
                    val converted = if (card.kanaInput) Kana.typed(field.text, value.text) else value.text
                    field = if (converted == value.text) value else TextFieldValue(converted, TextRange(converted.length))
                    onInputChange(converted)
                } else if (state.canUndo && value.text.length < state.input.length) {
                    // Backspace on the shown answer takes it back, the same as Undo and Back.
                    onUndo()
                }
            },
            singleLine = true,
            placeholder = { Text(if (card.kanaInput) "答え" else "Meaning") },
            trailingIcon = if (state.japaneseKeyboardOnReadings) {
                {
                    val context = LocalContext.current
                    IconButton(onClick = {
                        context.getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
                    }) {
                        Icon(AppIcons.Keyboard, contentDescription = "Switch keyboard")
                    }
                }
            } else {
                null
            },
            keyboardOptions = answerKeyboardOptions(card.kanaInput, state.japaneseKeyboardOnReadings),
            keyboardActions = KeyboardActions(onDone = {
                // Done answers the question. When the keyboard is meant to stay up, it is kept
                // across the answer so the next one needs no extra tap.
                val answering = !state.answered
                onSubmit()
                if (answering && keepKeyboard) {
                    runCatching { focus.requestFocus() }
                    keyboard?.show()
                }
            }),
            isError = state.feedback != null && !state.feedback.correct,
            modifier = Modifier.fillMaxWidth().focusRequester(focus).onPreviewKeyEvent { event ->
                // Soft keyboards send the delete key before the text shrinks on some devices, so
                // the key itself is caught here and the text-change path above covers the rest.
                val delete = event.key == Key.Backspace || event.key == Key.Delete
                if (delete && state.answered && state.canUndo && event.type == KeyEventType.KeyDown) {
                    onUndo()
                    true
                } else {
                    false
                }
            },
        )

        FeedbackPanel(
            state,
            onSubmit,
            revealed = revealed,
            onBannerTap = { correct ->
                when (bannerTap(correct, revealed, state.showAnswerOnWrong)) {
                    BannerTap.REVEAL -> revealed = true
                    BannerTap.DETAILS -> showDetails = true
                }
            },
        )
    }

    if (showDetails) ItemDetailsSheet(card.itemId, loadInfo, onDismiss = { showDetails = false }, onPlayAudio = onPlayAudio)
}

/**
 * Everything about an item, in a sheet that swipes up over a quiz or lesson. Linked subjects open in
 * the same sheet, with Back to retrace, so the session underneath is left as it was.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ItemDetailsSheet(
    subjectId: Long,
    loadInfo: suspend (Long) -> LessonInfo?,
    onDismiss: () -> Unit,
    onPlayAudio: ((Long) -> Unit)? = null,
) {
    var trail by remember(subjectId) { mutableStateOf(listOf(subjectId)) }
    val current = trail.last()
    val info by produceState<LessonInfo?>(null, current) { value = loadInfo(current) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        if (trail.size > 1) TextButton(onClick = { trail = trail.dropLast(1) }, modifier = Modifier.padding(horizontal = 12.dp)) { Text("Back") }
        val loaded = info
        if (loaded == null) {
            Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) { CircularProgressIndicator() }
        } else {
            SubjectCardContent(loaded, Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp), onOpen = { trail = trail + it }, onPlayAudio = onPlayAudio)
        }
    }
}

/** One of the four equally wide actions above the answer field, kept away from where the thumbs type. */
@Composable
private fun QuizAction(label: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    TextButton(onClick = onClick, enabled = enabled, modifier = modifier, contentPadding = PaddingValues(horizontal = 2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

@Composable
private fun FeedbackPanel(
    state: UiState.Quiz,
    onNext: () -> Unit,
    revealed: Boolean,
    onBannerTap: (correct: Boolean) -> Unit,
) {
    val feedback = state.feedback
    Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (feedback != null && feedback.retry) {
            Text("That is not an answer to this question. Try again.", color = MaterialTheme.colorScheme.error)
        }
        if (state.answered && feedback != null) {
            val correct = feedback.correct
            val scheme = MaterialTheme.colorScheme
            // Rounded like the item panels: a green shade harmonised with the theme when right, the
            // theme's red when wrong. The accepted answer only appears when it may be shown.
            val feedbackColors = if (correct) correctFeedbackColors(scheme) else wrongFeedbackColors(scheme)
            val container = feedbackColors.container
            val content = feedbackColors.content
            Surface(
                // The banner is a hidden hit target. On a wrong answer it reveals the accepted
                // answer above the characters; on a correct one it still opens the details sheet.
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .clickable(
                        onClickLabel = if (correct || revealed || state.showAnswerOnWrong) {
                            "Show the full details"
                        } else {
                            "Reveal the answer"
                        },
                        role = Role.Button,
                    ) { onBannerTap(correct) },
                shape = RoundedCornerShape(16.dp),
                color = container,
                border = BorderStroke(1.dp, content.copy(alpha = 0.18f)),
            ) {
                Column(
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        if (correct) "Correct" else "Incorrect",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = content,
                    )
                    if (correct && feedback.verdict.nearMatch) {
                        Text(
                            "Close enough to “${feedback.verdict.matchedAnswer}”.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = content,
                        )
                    }
                    feedback.verdict.digraphMatch?.let {
                        Text(
                            "Watch the small kana: ${it.regularKana} and ${it.smallKana} are different.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = content,
                        )
                    }
                    if (correct || state.showAnswerOnWrong) {
                        Text(acceptedAnswers(state.card), style = MaterialTheme.typography.bodyLarge, color = content)
                        if (!correct) {
                            Text(
                                "Tap for the full item details.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = content.copy(alpha = 0.8f),
                            )
                        }
                    } else if (revealed) {
                        Text(
                            "Tap again for the full item details.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = content.copy(alpha = 0.8f),
                        )
                    } else {
                        Text(
                            "The answer is hidden. Tap this banner to reveal it above the item.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = content.copy(alpha = 0.8f),
                        )
                    }
                }
            }
            Button(onClick = onNext, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Next") }
        }
    }
}

/**
 * The keyboard for the answer field. With the hint setting on, reading questions ask the IME for
 * Japanese (so the user's Japanese layout, such as 12-key flick, comes up) and meaning questions ask
 * for English. The IME decides whether to honour the hint. Off keeps the current keyboard language.
 */
private fun answerKeyboardOptions(reading: Boolean, hintJapanese: Boolean): KeyboardOptions = when {
    !hintJapanese -> KeyboardOptions(imeAction = ImeAction.Done)
    reading -> KeyboardOptions(
        hintLocales = LocaleList(Locale("ja")),
        autoCorrectEnabled = false,
        capitalization = KeyboardCapitalization.None,
        imeAction = ImeAction.Done,
    )
    else -> KeyboardOptions(hintLocales = LocaleList(Locale("en")), imeAction = ImeAction.Done)
}

private fun acceptedAnswers(card: QuizCard): String {
    val answers = card.answers
    return if (card.kanaInput) {
        val shown = when (card.kind) {
            QuestionKind.KANJI_ONYOMI -> answers.readings.filter { it.acceptedAnswer && it.isOnYomi }
            QuestionKind.KANJI_KUNYOMI -> answers.readings.filter { it.acceptedAnswer && it.isKunYomi }
            else -> answers.acceptedReadings
        }
        shown.joinToString("、") { it.reading }.ifEmpty { "—" }
    } else {
        answers.meanings.filter { it.acceptedAnswer }.joinToString(", ") { it.text }.ifEmpty { "—" }
    }
}

/** The subject chip: the kind of item being asked about. */
private fun subjectLabel(kind: QuestionKind) = when (kind) {
    QuestionKind.RADICAL_NAME -> "Radical"
    QuestionKind.KANJI_MEANING,
    QuestionKind.KANJI_READING,
    QuestionKind.KANJI_ONYOMI,
    QuestionKind.KANJI_KUNYOMI,
    -> "Kanji"
    else -> "Vocabulary"
}

/** The answer chip: a radical asks for its English name, so that counts as a meaning. */
private fun answerLabel(kind: QuestionKind) = if (kind.reading) "Reading" else "Meaning"

/** A small rounded label used under the item to say what kind of question this is. */
@Composable
private fun QuizChip(
    text: String,
    background: androidx.compose.ui.graphics.Color,
    content: androidx.compose.ui.graphics.Color,
    compact: Boolean,
) {
    Surface(shape = RoundedCornerShape(50), color = background, contentColor = content) {
        Text(
            text,
            style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = if (compact) 9.dp else 12.dp, vertical = if (compact) 2.dp else 4.dp),
        )
    }
}

@Composable
private fun kindColor(kind: QuestionKind): androidx.compose.ui.graphics.Color {
    val palette = LocalSubjectColors.current
    return when (kind) {
        QuestionKind.RADICAL_NAME -> palette.radical
        QuestionKind.KANJI_MEANING, QuestionKind.KANJI_READING, QuestionKind.KANJI_ONYOMI, QuestionKind.KANJI_KUNYOMI -> palette.kanji
        else -> palette.vocabulary
    }
}

@Composable
private fun SummaryScreen(state: UiState.Summary, onDone: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Session complete", style = MaterialTheme.typography.headlineMedium)
        Text(
            when (state.type) {
                SessionType.LESSON -> "${state.totalItems} ${if (state.totalItems == 1) "lesson" else "lessons"} learned"
                SessionType.SELF_STUDY -> "${state.totalItems} ${if (state.totalItems == 1) "item" else "items"} practised"
                SessionType.REVIEW -> "${state.totalItems} ${if (state.totalItems == 1) "item" else "items"} reviewed"
            },
            style = MaterialTheme.typography.titleMedium,
        )
        Text("${state.perfectItems} without a mistake", style = MaterialTheme.typography.bodyLarge)
        if (state.dryRun && state.type != SessionType.SELF_STUDY) {
            Text(
                "Dry run: nothing was saved or sent to WaniKani.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.tertiary,
                textAlign = TextAlign.Center,
            )
        }
        if (state.type == SessionType.SELF_STUDY) {
            Text(
                "Self-study results stay on your phone. They do not change your progress or reach WaniKani.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Done") }
    }
}
