package com.example.durtlesreborn.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.selection.toggleable
import com.example.durtlesreborn.answer.CloseEnoughAction
import com.example.durtlesreborn.notify.WidgetUpdater
import com.example.durtlesreborn.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

private val reviewOrders = listOf(
    "SHUFFLE", "DUE", "LEVEL", "TYPE", "SRS",
    "LEVEL_THEN_TYPE", "LEVEL_THEN_SRS", "TYPE_THEN_LEVEL", "TYPE_THEN_SRS", "SRS_THEN_TYPE", "SRS_THEN_LEVEL",
    "LEVEL_THEN_TYPE_THEN_SRS", "LEVEL_THEN_SRS_THEN_TYPE", "TYPE_THEN_LEVEL_THEN_SRS",
    "TYPE_THEN_SRS_THEN_LEVEL", "SRS_THEN_TYPE_THEN_LEVEL", "SRS_THEN_LEVEL_THEN_TYPE",
)

private val notificationFrequencies = listOf("ONLY_NEW_REVIEWS", "ONCE_PER_HOUR", "CONTINUOUSLY")

private val notificationPriorities = listOf("MIN", "LOW", "DEFAULT", "HIGH", "MAX")

private val notificationCategories = listOf(
    "CALL", "NAVIGATION", "MESSAGE", "EMAIL", "EVENT", "PROMO", "ALARM", "PROGRESS", "SOCIAL", "ERROR",
    "TRANSPORT", "SYSTEM", "SERVICE", "REMINDER", "RECOMMENDATION", "STATUS",
)

private val lessonOrders = listOf(
    "SHUFFLE", "LEVEL_THEN_TYPE", "RADICALS_THEN_LEVEL_THEN_TYPE", "TYPE_THEN_LEVEL", "LEVEL_THEN_SHUFFLE", "TYPE_THEN_SHUFFLE",
)

private val priorities = listOf(
    "NONE", "RADICALS_FIRST", "LEVEL_UP_FIRST", "CURRENT_LEVEL_RADICAL_KANJI_FIRST", "CURRENT_LEVEL_FIRST",
)

/** "LEVEL_THEN_TYPE" becomes "Level, then type". */
private fun pretty(name: String) = name.lowercase().replace("_then_", ", then ").replace('_', ' ').replaceFirstChar { it.uppercase() }

@Composable
fun SettingsScreen(
    state: UiState.Settings,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    onBack: () -> Unit,
    onExport: suspend () -> String,
    onImport: (String) -> Unit,
    onMessage: (String) -> Unit,
    onSync: () -> Unit,
    onSignOut: () -> Unit,
    audioDownload: AudioDownload,
    onOpenAudio: () -> Unit,
) {
    val s = state.settings
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Back") }
            Text("Settings", style = MaterialTheme.typography.titleLarge)
        }

        Section("Account")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onSync) { Text("Sync now") }
            OutlinedButton(onClick = onSignOut) { Text("Sign out") }
        }
        AudioDownloadRow(audioDownload, onOpenAudio)

        Section("Display")
        ChoiceRow(
            "Colour theme",
            AppTheme.fromName(s.theme, AppTheme.DYNAMIC).label,
            AppTheme.entries.map { it.name to it.label },
            swatch = { name -> fixedScheme(AppTheme.fromName(name, AppTheme.DYNAMIC))?.let { it.background to it.primary } },
        ) { name -> onChange { it.copy(theme = name) } }
        SwitchRow(
            "Show stage names under items",
            "A1–A4, G1–G2, Master, Enlightened and Burned instead of the five-segment bars; the stage legend is hidden.",
            s.textStageLabels,
        ) { onChange { it.copy(textStageLabels = !it.textStageLabels) } }

        Section("Notifications")
        SwitchRow("Notify about new reviews", "Shows how many reviews and lessons are waiting. Needs the notification permission.", s.enableNotifications) {
            onChange { it.copy(enableNotifications = !it.enableNotifications) }
        }
        ChoiceRow("Update frequency", pretty(s.notificationFrequency), notificationFrequencies.map { it to pretty(it) }) { name ->
            onChange { it.copy(notificationFrequency = name) }
        }
        ChoiceRow("Priority", pretty(s.notificationPriority), notificationPriorities.map { it to pretty(it) }) { name ->
            onChange { it.copy(notificationPriority = name) }
        }
        ChoiceRow("Category", pretty(s.notificationCategory), notificationCategories.map { it to pretty(it) }) { name ->
            onChange { it.copy(notificationCategory = name) }
        }

        val context = LocalContext.current
        OutlinedButton(onClick = { WidgetUpdater.requestPin(context) }, modifier = Modifier.padding(top = 8.dp)) {
            Text("Add widget to home screen")
        }

        Section("Syncing")
        SwitchRow("Allow background sync", "Send reviews and fetch changes every hour when online.", s.enableBackgroundSync) {
            onChange { it.copy(enableBackgroundSync = !it.enableBackgroundSync) }
        }
        SwitchRow("Sync when the app is opened", null, s.syncOnOpen) { onChange { it.copy(syncOnOpen = !it.syncOnOpen) } }

        Section("Graded Reading")
        SwitchRow(
            "Enable Graded Reading",
            "Read articles scored against your WaniKani progress, with words coloured by SRS stage and example sentences on item pages. Articles are cached only on this phone.",
            s.gradedReading,
        ) { onChange { it.copy(gradedReading = !it.gradedReading) } }

        Section("Reviews")
        SwitchRow(
            "Send results when the session ends",
            "Leave a review or lesson session and carry on later. Results are saved, and sent after you finish or wrap up.",
            s.delayResultUpload,
        ) { onChange { it.copy(delayResultUpload = !it.delayResultUpload) } }
        StepperRow("Reviews per session", s.maxReviewSessionSize, 10, AppSettings.MIN_SESSION_SIZE, AppSettings.MAX_SESSION_SIZE) { value ->
            onChange { it.copy(maxReviewSessionSize = value) }
        }
        ChoiceRow("Order", pretty(s.reviewOrder), reviewOrders.map { it to pretty(it) }) { name -> onChange { it.copy(reviewOrder = name) } }
        SwitchRow("Reverse order", null, s.reviewOrderReversed) { onChange { it.copy(reviewOrderReversed = !it.reviewOrderReversed) } }
        SwitchRow("Overdue items first", null, s.reviewOrderOverdueFirst) { onChange { it.copy(reviewOrderOverdueFirst = !it.reviewOrderOverdueFirst) } }
        ChoiceRow("Priority", pretty(s.reviewOrderPriority), priorities.map { it to pretty(it) }) { name -> onChange { it.copy(reviewOrderPriority = name) } }
        SwitchRow(
            "Ask meaning and reading back to back",
            "Both questions for one item before moving on to the next item.",
            s.reviewBackToBack,
        ) { onChange { it.copy(reviewBackToBack = !it.reviewBackToBack) } }
        // One choice instead of the original's two switches; both falsy means random.
        val questionOrder = when {
            s.reviewMeaningFirst -> "MEANING_FIRST"
            s.reviewReadingFirst -> "READING_FIRST"
            else -> "RANDOM"
        }
        ChoiceRow(
            "Question order",
            pretty(questionOrder),
            listOf("RANDOM", "MEANING_FIRST", "READING_FIRST").map { it to pretty(it) },
        ) { name ->
            onChange { it.copy(reviewMeaningFirst = name == "MEANING_FIRST", reviewReadingFirst = name == "READING_FIRST") }
        }

        Section("Lessons")
        StepperRow("Lessons per session", s.maxLessonSessionSize, 1, AppSettings.MIN_SESSION_SIZE, AppSettings.MAX_SESSION_SIZE) { value ->
            onChange { it.copy(maxLessonSessionSize = value) }
        }
        ChoiceRow("Order", pretty(s.lessonOrder), lessonOrders.map { it to pretty(it) }) { name -> onChange { it.copy(lessonOrder = name) } }

        BackupSection(state.message, onExport, onImport, onMessage)

        Section("Developer")
        SwitchRow(
            "Dry run (practice mode)",
            "Sessions run as normal, but nothing is saved on this phone or sent to WaniKani. Switch it off to go back to your real account. Results already waiting to be sent are still sent.",
            s.dryRun,
        ) { onChange { it.copy(dryRun = !it.dryRun) } }

        Section("Answers")
        SwitchRow(
            "Keep the keyboard open between answers",
            "Reviews and lessons. On: the field stays focused, so the next answer needs no extra tap. Off: the keyboard collapses after each answer.",
            s.keepKeyboardOpen,
        ) { onChange { it.copy(keepKeyboardOpen = !it.keepKeyboardOpen) } }
        ChoiceRow(
            "Answer that is close enough",
            closeEnoughLabel(s.closeEnoughAction),
            CloseEnoughAction.entries.map { it.name to closeEnoughLabel(it) },
        ) { name -> onChange { it.copy(closeEnoughAction = CloseEnoughAction.valueOf(name)) } }
        SwitchRow("Require on’yomi answers in katakana", null, s.requireOnYomiInKatakana) {
            onChange { it.copy(requireOnYomiInKatakana = !it.requireOnYomiInKatakana) }
        }
        SwitchRow(
            "Show the answer after a wrong answer",
            "Off: the answer stays hidden. Swipe up on the question for the full details.",
            s.showAnswerOnWrong,
        ) { onChange { it.copy(showAnswerOnWrong = !it.showAnswerOnWrong) } }
        SwitchRow(
            "Quick answer on a wrong answer",
            "On: the accepted answer appears above the item by itself after a wrong answer, for a quick check. Off: it stays hidden until you tap the red banner. Either way, a further tap opens the full item details.",
            s.quickAnswerOnWrong,
        ) { onChange { it.copy(quickAnswerOnWrong = !it.quickAnswerOnWrong) } }
        SwitchRow(
            "Japanese keyboard for reading answers",
            "Reading questions ask the keyboard for Japanese, so your Japanese layout (such as 12-key flick) appears; meaning questions ask for English. The keyboard must support switching this way. Off: it keeps whatever language it was last on.",
            s.japaneseKeyboardOnReadings,
        ) { onChange { it.copy(japaneseKeyboardOnReadings = !it.japaneseKeyboardOnReadings) } }
    }
}

private fun closeEnoughLabel(action: CloseEnoughAction) = when (action) {
    CloseEnoughAction.SILENTLY_ACCEPT -> "Accept silently"
    CloseEnoughAction.ACCEPT_WITH_TOAST -> "Accept and show the match"
    CloseEnoughAction.ACCEPT_WITH_TOAST_NO_LM -> "Accept and show the match (no lenience on short words)"
    CloseEnoughAction.SHAKE_AND_RETRY -> "Let me try again"
    CloseEnoughAction.REJECT -> "Count as wrong"
}

/**
 * Summary of the automatic pronunciation download, under Account. The Manage button opens the
 * per-level screen where space can be inspected and freed.
 */
@Composable
private fun AudioDownloadRow(state: AudioDownload, onOpen: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Pronunciation audio", style = MaterialTheme.typography.bodyLarge)
        Text(
            when {
                state.running && state.total > 0 -> "Downloading level ${state.level}: ${state.done} of ${state.total} clips${failedSuffix(state.failed)}"
                state.running -> "Preparing level ${state.level}…"
                state.storedCount == 0 -> "No clips on this phone yet."
                else -> "${state.storedCount} clips on this phone (${formatSize(state.storedBytes)})."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = onOpen, modifier = Modifier.fillMaxWidth()) { Text("Manage audio downloads") }
        Text(
            "Each level downloads by itself once you reach it. Open this to see or delete the space each level uses.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun failedSuffix(failed: Int) = if (failed > 0) ", $failed failed" else ""

internal fun formatSize(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000.0)
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000 -> "%.0f kB".format(bytes / 1_000.0)
    else -> "$bytes B"
}

@Composable
private fun Section(title: String) {
    HorizontalDivider(Modifier.padding(top = 16.dp))
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
}

@Composable
private fun SwitchRow(title: String, summary: String?, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = { onToggle() }).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            summary?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun StepperRow(title: String, value: Int, step: Int, min: Int, max: Int, onValue: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = { onValue((value - step).coerceAtLeast(min)) }, enabled = value > min) { Text("−") }
        Text("$value", modifier = Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = { onValue((value + step).coerceAtMost(max)) }, enabled = value < max) { Text("+") }
    }
}

@Composable
private fun ChoiceRow(
    title: String,
    current: String,
    options: List<Pair<String, String>>,
    /** Optional two colours to draw beside an option, so themes can be seen before they are picked. */
    swatch: ((String) -> Pair<Color, Color>?)? = null,
    onPick: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clickable { open = true }.padding(vertical = 8.dp).semantics { }) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(current, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for ((name, label) in options) {
                DropdownMenuItem(
                    text = { Text(label) },
                    leadingIcon = swatch?.let { colours ->
                        { Swatch(colours(name)) }
                    },
                    onClick = { open = false; onPick(name) },
                )
            }
        }
    }
}

/** A round chip showing a theme's background with its accent colour as a dot; a plain ring for wallpaper colours. */
@Composable
private fun Swatch(colours: Pair<Color, Color>?) {
    val ring = MaterialTheme.colorScheme.outline
    Box(
        Modifier.size(28.dp).clip(CircleShape).background(colours?.first ?: Color.Transparent).border(1.dp, ring, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(colours?.second ?: ring))
    }
}

/** Largest backup file accepted; real backups are a few kilobytes. */
private const val MAX_BACKUP_BYTES = 1_000_000

/** Saves and restores the settings as a JSON file chosen with Android's file picker. The token is never included. */
@Composable
private fun BackupSection(message: String?, onExport: suspend () -> String, onImport: (String) -> Unit, onMessage: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = runCatching {
                val text = onExport()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                }
            }.isSuccess
            onMessage(if (ok) "Settings saved. The file does not contain your API token." else "Could not write the file.")
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)!!.use { it.readNBytes(MAX_BACKUP_BYTES + 1).toString(Charsets.UTF_8) }
                }
            }.getOrNull()
            when {
                text == null -> onMessage("Could not read the file.")
                text.length > MAX_BACKUP_BYTES -> onMessage("That file is too large to be a settings backup.")
                else -> onImport(text)
            }
        }
    }

    Section("Backup")
    Text(
        "Save your settings to a file, or restore them from one. Backups from the original app work too. Your API token is never saved or restored.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { save.launch("durtles_reborn_settings_${LocalDate.now()}.json") }) { Text("Back up settings") }
        OutlinedButton(onClick = { open.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) { Text("Restore") }
    }
    message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp)) }
}
