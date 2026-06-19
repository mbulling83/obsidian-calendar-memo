package com.boxmemo.app.vaultnotes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.boxmemo.app.hwr.HwrEngineType
import com.boxmemo.app.hwr.formatAsNoteLines
import com.boxmemo.app.memo.GuidelineStyle
import com.boxmemo.app.memo.InkFlushHandle
import com.boxmemo.app.memo.MemoCanvas
import com.boxmemo.app.memo.PenSettings
import com.boxmemo.app.memo.RecognitionOutcome
import com.boxmemo.app.memo.StrokeStore
import com.boxmemo.app.memo.StrokePath
import com.boxmemo.app.memo.recognizeStrokes
import com.boxmemo.app.memo.renderInlineMarkdown
import com.boxmemo.app.settings.HwrSettingsStore
import com.boxmemo.app.vault.VaultEntry
import com.boxmemo.app.vault.VaultFileIndex
import com.boxmemo.app.vault.VaultFileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Standalone screen for adding handwritten notes to any `.md` file in the vault
 * (not just the daily note). Two phases: a file picker (folder tree or filename
 * search), then an editor that splices converted handwriting into the chosen
 * file at a tapped line. Reuses the low-latency [MemoCanvas] and the shared
 * [recognizeStrokes] pipeline; file I/O goes through [VaultFileRepository].
 */
@Composable
fun VaultNotesScreen(
    fileIndex: VaultFileIndex,
    fileRepository: VaultFileRepository,
    strokeStore: StrokeStore,
    penSettings: PenSettings,
    onBack: () -> Unit,
) {
    var selectedFile by remember { mutableStateOf<File?>(null) }
    val context = LocalContext.current
    val recentStore = remember { RecentItemsStore(context) }
    val recentScope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        val file = selectedFile
        if (file == null) {
            FilePickerHeader(onBack = onBack)
            HorizontalDivider(thickness = 2.dp)
            FilePicker(
                fileIndex = fileIndex,
                recentStore = recentStore,
                onFileSelected = {
                    recentScope.launch { recentStore.addFile(it.absolutePath) }
                    selectedFile = it
                },
            )
        } else {
            EditorHeader(fileName = file.name, onBack = { selectedFile = null })
            HorizontalDivider(thickness = 2.dp)
            VaultFileEditor(
                file = file,
                fileRepository = fileRepository,
                strokeStore = strokeStore,
                penSettings = penSettings,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun FilePickerHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to calendar")
        }
        Text(
            text = "Vault notes",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun EditorHeader(fileName: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to file list")
        }
        Text(
            text = fileName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

private enum class PickerMode { TREE, SEARCH }

@Composable
private fun FilePicker(
    fileIndex: VaultFileIndex,
    recentStore: RecentItemsStore,
    onFileSelected: (File) -> Unit,
) {
    var mode by remember { mutableStateOf(PickerMode.TREE) }

    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
                selected = mode == PickerMode.TREE,
                onClick = { mode = PickerMode.TREE },
                label = { Text("Browse") },
            )
            FilterChip(
                selected = mode == PickerMode.SEARCH,
                onClick = { mode = PickerMode.SEARCH },
                label = { Text("Search") },
            )
        }

        when (mode) {
            PickerMode.TREE -> TreeView(fileIndex = fileIndex, onFileSelected = onFileSelected)
            PickerMode.SEARCH -> SearchView(
                fileIndex = fileIndex,
                recentStore = recentStore,
                onFileSelected = onFileSelected,
            )
        }
    }
}

@Composable
private fun TreeView(
    fileIndex: VaultFileIndex,
    onFileSelected: (File) -> Unit,
) {
    val root = remember { fileIndex.rootEntry() }
    if (root == null) {
        Text(
            text = "No vault configured. Set the vault path in Settings.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 12.dp),
        )
        return
    }
    Column(modifier = Modifier.fillMaxWidth().fillMaxHeight().verticalScroll(rememberScrollState())) {
        // The root's children are shown directly (no need to expand the root itself).
        FolderChildren(fileIndex = fileIndex, dir = root.file, depth = 0, onFileSelected = onFileSelected)
    }
}

@Composable
private fun FolderChildren(
    fileIndex: VaultFileIndex,
    dir: File,
    depth: Int,
    onFileSelected: (File) -> Unit,
) {
    val children = remember(dir) { fileIndex.childrenOf(dir) }
    children.forEach { entry ->
        when (entry) {
            is VaultEntry.Folder -> FolderRow(fileIndex, entry, depth, onFileSelected)
            is VaultEntry.MarkdownFile -> FileRow(entry, depth, onClick = { onFileSelected(entry.file) })
        }
    }
}

@Composable
private fun FolderRow(
    fileIndex: VaultFileIndex,
    folder: VaultEntry.Folder,
    depth: Int,
    onFileSelected: (File) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(start = (depth * 16).dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (expanded) "▾" else "▸",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(end = 6.dp),
        )
        Text(
            text = "📁 ${folder.name}",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
    if (expanded) {
        FolderChildren(fileIndex, folder.file, depth + 1, onFileSelected)
    }
}

@Composable
private fun FileRow(
    file: VaultEntry.MarkdownFile,
    depth: Int,
    onClick: () -> Unit,
) {
    Text(
        text = file.name.removeSuffix(".md"),
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(start = (depth * 16 + 22).dp, top = 6.dp, bottom = 6.dp),
    )
}

@Composable
private fun SearchView(
    fileIndex: VaultFileIndex,
    recentStore: RecentItemsStore,
    onFileSelected: (File) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<VaultEntry.MarkdownFile>>(emptyList()) }
    // The exact (trimmed) query that `results` reflects. The empty-state message
    // keys off this, not the live text, so "no matching notes" never flashes
    // while you're still typing — it appears only once a search has actually run
    // for what's in the box.
    var searchedQuery by remember { mutableStateOf<String?>(null) }

    val recentSearches by recentStore.recentSearches.collectAsState(initial = emptyList())
    val recentPaths by recentStore.recentFiles.collectAsState(initial = emptyList())
    // Drop any remembered files that have since been deleted/moved so we never
    // offer a dead link.
    val recentFiles = remember(recentPaths) {
        recentPaths.map(::File).filter { it.exists() }
    }

    val trimmed = query.trim()
    // Debounce so a large vault isn't walked on every keystroke; the delay is
    // cancelled and restarted whenever the query changes again.
    LaunchedEffect(trimmed) {
        if (trimmed.isEmpty()) {
            results = emptyList()
            searchedQuery = null
            return@LaunchedEffect
        }
        delay(250)
        results = withContext(Dispatchers.IO) { fileIndex.search(trimmed) }
        searchedQuery = trimmed
        // Only remember searches that actually matched something.
        if (results.isNotEmpty()) recentStore.addSearch(trimmed)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search filenames") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight().verticalScroll(rememberScrollState())) {
            if (trimmed.isEmpty()) {
                // Nothing typed yet: surface recent searches and recently opened
                // files as quick re-entry points.
                if (recentSearches.isNotEmpty()) {
                    RecentSectionHeader("Recent searches")
                    recentSearches.forEach { term ->
                        RecentRow(label = term, leading = "🔍", onClick = { query = term })
                    }
                }
                if (recentFiles.isNotEmpty()) {
                    RecentSectionHeader("Recent files")
                    recentFiles.forEach { file ->
                        RecentRow(
                            label = file.name.removeSuffix(".md"),
                            leading = "📄",
                            onClick = { onFileSelected(file) },
                        )
                    }
                }
                if (recentSearches.isEmpty() && recentFiles.isEmpty()) {
                    Text(
                        text = "Type to search filenames across the vault.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            } else {
                if (searchedQuery == trimmed && results.isEmpty()) {
                    Text(
                        text = "No matching notes.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                results.forEach { entry ->
                    FileRow(entry, depth = 0, onClick = { onFileSelected(entry.file) })
                }
            }
        }
    }
}

@Composable
private fun RecentSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 14.dp, bottom = 2.dp),
    )
}

@Composable
private fun RecentRow(label: String, leading: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = leading, modifier = Modifier.padding(end = 8.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun VaultFileEditor(
    file: File,
    fileRepository: VaultFileRepository,
    strokeStore: StrokeStore,
    penSettings: PenSettings,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val hwrSettingsStore = remember { HwrSettingsStore(context) }
    val engine by hwrSettingsStore.engine.collectAsState(initial = HwrEngineType.ONYX)

    val strokeKey = file.absolutePath
    val inkHandle = remember(strokeKey) { InkFlushHandle() }
    var version by remember(strokeKey) { mutableIntStateOf(0) }
    var guidelineStyle by remember { mutableStateOf(GuidelineStyle.None) }
    var showEraseAllConfirm by remember { mutableStateOf(false) }
    var statusMessage by remember(strokeKey) { mutableStateOf<String?>(null) }
    // Collapse the existing-note pane to hand its width to the writing canvas.
    var fileExpanded by remember(strokeKey) { mutableStateOf(true) }

    // File content, reloaded after each successful convert. `reloadKey` bumps to
    // force the LaunchedEffect to re-read from disk.
    var reloadKey by remember(strokeKey) { mutableIntStateOf(0) }
    var lines by remember(strokeKey) { mutableStateOf<List<String>>(emptyList()) }
    var insertIndex by remember(strokeKey) { mutableIntStateOf(0) }
    LaunchedEffect(strokeKey, reloadKey) {
        val content = withContext(Dispatchers.IO) { fileRepository.readFile(file) } ?: ""
        val loaded = content.split("\n")
        lines = loaded
        insertIndex = loaded.size // default insert point: end of file
    }

    @Suppress("UNUSED_EXPRESSION")
    version
    val strokes = strokeStore.strokesFor(strokeKey)

    fun flushStrokes(): List<StrokePath> {
        val current = inkHandle.flush()
        strokeStore.setStrokes(strokeKey, current)
        version++
        return current
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // True 50/50 default split, then stylus-resizable via the divider.
        val density = LocalDensity.current
        var leftWidthDp by remember(strokeKey, maxWidth) { mutableStateOf(maxWidth.value / 2f) }
        val minPaneDp = 120f
        val maxLeftDp = maxWidth.value - minPaneDp

        Row(modifier = Modifier.fillMaxSize()) {
            // The existing-note pane and its drag handle are hidden when collapsed,
            // so the canvas reclaims the full width.
            if (fileExpanded) {
                FileContentPane(
                    lines = lines,
                    insertIndex = insertIndex,
                    onInsertIndexChange = { insertIndex = it },
                    modifier = Modifier.width(leftWidthDp.dp).fillMaxHeight(),
                )
                // Vertical drag handle: drag right to grow the file pane, left to grow the canvas.
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(24.dp)
                        .pointerInput(strokeKey) {
                            detectHorizontalDragGestures { change, dragAmount ->
                                change.consume()
                                val deltaDp = with(density) { dragAmount.toDp().value }
                                leftWidthDp = (leftWidthDp + deltaDp).coerceIn(minPaneDp, maxLeftDp)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .fillMaxHeight(0.2f)
                            .background(MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp)),
                    )
                }
            }

            Column(modifier = Modifier.weight(1f).fillMaxHeight().padding(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilterChip(
                        selected = fileExpanded,
                        onClick = { fileExpanded = !fileExpanded },
                        label = { Text(if (fileExpanded) "Hide note" else "Show note") },
                    )

                    FilterChip(
                        selected = guidelineStyle != GuidelineStyle.None,
                        onClick = {
                            guidelineStyle = when (guidelineStyle) {
                                GuidelineStyle.None -> GuidelineStyle.Lines
                                GuidelineStyle.Lines -> GuidelineStyle.DotGrid
                                GuidelineStyle.DotGrid -> GuidelineStyle.None
                            }
                        },
                        label = {
                            Text(
                                when (guidelineStyle) {
                                    GuidelineStyle.None -> "Lines"
                                    GuidelineStyle.Lines -> "Lines ✓"
                                    GuidelineStyle.DotGrid -> "Dots ✓"
                                },
                            )
                        },
                    )

                    FilterChip(
                        selected = false,
                        onClick = {
                            coroutineScope.launch {
                                val current = flushStrokes().ifEmpty { strokes }
                                if (current.isEmpty()) {
                                    statusMessage = "Nothing to convert."
                                    return@launch
                                }
                                statusMessage = "Recognizing (${engine.label})…"
                                when (val outcome = recognizeStrokes(context, engine, current)) {
                                    is RecognitionOutcome.Unavailable -> statusMessage = outcome.message
                                    is RecognitionOutcome.Recognized -> {
                                        val text = outcome.value
                                        if (text.isNullOrBlank()) {
                                            statusMessage = "Nothing recognized."
                                        } else {
                                            val newLines = formatAsNoteLines(text)
                                            val wrote = withContext(Dispatchers.IO) {
                                                fileRepository.insertLinesAt(file, insertIndex, newLines)
                                            }
                                            if (wrote) {
                                                strokeStore.clear(strokeKey)
                                                version++
                                                reloadKey++
                                                statusMessage = "Converted and saved (${engine.label})."
                                            } else {
                                                statusMessage = "Converted, but couldn't write to the file."
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        label = { Text("Convert") },
                    )

                    AssistChip(
                        onClick = {
                            val current = flushStrokes()
                            if (current.isEmpty()) return@AssistChip
                            showEraseAllConfirm = true
                        },
                        label = { Text("Erase all") },
                    )
                }

                statusMessage?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 6.dp, top = 2.dp),
                    )
                }

                key(strokeKey, penSettings) {
                    MemoCanvas(
                        strokes = strokeStore.strokesFor(strokeKey),
                        penSettings = penSettings,
                        guidelineStyle = guidelineStyle,
                        isEraserActive = false,
                        onStrokeFinished = { stroke ->
                            strokeStore.addStroke(strokeKey, stroke)
                            version++
                        },
                        onStrokesErased = { remaining ->
                            strokeStore.setStrokes(strokeKey, remaining)
                            version++
                        },
                        modifier = Modifier.weight(1f),
                        flushHandle = inkHandle,
                    )
                }
            }
        }
    }

    if (showEraseAllConfirm) {
        AlertDialog(
            onDismissRequest = { showEraseAllConfirm = false },
            title = { Text("Erase all handwriting?") },
            text = {
                Text(
                    "This clears every stroke on this canvas. Anything you haven't " +
                        "converted to notes yet will be lost. This can't be undone.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        strokeStore.clear(strokeKey)
                        version++
                        showEraseAllConfirm = false
                    },
                ) { Text("Erase all") }
            },
            dismissButton = {
                TextButton(onClick = { showEraseAllConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

private val HEADING = Regex("""^(#{1,6})\s+(.*)$""")

/**
 * Number of leading lines that make up a YAML frontmatter block (the opening
 * `---`, its body, and the closing `---`), or 0 if the file has none. Only a
 * fence on the very first line counts, and an unterminated fence is treated as
 * no frontmatter so the whole file isn't hidden.
 */
private fun frontmatterEndIndex(lines: List<String>): Int {
    if (lines.isEmpty() || lines[0].trim() != "---") return 0
    for (i in 1 until lines.size) {
        if (lines[i].trim() == "---") return i + 1
    }
    return 0
}

/**
 * Renders the file's existing lines with tappable insert markers between them.
 * YAML frontmatter is hidden and `#` headings render as styled headers, but
 * insert indices still map to the *real* file lines so splicing stays correct.
 * The active marker (where converted bullets land) is drawn as a solid bar;
 * tapping a line selects the point just after it.
 */
@Composable
private fun FileContentPane(
    lines: List<String>,
    insertIndex: Int,
    onInsertIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bodyStart = remember(lines) { frontmatterEndIndex(lines) }
    Column(modifier = modifier.verticalScroll(rememberScrollState()).padding(horizontal = 8.dp)) {
        Text(
            text = if (insertIndex >= lines.size) "Insert: end of file" else "Insert: at marked line",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(vertical = 4.dp),
        )
        // Top marker inserts just after any frontmatter (index 0 when there's none).
        InsertMarker(active = insertIndex <= bodyStart, onClick = { onInsertIndexChange(bodyStart) })
        for (i in bodyStart until lines.size) {
            MarkdownLine(
                line = lines[i],
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onInsertIndexChange(i + 1) }
                    .padding(vertical = 2.dp),
            )
            InsertMarker(active = insertIndex == i + 1, onClick = { onInsertIndexChange(i + 1) })
        }
    }
}

/** A single file line: a styled header when it starts with `#`, otherwise inline markdown. */
@Composable
private fun MarkdownLine(line: String, modifier: Modifier = Modifier) {
    val heading = HEADING.matchEntire(line)
    if (heading != null) {
        val level = heading.groupValues[1].length
        val style = when (level) {
            1 -> MaterialTheme.typography.titleLarge
            2 -> MaterialTheme.typography.titleMedium
            3 -> MaterialTheme.typography.titleSmall
            else -> MaterialTheme.typography.bodyLarge
        }
        Text(
            text = renderInlineMarkdown(heading.groupValues[2]),
            style = style,
            fontWeight = FontWeight.Bold,
            modifier = modifier,
        )
    } else {
        Text(
            text = renderInlineMarkdown(line),
            style = MaterialTheme.typography.bodyMedium,
            modifier = modifier,
        )
    }
}

@Composable
private fun InsertMarker(active: Boolean, onClick: () -> Unit) {
    // Inactive markers are a thin, tappable gap; the active marker shows a solid
    // 2dp bar plus a label, which reads clearly on e-ink without relying on
    // faint greys.
    if (active) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(2.dp)
                    .background(MaterialTheme.colorScheme.onSurface, RoundedCornerShape(1.dp)),
            )
            Text(
                text = " ↳ insert here",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .height(8.dp),
        )
    }
}
