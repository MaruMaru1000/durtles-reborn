package com.example.durtlesreborn.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.durtlesreborn.data.LessonInfo
import com.example.durtlesreborn.data.PitchAccent
import com.example.durtlesreborn.data.RelatedGroup
import com.example.durtlesreborn.data.RelatedSubject
import com.example.durtlesreborn.data.ReadingInfo
import com.example.durtlesreborn.session.SubjectKind

/** One lesson card: the subject, then Back / Next (the last card starts the quiz). */
@Composable
fun LessonScreen(
    state: UiState.Lesson,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onCancel: () -> Unit,
    loadInfo: suspend (Long) -> LessonInfo?,
    onPlayAudio: (Long) -> Unit,
) {
    BackHandler(onBack = onCancel)
    var openId by remember { mutableStateOf<Long?>(null) }
    openId?.let { ItemDetailsSheet(it, loadInfo, onDismiss = { openId = null }, onPlayAudio = onPlayAudio) }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)) {
        LinearProgressIndicator(
            progress = { (state.index + 1f) / state.total.coerceAtLeast(1) },
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Lesson ${state.index + 1} of ${state.total}" },
        )
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("${state.index + 1} / ${state.total}", style = MaterialTheme.typography.labelMedium)
            TextButton(onClick = onCancel) { Text("Leave") }
        }

        SubjectCardContent(state.info, Modifier.weight(1f), onOpen = { openId = it }, onPlayAudio = onPlayAudio)

        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onPrevious, enabled = !state.isFirst, modifier = Modifier.weight(1f).height(52.dp)) { Text("Back") }
            Button(onClick = onNext, modifier = Modifier.weight(2f).height(52.dp)) {
                Text(if (state.isLast) "Start quiz" else "Next")
            }
        }
    }
}

/**
 * The subject itself: characters, meanings and readings, mnemonics and (for vocabulary) example
 * sentences. Shared by lessons and the item detail screen. [header] adds lines under the title.
 */
@Composable
fun SubjectCardContent(
    info: LessonInfo,
    modifier: Modifier = Modifier,
    /** When set, linked subjects can be tapped to open them. */
    onOpen: ((Long) -> Unit)? = null,
    /** When set and the item has audio, tapping the characters plays the pronunciation. */
    onPlayAudio: ((Long) -> Unit)? = null,
    header: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val canPlay = info.hasAudio && onPlayAudio != null
        Text(
            text = info.title,
            fontSize = if (info.title.length > 3) 56.sp else 96.sp,
            color = subjectColor(info.kind),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
                .clickable(enabled = canPlay) { onPlayAudio?.invoke(info.id) }
                .semantics { contentDescription = if (canPlay) "Play the pronunciation of ${info.title}" else info.title },
        )
        Text(
            kindLabel(info),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        if (canPlay) {
            Text(
                "Tap to hear it",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
        header()

        // A vocabulary word shows the kanji it is made of at the top, centred above the meaning.
        val componentKanji = info.related.firstOrNull { it.label == "Kanji" }
        if (componentKanji != null) {
            RelatedSection(componentKanji, onOpen, centered = true)
        }

        // Each part of the item gets its own rounded panel, alternating between two theme tints
        // so neighbouring sections are easy to tell apart.
        val sections = buildList {
            add("Meaning" to info.meanings.joinToString(", "))
            if (info.readings.isNotEmpty()) add("Reading" to readingsText(info))
            if (info.partsOfSpeech.isNotEmpty()) add("Word type" to info.partsOfSpeech.joinToString(", "))
            info.meaningMnemonic?.let { add("Meaning mnemonic" to it) }
            info.meaningHint?.let { add("Hint" to it) }
            info.readingMnemonic?.let { add("Reading mnemonic" to it) }
            info.readingHint?.let { add("Reading hint" to it) }
        }
        var alternate = false
        for ((title, body) in sections) {
            CardSection(title, body, alternate)
            alternate = !alternate
        }
        if (info.pitch.isNotEmpty()) {
            PitchSection(info.pitch, alternate)
            alternate = !alternate
        }
        if (info.contextSentences.isNotEmpty()) {
            CardSection("Example sentences", alternate) {
                for (s in info.contextSentences) {
                    Column(Modifier.padding(vertical = 2.dp)) {
                        Text(s.japanese, style = MaterialTheme.typography.bodyLarge)
                        if (s.english.isNotEmpty()) Text(s.english, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        for (group in info.related) if (group !== componentKanji) RelatedSection(group, onOpen)
        StrokeOrderSection(info)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RelatedSection(group: RelatedGroup, onOpen: ((Long) -> Unit)?, centered: Boolean = false) {
    Column(
        modifier = if (centered) Modifier.fillMaxWidth() else Modifier,
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(group.label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        FlowRow(
            horizontalArrangement = if (centered) {
                Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            } else {
                Arrangement.spacedBy(8.dp)
            },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (item in group.items) RelatedChip(item, onOpen)
        }
        if (group.more > 0) Text("and ${group.more} more", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RelatedChip(item: RelatedSubject, onOpen: ((Long) -> Unit)?) {
    val content = @Composable {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(item.title, fontSize = 26.sp, color = subjectColor(item.kind))
            if (item.subtitle.isNotEmpty()) {
                Text(item.subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 120.dp))
            }
        }
    }
    if (onOpen != null) {
        Surface(onClick = { onOpen(item.id) }, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant, content = content)
    } else {
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant, content = content)
    }
}

/** One part of an item's information in a rounded, theme-tinted panel. */
@Composable
private fun CardSection(title: String, body: String, alternate: Boolean) {
    CardSection(title, alternate) { Text(body, style = MaterialTheme.typography.bodyLarge) }
}

/** As above, for a section with more than one line of content. [alternate] picks the second tint. */
@Composable
private fun CardSection(title: String, alternate: Boolean, content: @Composable ColumnScope.() -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (alternate) scheme.surfaceContainerLow else scheme.surfaceContainerHighest,
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = scheme.primary)
            content()
        }
    }
}

/**
 * The pitch accent patterns of the word, one diagram per reading and accent position. Each mora is
 * a dot at high or low pitch, joined by a line that steps where the pitch drops, with a hollow dot
 * for the following particle. The data comes from Kanjium, which the licence requires crediting.
 */
@Composable
private fun PitchSection(pitch: List<PitchAccent>, alternate: Boolean) {
    CardSection("Pitch accent", alternate) {
        for (entry in pitch) {
            for (accent in entry.positions) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    Text(entry.reading, style = MaterialTheme.typography.bodyLarge)
                    PitchDiagram(entry.reading, accent, Modifier.padding(top = 6.dp))
                }
            }
        }
        Text(
            "High dots are accented, low dots unaccented; the hollow dot is the following particle.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
        Text(
            "Pitch data: Kanjium (Uros O.), CC BY-SA 4.0.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun kindLabel(info: LessonInfo) = when (info.kind) {
    SubjectKind.RADICAL -> "Radical, level ${info.level}"
    SubjectKind.KANJI -> "Kanji, level ${info.level}"
    SubjectKind.VOCABULARY -> "Vocabulary, level ${info.level}"
    SubjectKind.KANA_VOCABULARY -> "Kana-only vocabulary, level ${info.level}"
    null -> "Level ${info.level}"
}

/** Kanji readings are grouped by type, the others are a plain list. */
private fun readingsText(info: LessonInfo): String {
    fun join(list: List<ReadingInfo>) = list.joinToString("、") { it.text }
    val kanji = info.readings.filter { it.type != null }
    if (kanji.isEmpty()) return join(info.readings)
    return listOf("onyomi" to "On’yomi", "kunyomi" to "Kun’yomi", "nanori" to "Nanori")
        .mapNotNull { (type, label) -> kanji.filter { it.type == type }.takeIf { it.isNotEmpty() }?.let { "$label: ${join(it)}" } }
        .joinToString("\n")
}

@Composable
fun subjectColor(kind: SubjectKind?): androidx.compose.ui.graphics.Color {
    val palette = LocalSubjectColors.current
    return when (kind) {
        SubjectKind.RADICAL -> palette.radical
        SubjectKind.KANJI -> palette.kanji
        else -> palette.vocabulary
    }
}
