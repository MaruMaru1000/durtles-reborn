package com.example.durtlesreborn.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import com.example.durtlesreborn.data.BreakdownRow
import com.example.durtlesreborn.data.DayForecast
import com.example.durtlesreborn.data.Dashboard
import com.example.durtlesreborn.data.HeatMonth
import com.example.durtlesreborn.data.LevelProgress
import com.example.durtlesreborn.data.LevelTile
import com.example.durtlesreborn.data.StageGroup
import com.example.durtlesreborn.data.heatLevel
import com.example.durtlesreborn.session.SessionType
import com.example.durtlesreborn.session.SubjectKind
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.ceil

data class DashboardActions(
    val startReviews: () -> Unit,
    val startLessons: () -> Unit,
    val openSearch: () -> Unit,
    val openLevels: (Int?) -> Unit,
    val openSettings: () -> Unit,
    val openStats: () -> Unit,
    val openStage: (StageGroup) -> Unit,
    val openItem: (Long) -> Unit,
    val refresh: () -> Unit,
    val dismissMessage: () -> Unit,
    val resumeSession: () -> Unit,
    val discardSession: () -> Unit,
    /** Null when the Graded Reading feature flag is off: the icon is hidden. */
    val openReading: (() -> Unit)? = null,
)

/** What a tile's long-press card says about when it is next reviewed. */
fun nextReviewText(tile: LevelTile, now: Long): String = when {
    !tile.unlocked -> "Locked"
    tile.stage == null || tile.stage == 0 -> "Not started"
    StageGroup.of(tile.stage) == StageGroup.BURNED -> "Burned"
    tile.nextReviewAt == null -> "N/A"
    tile.nextReviewAt <= now -> "Available now"
    else -> formatUntil(tile.nextReviewAt - now)
}

/** How many of a tile's five progress segments are filled, from its SRS stage. */
fun filledSegments(stage: Int?): Int = if (stage == null || stage <= 0) 0 else ceil(stage.coerceAtMost(9) * 5 / 9.0).toInt()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(state: UiState.Home, a: DashboardActions) {
    val d = state.dashboard
    Column(Modifier.fillMaxSize()) {
        DashboardTopBar(d?.level, d?.username, a)
        if (state.syncing) SyncStrip(state.progress)
        if (state.dryRun) DryRunStrip()
        state.message?.let { MessageBanner(it, a.dismissMessage) }
        PullToRefreshBox(isRefreshing = state.syncing, onRefresh = a.refresh, modifier = Modifier.weight(1f)) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val wide = maxWidth >= 600.dp
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (d == null) {
                        Column(Modifier.fillMaxWidth().padding(48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            CircularProgressIndicator()
                            Text(
                                state.progress ?: if (state.syncing) "Connecting to WaniKani…" else "Loading…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (state.syncing) {
                                Text(
                                    "The first sync downloads all your items and can take a few minutes.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else {
                        state.saved?.let { ResumeCard(it, a) }
                        DashboardContent(d, wide, a, state.textStageLabels)
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardTopBar(level: Int?, username: String?, a: DashboardActions) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { a.openLevels(level) }
                .semantics { contentDescription = "Level ${level ?: "unknown"}. Open the level browser" },
            contentAlignment = Alignment.Center,
        ) {
            Text(level?.toString() ?: "–", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        Text(
            username ?: "Durtles Reborn",
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
        )
        IconButton(onClick = a.openSearch) { Icon(AppIcons.Search, "Search") }
        IconButton(onClick = { a.openLevels(level) }) { Icon(AppIcons.Levels, "Levels") }
        a.openReading?.let { open -> IconButton(onClick = open) { Icon(AppIcons.Reading, "Graded reading") } }
        IconButton(onClick = a.openSettings) { Icon(AppIcons.Settings, "Settings") }
    }
}

@Composable
private fun SyncStrip(progress: String?) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Text(
            progress ?: "Syncing…",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun DryRunStrip() {
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
        Text(
            "Dry run is on: sessions are not saved or sent to WaniKani.",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun MessageBanner(message: String, onDismiss: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(vertical = 8.dp))
            IconButton(onClick = onDismiss) { Icon(AppIcons.Close, "Dismiss") }
        }
    }
}

@Composable
private fun DashboardContent(d: Dashboard, wide: Boolean, a: DashboardActions, stageText: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ActionCard("Lessons", d.counts.lessonsAvailable, LocalActionColors.current.lessons, Modifier.weight(1f), a.startLessons, lessons = true)
        ActionCard("Reviews", d.counts.reviewsDue, LocalActionColors.current.reviews, Modifier.weight(1f), a.startReviews, lessons = false)
    }
    d.progress?.let { LevelPill(it) { a.openLevels(it.level) } }
    val palette = LocalSubjectColors.current
    // The legend sits with the forecast, just above it, so it explains the tile bars without
    // pushing the Radicals and Kanji cards down the screen. Stage names need no legend.
    val showLegend = !stageText && (d.radicals.isNotEmpty() || d.kanji.isNotEmpty())
    if (wide) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TileCard("Radicals", d.radicals, palette.radical, Modifier.weight(1f), a.openItem, stageText)
            TileCard("Kanji", d.kanji, palette.kanji, Modifier.weight(1f), a.openItem, stageText)
        }
        if (showLegend) StageLegend()
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ForecastCard(d.days, Modifier.weight(1f), a.openStats)
            StageTiles(d.breakdown, Modifier.weight(1f), a.openStage)
        }
        ActivityHeatmap(d.activity)
    } else {
        TileCard("Radicals", d.radicals, palette.radical, Modifier.fillMaxWidth(), a.openItem, stageText)
        TileCard("Kanji", d.kanji, palette.kanji, Modifier.fillMaxWidth(), a.openItem, stageText)
        if (showLegend) StageLegend()
        ForecastCard(d.days, Modifier.fillMaxWidth(), a.openStats)
        StageTiles(d.breakdown, Modifier.fillMaxWidth(), a.openStage)
        ActivityHeatmap(d.activity)
    }
}

/** Offers to carry on with a session that was left unfinished, or to throw it away. */
@Composable
private fun ResumeCard(saved: SavedSession, a: DashboardActions) {
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val what = if (saved.type == SessionType.LESSON) "Lessons" else "Reviews"
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("$what in progress", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(
                "${saved.doneItems} of ${saved.totalItems} items done. Nothing is sent until you finish or wrap up.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = a.resumeSession) { Text("Resume") }
                TextButton(onClick = { confirmDiscard = true }) { Text("Discard") }
            }
        }
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard this session?") },
            text = { Text("The answers you gave in it are lost. The items stay due, and nothing is sent.") },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; a.discardSession() }) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep") } },
        )
    }
}

/** A big tappable card for lessons or reviews. Grey when there is nothing to do. */
@Composable
private fun ActionCard(title: String, count: Int, color: Color, modifier: Modifier, onClick: () -> Unit, lessons: Boolean) {
    val enabled = count > 0
    val background = if (enabled) color else MaterialTheme.colorScheme.surfaceVariant
    val content = if (enabled) contentColorOn(color) else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier.height(120.dp).clip(RoundedCornerShape(16.dp)).background(background)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = "$title: $count" },
    ) {
        // Own artwork: a book for lessons, a pencil for reviews.
        Icon(
            if (lessons) AppIcons.Book else AppIcons.Pencil,
            contentDescription = null,
            tint = content.copy(alpha = 0.18f),
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 14.dp, end = 18.dp).size(42.dp),
        )
        Text(
            title, color = content, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
        )
        Box(
            Modifier.align(Alignment.BottomEnd).padding(12.dp).clip(CircleShape)
                .background(if (enabled) Color.White else MaterialTheme.colorScheme.surface)
                .padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            Text(
                count.toString(),
                color = if (enabled) Color(0xFF14130F) else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** "27 of 36 kanji passed" as a bar, with a mark where 90% (enough to level up) is. */
@Composable
private fun LevelPill(p: LevelProgress, onClick: () -> Unit) {
    val fraction = if (p.totalKanji == 0) 0f else p.passedKanji.toFloat() / p.totalKanji
    val track = MaterialTheme.colorScheme.surfaceVariant
    val fill = MaterialTheme.colorScheme.primaryContainer
    val mark = MaterialTheme.colorScheme.primary
    Box(
        Modifier.fillMaxWidth().height(44.dp).clip(CircleShape).background(track).clickable(onClick = onClick)
            .semantics { contentDescription = "${p.passedKanji} of ${p.totalKanji} kanji passed. ${p.neededKanji} are needed to level up." },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).align(Alignment.CenterStart).background(fill))
        Canvas(Modifier.fillMaxSize()) {
            val x = size.width * LevelProgress.LEVEL_UP_SHARE.toFloat()
            drawRoundRect(mark, Offset(x, size.height * 0.2f), Size(2.dp.toPx(), size.height * 0.6f), CornerRadius(1.dp.toPx()))
        }
        Text("${p.passedKanji} of ${p.totalKanji} kanji passed", style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * Key for the five bars under every radical and kanji tile: the bar fills in five steps as the
 * item climbs the SRS stages, and its colour is the stage the item is in now.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StageLegend() {
    val stages = LocalStageColors.current
    val keys = listOf(
        "Apprentice" to stages.apprentice,
        "Guru" to stages.guru,
        "Master" to stages.master,
        "Enlightened" to stages.enlightened,
        "Burned" to stages.burned,
    )
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            for ((label, color) in keys) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(Modifier.size(width = 14.dp, height = 5.dp).clip(RoundedCornerShape(2.dp)).background(color))
                    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Text(
            "The five bars under each item fill as it climbs these stages; the colour is the stage it is in now.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * A contribution-style heatmap of the items passed or burned each day: six rows, one per month
 * (oldest first), each row one cell per day of that month. Day numbers sit above the first row.
 */
@Composable
private fun ActivityHeatmap(months: List<HeatMonth>) {
    if (months.isEmpty()) return
    val max = months.flatMap { it.days }.mapNotNull { it?.count }.maxOrNull() ?: 0
    val empty = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    fun color(level: Int): Color = when (level) {
        0 -> empty
        1 -> accent.copy(alpha = 0.25f)
        2 -> accent.copy(alpha = 0.45f)
        3 -> accent.copy(alpha = 0.7f)
        else -> accent
    }
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Progress heatmap", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    "Last ${months.size} months",
                    style = MaterialTheme.typography.labelSmall,
                    color = muted,
                )
            }
            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val gap = 0.5.dp
                val labelWidth = 24.dp
                val cell = ((maxWidth - labelWidth - gap * 30) / 31).coerceIn(4.dp, 11.dp)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.width(labelWidth))
                        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                            for (day in 1..31) {
                                Box(Modifier.width(cell), contentAlignment = Alignment.Center) {
                                    Text("$day", fontSize = 7.sp, lineHeight = 7.sp, color = muted)
                                }
                            }
                        }
                    }
                    for (month in months) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                month.label,
                                fontSize = 10.sp,
                                lineHeight = 10.sp,
                                color = muted,
                                modifier = Modifier.width(labelWidth),
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                                for (day in month.days) {
                                    Box(
                                        Modifier.size(cell).clip(RoundedCornerShape(2.dp))
                                            .background(color(heatLevel(day?.count ?: 0, max))),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Less", style = MaterialTheme.typography.labelSmall, color = muted)
                for (level in 0..4) {
                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(color(level)))
                }
                Text("More", style = MaterialTheme.typography.labelSmall, color = muted)
            }
            Text(
                "Items passed or burned each day. Grey means no activity.",
                style = MaterialTheme.typography.labelSmall,
                color = muted,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TileCard(
    title: String,
    tiles: List<LevelTile>,
    color: Color,
    modifier: Modifier,
    onOpen: (Long) -> Unit,
    stageText: Boolean,
) {
    var showDetails by rememberSaveable(title) { mutableStateOf(false) }
    Surface(modifier, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                // The eye reveals the meaning (and reading) under every tile in this card.
                IconButton(onClick = { showDetails = !showDetails }) {
                    Icon(
                        if (showDetails) AppIcons.Visibility else AppIcons.VisibilityOff,
                        if (showDetails) "Hide meanings and readings" else "Show meanings and readings",
                    )
                }
            }
            if (tiles.isEmpty()) {
                Text("Nothing here yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                FlowRow(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (tile in tiles) SubjectTile(tile, color, onOpen, stageText, showDetails)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SubjectTile(tile: LevelTile, color: Color, onOpen: (Long) -> Unit, stageText: Boolean, showDetails: Boolean) {
    var peek by remember { mutableStateOf(false) }
    val bg = if (tile.unlocked) color else LocalActionColors.current.locked
    val fg = contentColorOn(bg)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = if (showDetails) Modifier.width(64.dp) else Modifier) {
        Box(
            Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)).background(bg)
                .combinedClickable(onClick = { onOpen(tile.id) }, onLongClick = { peek = true })
                .semantics { contentDescription = "${tile.title}, ${tile.meaning}, ${nextReviewText(tile, System.currentTimeMillis())}" },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                tile.title, color = fg, maxLines = 1, softWrap = false, textAlign = TextAlign.Center,
                fontSize = when (tile.title.length) { 1 -> 26.sp; 2 -> 20.sp; else -> 11.sp },
            )
            DropdownMenu(expanded = peek, onDismissRequest = { peek = false }) { PeekCard(tile) }
        }
        if (showDetails) {
            val muted = MaterialTheme.colorScheme.onSurfaceVariant
            tile.reading?.takeIf { it.isNotBlank() }?.let { reading ->
                Text(
                    reading,
                    fontSize = 8.sp,
                    lineHeight = 10.sp,
                    color = muted,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                )
            }
            Text(
                tile.meaning.ifBlank { "—" },
                fontSize = 9.sp,
                lineHeight = 11.sp,
                color = muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 1.dp),
            )
        }
        if (stageText) {
            // Height is reserved even for blanks so a row of tiles stays aligned.
            Text(
                if (tile.unlocked) stageLabel(tile.stage).orEmpty() else "",
                fontSize = 8.sp,
                lineHeight = 10.sp,
                color = stageColor(tile.stage),
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(top = 2.dp),
            )
        } else {
            StageBar(tile)
        }
    }
}

/**
 * The SRS stage as the short text used when the five bars are off: A1–A4, G1–G2, Master,
 * Enlightened and Burned. Null for an item that has not been started.
 */
internal fun stageLabel(stage: Int?): String? = when {
    stage == null || stage <= 0 -> null
    stage <= 4 -> "A$stage"
    stage <= 6 -> "G${stage - 4}"
    stage == 7 -> "Master"
    stage == 8 -> "Enlightened"
    else -> "Burned"
}

/** The theme's colour for the item's SRS stage group. */
@Composable
private fun stageColor(stage: Int?): Color {
    val stages = LocalStageColors.current
    return when (StageGroup.of(stage)) {
        StageGroup.NOT_STARTED, StageGroup.APPRENTICE -> stages.apprentice
        StageGroup.GURU -> stages.guru
        StageGroup.MASTER -> stages.master
        StageGroup.ENLIGHTENED -> stages.enlightened
        StageGroup.BURNED -> stages.burned
    }
}

/** Five small segments under a tile, filled as the item climbs the SRS stages. */
@Composable
private fun StageBar(tile: LevelTile) {
    val filled = if (tile.unlocked) filledSegments(tile.stage) else 0
    val color = stageColor(tile.stage)
    Row(Modifier.padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (i in 0 until 5) {
            Box(
                Modifier.size(width = 8.dp, height = 3.dp).clip(RoundedCornerShape(1.dp))
                    .background(if (i < filled) color else MaterialTheme.colorScheme.outlineVariant),
            )
        }
    }
}

/** The card shown on long-press: the item, its reading and meaning, and when it is next reviewed. */
@Composable
private fun PeekCard(tile: LevelTile) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(tile.title, fontSize = 36.sp, style = MaterialTheme.typography.headlineMedium)
        tile.reading?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
        Text(tile.meaning, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(nextReviewText(tile, System.currentTimeMillis()), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun ForecastCard(days: List<DayForecast>, modifier: Modifier, onOpenStats: () -> Unit) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val zone = ZoneId.systemDefault()
    val dayFormat = DateTimeFormatter.ofPattern("EEE").withLocale(Locale.getDefault()).withZone(zone)
    val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault()).withZone(zone)
    Surface(modifier, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(vertical = 16.dp)) {
            Text(
                "Review forecast", style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.clickable(onClick = onOpenStats).padding(horizontal = 16.dp).padding(bottom = 8.dp),
            )
            if (days.isEmpty()) return@Column
            ScrollableTabRow(selectedTabIndex = selected.coerceIn(0, days.lastIndex), containerColor = Color.Transparent, edgePadding = 8.dp) {
                days.forEachIndexed { i, day ->
                    Tab(
                        selected = selected == i, onClick = { selected = i },
                        text = { Text(if (i == 0) "Today" else dayFormat.format(Instant.ofEpochMilli(day.dayStartMillis)), maxLines = 1, fontSize = 13.sp) },
                    )
                }
            }
            val day = days[selected.coerceIn(0, days.lastIndex)]
            val most = (day.rows.maxOfOrNull { it.count } ?: 1).coerceAtLeast(1)
            Column(Modifier.padding(horizontal = 16.dp).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (day.rows.isEmpty()) {
                    Text("No new reviews arrive this day.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                for (row in day.rows) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(timeFormat.format(Instant.ofEpochMilli(row.startMillis)), style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(72.dp))
                        Box(Modifier.weight(1f).height(14.dp)) {
                            Box(
                                Modifier.fillMaxHeight().fillMaxWidth(row.count.toFloat() / most).clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.primary),
                            )
                        }
                        Text("+${row.count}", style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.End, modifier = Modifier.width(44.dp))
                        Text(row.cumulative.toString(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, modifier = Modifier.width(44.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun StageTiles(breakdown: List<BreakdownRow>, modifier: Modifier, onOpen: (StageGroup) -> Unit) {
    val stages = LocalStageColors.current
    fun total(group: StageGroup) = breakdown.firstOrNull { it.group == group }?.total ?: 0
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StageTile("Apprentice", total(StageGroup.APPRENTICE), stages.apprentice, Modifier.weight(1f)) { onOpen(StageGroup.APPRENTICE) }
            StageTile("Guru", total(StageGroup.GURU), stages.guru, Modifier.weight(1f)) { onOpen(StageGroup.GURU) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StageTile("Master", total(StageGroup.MASTER), stages.master, Modifier.weight(1f)) { onOpen(StageGroup.MASTER) }
            StageTile("Enlightened", total(StageGroup.ENLIGHTENED), stages.enlightened, Modifier.weight(1f)) { onOpen(StageGroup.ENLIGHTENED) }
        }
        StageTile("Burned", total(StageGroup.BURNED), stages.burned, Modifier.fillMaxWidth()) { onOpen(StageGroup.BURNED) }
    }
}

@Composable
private fun StageTile(label: String, count: Int, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(color).clickable(onClick = onClick).padding(vertical = 14.dp)
            .semantics { contentDescription = "$label: $count. Show these items" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val fg = contentColorOn(color)
        Text(count.toString(), color = fg, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, color = fg, style = MaterialTheme.typography.labelMedium)
    }
}
