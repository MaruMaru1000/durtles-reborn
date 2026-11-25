package com.example.durtlesreborn.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.durtlesreborn.R
import com.example.durtlesreborn.update.AppUpdates
import com.example.durtlesreborn.update.UpdateStatus

/**
 * The popup shown when the app finds a newer release: Cancel or Update. After Update it stays up
 * to show the download, ask for the install permission if needed, and report a failure. It never
 * appears in the middle of a review or lesson ([busy]); Cancel hides that version until the next
 * check, which happens the next time the app is opened.
 */
@Composable
fun UpdatePrompt(updates: AppUpdates, enabled: Boolean, busy: Boolean) {
    val context = LocalContext.current
    val status by updates.status.collectAsState()
    val confirmation by updates.confirmation.collectAsState()
    var dismissed by rememberSaveable { mutableStateOf<Long?>(null) }
    var started by rememberSaveable { mutableStateOf(false) }

    // Android's own install confirmation, when it asks for one.
    LaunchedEffect(confirmation) {
        confirmation?.let { intent ->
            runCatching { context.startActivity(intent) }
            updates.confirmationShown()
        }
    }
    // A fresh check (the app opened again later, or "Check now") may offer a cancelled version again.
    LaunchedEffect(status) {
        if (status is UpdateStatus.Checking) dismissed = null
    }

    val show = when (val s = status) {
        is UpdateStatus.Available -> started || ((enabled || s.requested) && !busy && s.update.versionCode != dismissed)
        is UpdateStatus.Downloading, is UpdateStatus.NeedsPermission, is UpdateStatus.Installing -> started
        is UpdateStatus.Failed -> started && s.update != null
        else -> false
    }
    if (!show) return
    val appName = stringResource(R.string.app_name)
    fun close(versionCode: Long?) {
        started = false
        dismissed = versionCode
    }

    when (val s = status) {
        is UpdateStatus.Available -> AlertDialog(
            onDismissRequest = { close(s.update.versionCode) },
            title = { Text("Update available") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "$appName ${s.update.versionName} is available. You have ${updates.currentVersion}." +
                            (if (s.update.sizeBytes > 0) " Download size: ${formatSize(s.update.sizeBytes)}." else ""),
                    )
                    val notes = plainReleaseNotes(s.update.notes)
                    if (notes.isNotEmpty()) {
                        Text(
                            notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState()),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { started = true; updates.install() }) { Text("Update") }
            },
            dismissButton = { TextButton(onClick = { close(s.update.versionCode) }) { Text("Cancel") } },
        )

        is UpdateStatus.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Downloading update") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (s.total > 0) {
                        LinearProgressIndicator(progress = { (s.done.toFloat() / s.total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                        Text("${formatSize(s.done)} of ${formatSize(s.total)}")
                    } else {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { updates.cancel(); close(s.update.versionCode) }) { Text("Cancel") }
            },
        )

        is UpdateStatus.NeedsPermission -> AlertDialog(
            onDismissRequest = { close(s.update.versionCode) },
            title = { Text("Allow updates") },
            text = {
                Text(
                    "To update itself, $appName needs permission to install apps. Open the settings, turn on " +
                        "\"Allow from this source\", then come back and tap Update.",
                )
            },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        updates.installPermissionIntent()?.let { intent ->
                            runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        }
                    }) { Text("Open settings") }
                    TextButton(onClick = { updates.install() }) { Text("Update") }
                }
            },
            dismissButton = { TextButton(onClick = { close(s.update.versionCode) }) { Text("Cancel") } },
        )

        is UpdateStatus.Installing -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Installing update") },
            text = {
                Text(
                    "Android is installing $appName ${s.update.versionName}. The app closes while it updates; " +
                        "a notification lets you reopen it.",
                )
            },
            confirmButton = { TextButton(onClick = { close(s.update.versionCode) }) { Text("Hide") } },
        )

        is UpdateStatus.Failed -> AlertDialog(
            onDismissRequest = { close(s.update?.versionCode) },
            title = { Text("Update failed") },
            text = { Text(s.message) },
            confirmButton = { TextButton(onClick = { updates.install() }) { Text("Try again") } },
            dismissButton = { TextButton(onClick = { close(s.update?.versionCode) }) { Text("Close") } },
        )

        else -> Unit
    }
}

/** One line for Settings saying where updating stands. */
fun updateStatusLine(status: UpdateStatus, current: String): String = when (status) {
    UpdateStatus.Idle -> "Installed version $current."
    UpdateStatus.Checking -> "Checking GitHub for a newer version…"
    is UpdateStatus.UpToDate -> "Installed version $current. Up to date" + (status.latestName?.let { " (latest release $it)." } ?: ".")
    is UpdateStatus.Available -> "Version ${status.update.versionName} is available. You have $current."
    is UpdateStatus.Downloading -> "Downloading version ${status.update.versionName}…"
    is UpdateStatus.NeedsPermission -> "Version ${status.update.versionName} is waiting for permission to install."
    is UpdateStatus.Installing -> "Installing version ${status.update.versionName}…"
    is UpdateStatus.Failed -> status.message
}

/**
 * Release notes from GitHub as plain text for the popup: Markdown emphasis and heading marks
 * removed, and everything from the "Install" section on left out (it is about downloading by hand).
 */
internal fun plainReleaseNotes(markdown: String): String {
    val kept = mutableListOf<String>()
    for (raw in markdown.replace("\r\n", "\n").lines()) {
        val heading = raw.trimStart().takeIf { it.startsWith("#") }?.trimStart('#')?.trim()
        if (heading != null && heading.equals("Install", ignoreCase = true)) break
        val line = (heading ?: raw)
            .replace("**", "")
            .replace("`", "")
            .replace(Regex("""^(\s*)[-*] """), "$1• ")
        kept += line.trimEnd()
    }
    return kept.joinToString("\n").replace(Regex("\n{3,}"), "\n\n").trim()
}
