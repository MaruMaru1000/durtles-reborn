package com.example.durtlesreborn.reading.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import com.example.durtlesreborn.reading.core.ReadingPalette
import com.example.durtlesreborn.reading.core.TokenMatch

/**
 * The native reader: the extracted text with each known WaniKani word tinted by its SRS stage.
 * Tapping a word opens a small card with reading, meaning, level and stage, plus a link to the
 * item screen. The end of the article lists the WaniKani items grouped by stage.
 */
@OptIn(ExperimentalLayoutApi::class)
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
    var showLegend by remember { mutableStateOf(false) }
    val text = remember(data, mode, palette) { readerText(data, mode, palette) }
    val grouped = remember(data) {
        data.matches.filterNotNull()
            .distinctBy { it.subject.id }
            .groupBy { stageLabel(it.srsStage) }
    }

    BackHandler(enabled = showLegend) { showLegend = false }

    Box(Modifier.fillMaxSize()) {
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
            FlowRow(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                for (option in HighlightMode.entries) {
                    FilterChip(mode == option, { mode = option }, { Text(option.label) })
                }
                AssistChip(onClick = { showLegend = true }, label = { Text("Legend") })
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

        AnimatedVisibility(
            visible = showLegend,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                Modifier.fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .pointerInput(Unit) { detectTapGestures { showLegend = false } },
            )
        }
        AnimatedVisibility(
            visible = showLegend,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        ) {
            LegendSheet(palette = palette, onDismiss = { showLegend = false })
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

/**
 * A legend that peeks down from the top edge: the reader's palette, what each stage colour means,
 * and how the highlight modes choose words.
 */
@Composable
private fun LegendSheet(palette: ReadingPalette, onDismiss: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().pointerInput(Unit) { detectTapGestures { } },
        shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
    ) {
        Column(
            Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier.align(Alignment.CenterHorizontally).padding(bottom = 2.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("SRS colour legend", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Close") }
            }
            LegendRow(Color(palette.apprentice), "Apprentice", "Stages 1–4 · still learning")
            LegendRow(Color(palette.guru), "Guru", "Stages 5–6 · counted as known")
            LegendRow(Color(palette.master), "Master", "Stage 7")
            LegendRow(Color(palette.enlightened), "Enlightened", "Stage 8")
            LegendRow(Color(palette.burned), "Burned", "Stage 9")
            LegendRow(Color(palette.locked), "Locked", "Not started yet")
            Text(
                "Words are tinted at 30% so the text stays readable. Known shows Guru and above; " +
                    "Unknown shows Apprentice and Locked words. All tints every WaniKani word; Off removes the tint.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
            )
        }
    }
}

@Composable
private fun LegendRow(color: Color, title: String, detail: String) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).background(color))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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
