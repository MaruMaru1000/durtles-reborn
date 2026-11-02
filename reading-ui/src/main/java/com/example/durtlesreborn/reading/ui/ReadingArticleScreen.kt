package com.example.durtlesreborn.reading.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.example.durtlesreborn.reading.core.ReadingPalette
import com.example.durtlesreborn.reading.core.TokenMatch

/**
 * The native reader: the extracted text with each known WaniKani word tinted by its SRS stage.
 * Tapping a word opens a small card with reading, meaning, level and stage, plus a link to the
 * item screen. The end of the article lists the WaniKani items grouped by stage.
 */
@Composable
fun ReadingArticleScreen(
    data: ReaderData,
    palette: ReadingPalette,
    onBack: () -> Unit,
    onOpenItem: (Long) -> Unit,
    onOpenWeb: () -> Unit,
) {
    var mode by remember { mutableStateOf(HighlightMode.ALL) }
    var selected by remember { mutableStateOf<TokenMatch?>(null) }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val text = remember(data, mode, palette) { readerText(data, mode, palette) }
    val grouped = remember(data) {
        data.matches.filterNotNull()
            .distinctBy { it.subject.id }
            .groupBy { stageLabel(it.srsStage) }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onBack) { Text("Back") }
            Text(data.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
            TextButton(onClick = onOpenWeb) { Text("Web") }
        }
        Text(
            "${data.sourceName} · ${data.score.knownPercent}% known · ${data.score.uncoveredPercent}% not in WaniKani",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (option in HighlightMode.entries) {
                FilterChip(mode == option, { mode = option }, { Text(option.label) })
            }
        }

        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp)) {
            item {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                    onTextLayout = { layout = it },
                    modifier = Modifier.fillMaxWidth().pointerInput(data, mode) {
                        detectTapGestures { position ->
                            val result = layout ?: return@detectTapGestures
                            val offset = result.getOffsetForPosition(position)
                            selected = data.matches.filterNotNull().firstOrNull {
                                offset >= it.token.start && offset < it.token.end
                            }
                        }
                    },
                )
            }
            item {
                Text(
                    "WaniKani items in this article",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 20.dp, bottom = 6.dp),
                )
            }
            val groupedItems = grouped
            for ((stage, items) in groupedItems) {
                item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(stage, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        for (match in items) {
                            Row(
                                Modifier.fillMaxWidth().clickable { onOpenItem(match.subject.id.toLong()) }.padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(match.subject.characters.orEmpty(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(end = 10.dp))
                                Text(match.subject.meanings.firstOrNull().orEmpty(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                Text("L${match.subject.level}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    selected?.let { match ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = {
                Column {
                    Text(match.subject.characters.orEmpty(), style = MaterialTheme.typography.headlineSmall)
                    Text(stageLabel(match.srsStage), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (match.subject.readings.isNotEmpty()) {
                        Text(match.subject.readings.joinToString("、"), style = MaterialTheme.typography.bodyLarge)
                    }
                    Text(match.subject.meanings.joinToString(", "), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Level ${match.subject.level} · ${subjectTypeLabel(match.subject.type)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val id = match.subject.id.toLong()
                    selected = null
                    onOpenItem(id)
                }) { Text("Open item") }
            },
            dismissButton = { TextButton(onClick = { selected = null }) { Text("Close") } },
        )
    }
}

/** The article text with a tinted background behind every highlighted token. */
private fun readerText(data: ReaderData, mode: HighlightMode, palette: ReadingPalette): AnnotatedString =
    buildAnnotatedString {
        append(data.text)
        for (match in data.matches) {
            if (match == null) continue
            val color = highlightColor(match, mode, palette) ?: continue
            val start = match.token.start.coerceIn(0, data.text.length)
            val end = match.token.end.coerceIn(start, data.text.length)
            if (start == end) continue
            addStyle(SpanStyle(background = color.copy(alpha = 0.30f)), start, end)
        }
    }
