package com.example.durtlesreborn.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.durtlesreborn.data.SearchCriteria
import com.example.durtlesreborn.data.SearchResult
import com.example.durtlesreborn.data.StageGroup
import com.example.durtlesreborn.reading.bridge.ReadingExamples
import com.example.durtlesreborn.session.SubjectKind

@Composable
fun SearchScreen(
    state: UiState.Search,
    onCriteria: (SearchCriteria) -> Unit,
    onOpen: (Long) -> Unit,
    onSelfStudy: () -> Unit,
    onBack: () -> Unit,
) {
    val c = state.criteria
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Back") }
            Text("Search", style = MaterialTheme.typography.titleLarge)
        }
        OutlinedTextField(
            value = c.text,
            onValueChange = { onCriteria(c.copy(text = it)) },
            label = { Text("Characters, meaning or reading") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )

        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((kind, label) in listOf(SubjectKind.RADICAL to "Radicals", SubjectKind.KANJI to "Kanji", SubjectKind.VOCABULARY to "Vocabulary")) {
                FilterChip(
                    selected = kind in c.kinds,
                    onClick = {
                        // Vocabulary stands for kana-only vocabulary too.
                        val group = if (kind == SubjectKind.VOCABULARY) setOf(kind, SubjectKind.KANA_VOCABULARY) else setOf(kind)
                        onCriteria(c.copy(kinds = if (kind in c.kinds) c.kinds - group else c.kinds + group))
                    },
                    label = { Text(label) },
                )
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StageMenu(c.stage) { onCriteria(c.copy(stage = it)) }
            LevelMenu("From", c.minLevel) { onCriteria(c.copy(minLevel = it, maxLevel = maxOf(it, c.maxLevel))) }
            LevelMenu("To", c.maxLevel) { onCriteria(c.copy(maxLevel = it, minLevel = minOf(it, c.minLevel))) }
        }

        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                if (state.searching) "Searching…" else "${state.results.size}${if (state.results.size >= SEARCH_LIMIT) "+" else ""} results",
                style = MaterialTheme.typography.labelMedium,
            )
            // Self-study quizzes whatever the search matches; results are not sent to WaniKani.
            TextButton(onClick = onSelfStudy, enabled = state.results.isNotEmpty() && !state.searching) { Text("Quiz these") }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(state.results, key = { it.id }) { r -> ResultRow(r) { onOpen(r.id) } }
        }
    }
}

const val SEARCH_LIMIT = 200

@Composable
private fun ResultRow(r: SearchResult, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(r.title, fontSize = 28.sp, color = subjectColor(r.kind), modifier = Modifier.width(112.dp))
            Column(Modifier.weight(1f)) {
                Text(r.meaning, style = MaterialTheme.typography.bodyLarge)
                r.reading?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Level ${r.level}", style = MaterialTheme.typography.labelSmall)
                Text(r.stage.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        HorizontalDivider()
    }
}

@Composable
private fun StageMenu(selected: StageGroup?, onPick: (StageGroup?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }) { Text(selected?.label ?: "Any stage") }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        DropdownMenuItem(text = { Text("Any stage") }, onClick = { open = false; onPick(null) })
        for (g in StageGroup.entries) DropdownMenuItem(text = { Text(g.label) }, onClick = { open = false; onPick(g) })
    }
}

@Composable
private fun LevelMenu(label: String, value: Int, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }) { Text("$label $value") }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        for (level in 1..60) DropdownMenuItem(text = { Text("Level $level") }, onClick = { open = false; onPick(level) })
    }
}

@Composable
fun ItemDetailScreen(
    state: UiState.Detail,
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
    onPlayAudio: (Long) -> Unit,
    readingExamples: ReadingExamples? = null,
    onOpenReadingArticle: ((Long) -> Unit)? = null,
) {
    val d = state.detail
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Back") }
        }
        SubjectCardContent(
            d.info,
            Modifier.weight(1f),
            onOpen = onOpen,
            onPlayAudio = onPlayAudio,
            readingExamples = readingExamples,
            onOpenReadingArticle = onOpenReadingArticle,
        ) {
            Text(
                buildString {
                    append(d.stage.label)
                    d.percentageCorrect?.let { append(" · $it% correct") }
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
        Spacer(Modifier.padding(4.dp))
    }
}
