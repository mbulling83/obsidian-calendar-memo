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
import androidx.compose.ui.focus.onFocusChanged
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.boxmemo.app.settings.HwrSettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

    // Recomputed on resume: the screen can sit for days on an always-on e-ink
    // device, and a one-shot remember would leave the marker on the wrong day.
    var today by remember { mutableStateOf(LocalDate.now()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) today = LocalDate.now()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
    // While the search field holds focus its handwriting keyboard is up; suspend
    // the ink surface's pen capture so keystrokes aren't grabbed as stray ink.
    var searchFocused by remember { mutableStateOf(false) }

    // Strokes for the displayed month, scaled to the live canvas. Driven into
    // MemoCanvas; its callbacks write back here.
    var strokes by remember { mutableStateOf<List<StrokePath>>(emptyList()) }
    var canvasW by remember { mutableIntStateOf(0) }
    var canvasH by remember { mutableIntStateOf(0) }
    // The canvas size the in-memory strokes are currently expressed in — set
    // when a month's ink loads or when a canvas resize transforms the strokes.
    var strokesW by remember { mutableIntStateOf(0) }
    var strokesH by remember { mutableIntStateOf(0) }
    // True while a month's ink is loading from disk. Saves are suppressed then:
    // a save mid-load would write the previous month's (or empty) strokes into
    // the newly displayed month's file.
    var loading by remember { mutableStateOf(true) }
    val inkHandle = remember(month) { InkFlushHandle() }

    // Debounced autosave: bumped on every stroke/erase.
    var saveTick by remember { mutableIntStateOf(0) }

    // Survives the composition: the final save on dispose must not die with
    // rememberCoroutineScope, which is cancelled right after onDispose runs.
    val exitScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.IO) }

    // [flush] toggles the pen's raw-drawing layer to drain any buffered stroke,
    // which also wipes the on-screen ink (the surface doesn't repaint after a
    // flush). So only flush when we're leaving the month anyway — the periodic
    // autosave persists the `strokes` state directly (every completed stroke is
    // already delivered there via onStrokeFinished), leaving the canvas intact.
    // [expectedMonth] guards a stale debounced autosave: if the displayed month
    // has changed since the save was scheduled, the save is aborted rather than
    // written into the wrong month's file.
    fun saveCurrent(
        flush: Boolean,
        expectedMonth: YearMonth? = null,
        scope: CoroutineScope = coroutineScope,
    ) {
        if (loading) return
        val m = month
        if (expectedMonth != null && m != expectedMonth) return
        // Save at the size the strokes are actually expressed in, which can lag
        // the live canvas by one transform pass.
        val w = if (strokesW > 0) strokesW else canvasW
        val h = if (strokesH > 0) strokesH else canvasH
        if (w <= 0 || h <= 0) return
        val current = if (flush) inkHandle.flush().ifEmpty { strokes } else strokes
        scope.launch(Dispatchers.IO) {
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
        saveTick = 0 // any pending autosave belonged to the month we just saved
        // Set eagerly (the load effect sets it too): no save may run between
        // the switch and the new month's ink arriving, or the old month's
        // strokes would land in the new month's file.
        loading = true
        month = target
    }

    // Jump to a search hit: save the current month, switch, and highlight the day.
    fun jumpTo(hit: ScribbleHit) {
        if (hit.month != month) {
            saveCurrent(flush = true)
            isEraser = false
            saveTick = 0
            loading = true // see goTo: no save until the new month's ink loads
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

    // Keyed on month as well as the tick so a month switch cancels any pending
    // autosave (goTo flush-saves the old month itself); the captured month is
    // double-checked in saveCurrent in case the switch races the delay.
    LaunchedEffect(saveTick, month) {
        if (saveTick == 0) return@LaunchedEffect
        val target = month
        delay(AUTOSAVE_DELAY_MS)
        saveCurrent(flush = false, expectedMonth = target)
    }

    // Save the month currently on screen when leaving the screen. The stroke
    // flush runs synchronously inside saveCurrent; the write itself goes to
    // exitScope, which outlives the composition (rememberCoroutineScope is
    // cancelled straight after dispose and would drop the write).
    DisposableEffect(Unit) {
        onDispose { saveCurrent(flush = true, scope = exitScope) }
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
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { searchFocused = it.isFocused },
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

            // Load this month's ink whenever the month changes. Keyed on the
            // month ONLY: a canvas resize (e.g. the search results panel
            // opening) must not reload from disk — that would drop strokes the
            // debounced autosave hasn't written yet. Resizes are handled by the
            // in-memory transform effect below.
            LaunchedEffect(month) {
                loading = true
                val loaded = withContext(Dispatchers.IO) { store.load(month) }
                val targetW = canvasW
                val targetH = canvasH
                if (loaded == null) {
                    strokes = emptyList()
                    strokesW = targetW
                    strokesH = targetH
                } else if (targetW > 0 && targetH > 0) {
                    strokes = transformStrokesForGrid(
                        month, loaded.strokes,
                        loaded.captureWidth, loaded.captureHeight,
                        targetW, targetH, densityValue,
                    )
                    strokesW = targetW
                    strokesH = targetH
                } else {
                    // Canvas not measured yet: keep the ink at its stored size;
                    // the transform effect below rescales once dims arrive.
                    strokes = loaded.strokes
                    strokesW = loaded.captureWidth
                    strokesH = loaded.captureHeight
                }
                loading = false
            }

            // A pure canvas-size change transforms the in-memory strokes to the
            // new size — grid-aware, so ink stays in the day cell it was
            // written in (the header band is fixed-height and cells are
            // min-clamped square, so a plain linear rescale would drift ink
            // across cell boundaries).
            LaunchedEffect(w, h) {
                if (loading || w <= 0 || h <= 0) return@LaunchedEffect
                if (strokesW == w && strokesH == h) return@LaunchedEffect
                if (strokesW > 0 && strokesH > 0) {
                    strokes = transformStrokesForGrid(month, strokes, strokesW, strokesH, w, h, densityValue)
                }
                strokesW = w
                strokesH = h
            }

            // key(month): a new month means a fresh surface seeded with that
            // month's strokes. penSettings change also recreates, matching the
            // other screens.
            // key includes highlightDay so a search jump (or clearing the
            // highlight by writing) repaints the background grid; the surface is
            // reseeded with the current strokes, so the ink is preserved. today
            // is keyed too so the marker moves when the date rolls over on resume.
            androidx.compose.runtime.key(month, penSettings, highlightDay, today) {
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
                    penCaptureSuspended = searchFocused,
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
