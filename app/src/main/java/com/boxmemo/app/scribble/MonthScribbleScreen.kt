package com.boxmemo.app.scribble

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.boxmemo.app.hwr.HwrEngineType
import com.boxmemo.app.memo.GuidelineStyle
import com.boxmemo.app.memo.InkFlushHandle
import com.boxmemo.app.memo.MemoCanvas
import com.boxmemo.app.memo.PenSettings
import com.boxmemo.app.memo.StrokePath
import com.boxmemo.app.settings.HwrSettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val MONTH_LABEL = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
private val HIT_DAY_LABEL = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH)
private const val AUTOSAVE_DELAY_MS = 800L

/**
 * Standalone month-by-month scribble calendar. A single freeform ink layer is
 * drawn over the month grid (rendered into the ink surface itself, see
 * [drawMonthGrid]), like writing on a paper wall planner. Not linked to
 * Obsidian — strokes persist on-device per month via [MonthScribbleStore] and
 * remain editable on reopen.
 *
 * Opens on the current month; prev/next/today navigate. Each month's ink is its
 * own stroke set: the [MemoCanvas] is keyed by month so switching recreates the
 * surface with the target month's strokes.
 *
 * The handwriting is also made searchable without altering it: a [ScribbleIndexer]
 * recognises each month into a text sidecar at the same flush moments the ink is
 * saved (plus a one-time backfill on open). The search bar finds a word across
 * months and jumps to the matching day, highlighting its cell.
 */
@Composable
fun MonthScribbleScreen(
    store: MonthScribbleStore,
    penSettings: PenSettings,
    hwrSettingsStore: HwrSettingsStore,
    onBack: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val today = remember { LocalDate.now() }

    var month by remember { mutableStateOf(YearMonth.now()) }
    var isEraser by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }

    // Which day cell to highlight, set when a search result jumps here; cleared
    // by writing or navigating away.
    var highlightDay by remember { mutableStateOf<LocalDate?>(null) }

    // Search state.
    val engine by hwrSettingsStore.engine.collectAsState(initial = HwrEngineType.ONYX)
    val engineState = rememberUpdatedState(engine)
    val indexer = remember(store) {
        ScribbleIndexer(
            context = context,
            store = store,
            baseDir = store.baseDir,
            density = context.resources.displayMetrics.density,
            engine = { engineState.value },
        )
    }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<ScribbleHit>>(emptyList()) }
    var searched by remember { mutableStateOf(false) }

    // Strokes for the displayed month, scaled to the live canvas. Driven into
    // MemoCanvas; its callbacks write back here.
    var strokes by remember { mutableStateOf<List<StrokePath>>(emptyList()) }
    var canvasW by remember { mutableIntStateOf(0) }
    var canvasH by remember { mutableIntStateOf(0) }
    val inkHandle = remember(month) { InkFlushHandle() }

    // Debounced autosave: bumped on every stroke/erase.
    var saveTick by remember { mutableIntStateOf(0) }

    // saveCurrent() is closed over by navigation/dispose; keep its captures fresh.
    val state = rememberUpdatedState(
        Triple(month, canvasW to canvasH, strokes),
    )
    // [flush] toggles the pen's raw-drawing layer to drain any buffered stroke,
    // which also wipes the on-screen ink (the surface doesn't repaint after a
    // flush). So only flush when we're leaving the month anyway — the periodic
    // autosave persists the `strokes` state directly (every completed stroke is
    // already delivered there via onStrokeFinished), leaving the canvas intact.
    fun saveCurrent(flush: Boolean) {
        val (m, dims, currentState) = state.value
        val (w, h) = dims
        if (w <= 0 || h <= 0) return
        val current = if (flush) inkHandle.flush().ifEmpty { currentState } else currentState
        coroutineScope.launch(Dispatchers.IO) {
            store.save(MonthScribble(m, w, h, current))
            // Refresh the search index for the month we're leaving. (A backfill on
            // open is the safety net if this is cut short by the screen closing.)
            if (flush) indexer.reindexMonth(m)
        }
    }

    fun goTo(target: YearMonth) {
        if (target == month) return
        saveCurrent(flush = true)
        isEraser = false
        highlightDay = null
        month = target
    }

    // Jump to a search hit: save the current month, switch, and highlight the day.
    fun jumpTo(hit: ScribbleHit) {
        if (hit.month != month) {
            saveCurrent(flush = true)
            isEraser = false
            month = hit.month
        }
        highlightDay = hit.day
        query = ""
        results = emptyList()
        searched = false
    }

    fun runSearch() {
        val q = query
        coroutineScope.launch {
            val hits = withContext(Dispatchers.IO) { searchIndex(indexer.loadAllIndexes(), q) }
            results = hits
            searched = true
        }
    }

    // One-time backfill: index any month whose ink predates the search feature
    // (or whose index didn't finish on a previous exit).
    LaunchedEffect(Unit) {
        indexer.backfill()
    }

    LaunchedEffect(saveTick) {
        if (saveTick == 0) return@LaunchedEffect
        delay(AUTOSAVE_DELAY_MS)
        saveCurrent(flush = false)
    }

    // Save the month currently on screen when leaving the screen.
    DisposableEffect(Unit) {
        onDispose { saveCurrent(flush = true) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to calendar")
            }
            IconButton(onClick = { goTo(month.minusMonths(1)) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
            }
            Text(
                text = month.format(MONTH_LABEL),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            IconButton(onClick = { goTo(month.plusMonths(1)) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
            }
            IconButton(onClick = { goTo(YearMonth.now()) }) {
                Icon(Icons.Filled.DateRange, contentDescription = "This month")
            }
            FilterChip(
                selected = isEraser,
                onClick = { isEraser = !isEraser },
                label = { Text(if (isEraser) "Erase ✓" else "Erase") },
            )
            AssistChip(
                // Check the strokes state, not inkHandle.flush(): a flush would
                // wipe the visible ink, which is wrong if the user then cancels.
                onClick = { if (strokes.isNotEmpty()) showClearConfirm = true },
                label = { Text("Clear month") },
            )
        }
        // Search bar: find handwritten words across months. Submit (not every
        // keystroke) triggers the search, since e-ink redraws are costly.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("Search handwriting") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty() || searched) {
                        IconButton(onClick = {
                            query = ""
                            results = emptyList()
                            searched = false
                        }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { runSearch() }),
            )
            TextButton(onClick = { runSearch() }, enabled = query.isNotBlank()) {
                Text("Search")
            }
        }

        if (searched) {
            ScribbleSearchResults(
                results = results,
                onSelect = { jumpTo(it) },
            )
        }

        HorizontalDivider(thickness = 2.dp)

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val w = with(density) { maxWidth.toPx() }.toInt()
            val h = with(density) { maxHeight.toPx() }.toInt()
            canvasW = w
            canvasH = h
            val densityValue = density.density

            // Load (and rescale) this month's ink whenever the month or the
            // canvas size changes.
            LaunchedEffect(month, w, h) {
                if (w <= 0 || h <= 0) return@LaunchedEffect
                val loaded = withContext(Dispatchers.IO) { store.load(month) }
                strokes = if (loaded == null) {
                    emptyList()
                } else {
                    scaleStrokes(loaded.strokes, loaded.captureWidth, loaded.captureHeight, w, h)
                }
            }

            // key(month): a new month means a fresh surface seeded with that
            // month's strokes. penSettings change also recreates, matching the
            // other screens.
            // key includes highlightDay so a search jump (or clearing the
            // highlight by writing) repaints the background grid; the surface is
            // reseeded with the current strokes, so the ink is preserved.
            androidx.compose.runtime.key(month, penSettings, highlightDay) {
                MemoCanvas(
                    strokes = strokes,
                    penSettings = penSettings,
                    guidelineStyle = GuidelineStyle.None, // the grid is the guide
                    isEraserActive = isEraser,
                    onStrokeFinished = { stroke ->
                        strokes = strokes + listOf(stroke)
                        highlightDay = null
                        saveTick++
                    },
                    onStrokesErased = { remaining ->
                        strokes = remaining
                        saveTick++
                    },
                    modifier = Modifier.fillMaxSize(),
                    flushHandle = inkHandle,
                    backgroundRenderer = { canvas, cw, ch ->
                        drawMonthGrid(canvas, cw, ch, month, today, densityValue, highlightDay)
                    },
                )
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear this month's scribbles?") },
            text = {
                Text(
                    "This removes every stroke on ${month.format(MONTH_LABEL)}. " +
                        "This can't be undone.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        strokes = emptyList()
                        saveTick++
                        showClearConfirm = false
                    },
                ) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

/**
 * The list of search hits, newest-first. Each row shows the day (or the month
 * for a month-level hit) and the matched snippet; tapping jumps to it.
 */
@Composable
private fun ScribbleSearchResults(
    results: List<ScribbleHit>,
    onSelect: (ScribbleHit) -> Unit,
) {
    if (results.isEmpty()) {
        Text(
            text = "No handwriting found.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        )
        return
    }
    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
        items(results) { hit ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(hit) },
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    val label = hit.day?.format(HIT_DAY_LABEL) ?: hit.month.format(MONTH_LABEL)
                    Text(text = label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = hit.snippet,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                    )
                }
            }
            HorizontalDivider(thickness = 1.dp)
        }
    }
}
