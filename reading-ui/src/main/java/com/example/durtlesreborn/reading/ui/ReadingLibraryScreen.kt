package com.example.durtlesreborn.reading.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.durtlesreborn.reading.store.ArticleSummary

private enum class Sort { RECENT, KNOWN, READY }

/**
 * The library: scored articles from the configured sources. Reading data never leaves the device;
 * failures show a short message instead of breaking the screen.
 */
@Composable
fun ReadingLibraryScreen(
    state: LibraryState,
    onRefresh: () -> Unit,
    onOpenArticle: (Long) -> Unit,
    onClose: () -> Unit,
    onAddSource: (String, String) -> Unit,
    onRemoveSource: (Long) -> Unit,
    onClearAll: () -> Unit,
) {
    var sort by remember { mutableStateOf(Sort.RECENT) }
    var minKnown by remember { mutableStateOf(0) }
    var showAdd by remember { mutableStateOf(false) }
    var showSources by remember { mutableStateOf(false) }

    val articles = remember(state.articles, sort, minKnown) {
        state.articles
            .filter { it.knownPercent >= minKnown }
            .let {
                when (sort) {
                    Sort.RECENT -> it.sortedByDescending { a -> a.fetchedAt }
                    Sort.KNOWN -> it.sortedByDescending { a -> a.knownPercent }
                    Sort.READY -> it.sortedWith(compareBy(nullsLast()) { a -> a.readyLevel })
                }
            }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onClose) { Text("Back") }
            Text("Graded Reading", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(start = 8.dp))
            TextButton(onClick = onRefresh) { Text("Refresh") }
        }
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 6.dp))
        if (state.message != null) {
            Text(state.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(onClick = { showSources = true }) { Text("Sources (${state.sources.size})") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { showAdd = true }) { Text("Add source") }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(sort == Sort.RECENT, { sort = Sort.RECENT }, { Text("Recent") })
            FilterChip(sort == Sort.KNOWN, { sort = Sort.KNOWN }, { Text("Known %") })
            FilterChip(sort == Sort.READY, { sort = Sort.READY }, { Text("Ready level") })
        }
        Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(minKnown == 0, { minKnown = 0 }, { Text("Any") })
            FilterChip(minKnown == 80, { minKnown = 80 }, { Text("80%+") })
            FilterChip(minKnown == 95, { minKnown = 95 }, { Text("95%+") })
        }

        if (articles.isEmpty() && !state.loading) {
            Text(
                "No articles yet. Pull in a source with Refresh.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 24.dp),
            )
        }

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(articles, key = { it.id }) { article ->
                ArticleRow(article, onOpen = { onOpenArticle(article.id) })
            }
        }
    }

    if (showAdd) {
        AddSourceDialog(
            onDismiss = { showAdd = false },
            onAdd = { name, url -> onAddSource(name, url); showAdd = false },
        )
    }
    if (showSources) {
        SourcesDialog(
            state = state,
            onDismiss = { showSources = false },
            onRemove = onRemoveSource,
            onClearAll = { onClearAll(); showSources = false },
        )
    }
}

@Composable
private fun ArticleRow(article: ArticleSummary, onOpen: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(article.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(article.sourceName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "${article.knownPercent}% known · ${article.uncoveredPercent}% not in WaniKani · ${article.knownKanjiPercent}% kanji",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                article.readyLevel?.let { "Ready at level $it" } ?: "No WaniKani words yet",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun AddSourceDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a source") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Paste an RSS or Atom feed, or an index page URL.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(url, { url = it }, label = { Text("URL") }, singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(name, url) }, enabled = name.isNotBlank() && url.startsWith("http")) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SourcesDialog(
    state: LibraryState,
    onDismiss: () -> Unit,
    onRemove: (Long) -> Unit,
    onClearAll: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sources and cache") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (source in state.sources) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(source.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { onRemove(source.id) }) { Text("Remove") }
                    }
                }
                Text(
                    "Articles and example sentences are kept for 30 days (up to 50 MB) and only on this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onClearAll) { Text("Clear reading data") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
