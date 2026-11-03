package com.example.durtlesreborn.reading.ui

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.durtlesreborn.reading.core.SubjectLite

/**
 * Web fallback: the original page in a WebView. A script walks its text nodes, asks Kotlin to
 * tokenize each one (the text never leaves the device), and wraps matched words in coloured spans.
 * Tapping a word shows the same card as the reader. An "Aa" button slides up text settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingWebScreen(
    url: String,
    controller: ReadingController,
    onBack: () -> Unit,
    onOpenItem: (Long) -> Unit,
) {
    var tapped by remember { mutableStateOf<SubjectLite?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    val fontZoom = controller.webFontZoom
    val lineWidth = controller.webLineWidth
    val latest = rememberUpdatedState(fontZoom to lineWidth)

    BackHandler {
        // Back closes the text settings first, then returns to the native reader; the library is
        // one step further back.
        if (showSettings) showSettings = false else onBack()
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Reader") }
            Text(url, style = MaterialTheme.typography.labelSmall, maxLines = 1, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
            TextButton(
                onClick = { showSettings = true },
                modifier = Modifier.semantics { contentDescription = "Text settings" },
            ) { Text("Aa") }
        }
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = false
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    addJavascriptInterface(
                        WebBridge(controller) { id ->
                            Handler(Looper.getMainLooper()).post { tapped = controller.subject(id) }
                        },
                        "Durtles",
                    )
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest,
                        ): Boolean = !request.url.toString().isWebUrl()

                        override fun onPageFinished(view: WebView, finishedUrl: String) {
                            val (zoom, width) = latest.value
                            view.applyReaderStyle(zoom, width)
                            view.evaluateJavascript(INJECT_SCRIPT, null)
                        }
                    }
                    if (url.isWebUrl()) loadUrl(url)
                }
            },
            update = { it.applyReaderStyle(fontZoom, lineWidth) },
            onRelease = { it.destroy() },
        )
    }

    if (showSettings) {
        ModalBottomSheet(
            onDismissRequest = { showSettings = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Text settings", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        controller.webFontZoom = DEFAULT_ZOOM
                        controller.webLineWidth = DEFAULT_WIDTH
                    }) { Text("Reset") }
                    TextButton(onClick = { showSettings = false }) { Text("Done") }
                }
                ReaderSettingRow(
                    label = "Font size",
                    value = "$fontZoom%",
                    canDecrease = fontZoom > MIN_ZOOM,
                    canIncrease = fontZoom < MAX_ZOOM,
                    onDecrease = { controller.webFontZoom = (fontZoom - ZOOM_STEP).coerceAtLeast(MIN_ZOOM) },
                    onIncrease = { controller.webFontZoom = (fontZoom + ZOOM_STEP).coerceAtMost(MAX_ZOOM) },
                )
                ReaderSettingRow(
                    label = "Line width",
                    value = "$lineWidth characters",
                    canDecrease = lineWidth > MIN_WIDTH,
                    canIncrease = lineWidth < MAX_WIDTH,
                    onDecrease = { controller.webLineWidth = (lineWidth - WIDTH_STEP).coerceAtLeast(MIN_WIDTH) },
                    onIncrease = { controller.webLineWidth = (lineWidth + WIDTH_STEP).coerceAtMost(MAX_WIDTH) },
                )
                Text(
                    "Text zoom and line width apply to this web view. The native reader uses the app's text settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    tapped?.let { subject ->
        val stage = controller.stageOf(subject.id)
        AlertDialog(
            onDismissRequest = { tapped = null },
            title = {
                Column {
                    Text(subject.characters.orEmpty(), style = MaterialTheme.typography.headlineSmall)
                    Text(stageLabel(stage), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (subject.readings.isNotEmpty()) Text(subject.readings.joinToString("、"), style = MaterialTheme.typography.bodyLarge)
                    Text(subject.meanings.joinToString(", "), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Level ${subject.level} · ${subjectTypeLabel(subject.type)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val id = subject.id.toLong()
                    tapped = null
                    onOpenItem(id)
                }) { Text("Open item") }
            },
            dismissButton = { TextButton(onClick = { tapped = null }) { Text("Close") } },
        )
    }
}

/** One labelled − / + control in the text-settings sheet. */
@Composable
private fun ReaderSettingRow(
    label: String,
    value: String,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedButton(
            onClick = onDecrease,
            enabled = canDecrease,
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.size(44.dp),
        ) { Text("−", style = MaterialTheme.typography.titleMedium) }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(
            onClick = onIncrease,
            enabled = canIncrease,
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.size(44.dp),
        ) { Text("+", style = MaterialTheme.typography.titleMedium) }
    }
}

/** Applies the text zoom and a readable max line width to whatever page is loaded. */
private fun WebView.applyReaderStyle(fontZoom: Int, lineWidth: Int) {
    settings.textZoom = fontZoom
    val script = """
        (function() {
          var id = 'durtles-reader-style';
          var style = document.getElementById(id);
          if (!style) {
            style = document.createElement('style');
            style.id = id;
            (document.head || document.documentElement).appendChild(style);
          }
          style.textContent = 'body{max-width:${lineWidth}ch !important;margin-left:auto !important;margin-right:auto !important;padding-left:12px !important;padding-right:12px !important;}';
        })();
    """.trimIndent()
    evaluateJavascript(script, null)
}

private fun String.isWebUrl(): Boolean = startsWith("https://") || startsWith("http://")

private const val DEFAULT_ZOOM = 100
private const val MIN_ZOOM = 70
private const val MAX_ZOOM = 160
private const val ZOOM_STEP = 10
private const val DEFAULT_WIDTH = 40
private const val MIN_WIDTH = 28
private const val MAX_WIDTH = 56
private const val WIDTH_STEP = 4

private class WebBridge(
    private val controller: ReadingController,
    private val onTap: (Int) -> Unit,
) {
    @JavascriptInterface
    fun analyze(text: String): String = controller.webSpans(text, HighlightMode.ALL)

    @JavascriptInterface
    fun tap(subjectId: String) {
        subjectId.toIntOrNull()?.let(onTap)
    }
}

private const val INJECT_SCRIPT = """
(function() {
  if (window.__durtlesWalked) return;
  window.__durtlesWalked = true;
  var isJapanese = function(t) { return /[\u3040-\u30ff\u4e00-\u9fff]/.test(t); };
  var walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null);
  var nodes = [];
  while (walker.nextNode()) {
    var n = walker.currentNode;
    var parent = n.parentElement;
    if (!parent) continue;
    if (parent.closest('script,style,noscript')) continue;
    if (!isJapanese(n.textContent)) continue;
    nodes.push(n);
  }
  for (var i = 0; i < nodes.length; i++) {
    var node = nodes[i];
    var text = node.textContent;
    var spans;
    try { spans = JSON.parse(window.Durtles.analyze(text)); } catch (e) { continue; }
    if (!spans || !spans.length) continue;
    spans.sort(function(a, b) { return a.s - b.s; });
    var fragment = document.createDocumentFragment();
    var pos = 0;
    for (var j = 0; j < spans.length; j++) {
      var sp = spans[j];
      if (sp.s < pos) continue;
      if (sp.s > pos) fragment.appendChild(document.createTextNode(text.slice(pos, sp.s)));
      var span = document.createElement('span');
      span.textContent = text.slice(sp.s, sp.e);
      span.style.backgroundColor = sp.c;
      span.style.borderRadius = '3px';
      span.style.cursor = 'pointer';
      span.dataset.sid = sp.sid;
      fragment.appendChild(span);
      pos = sp.e;
    }
    if (pos < text.length) fragment.appendChild(document.createTextNode(text.slice(pos)));
    if (node.parentNode) node.parentNode.replaceChild(fragment, node);
  }
  document.addEventListener('click', function(e) {
    var el = e.target && e.target.closest ? e.target.closest('span[data-sid]') : null;
    if (!el) return;
    e.preventDefault();
    e.stopPropagation();
    window.Durtles.tap(el.dataset.sid);
  }, true);
})();
"""
