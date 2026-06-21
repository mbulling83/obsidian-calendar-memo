package com.boxmemo.app.scribble

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.boxmemo.app.memo.GuidelineStyle
import com.boxmemo.app.memo.InkFlushHandle
import com.boxmemo.app.memo.MemoCanvas
import com.boxmemo.app.memo.PenSettings
import com.boxmemo.app.memo.StrokePath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val MONTH_LABEL = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
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
 */
@Composable
fun MonthScribbleScreen(
    store: MonthScribbleStore,
    penSettings: PenSettings,
    onBack: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val today = remember { LocalDate.now() }

    var month by remember { mutableStateOf(YearMonth.now()) }
    var isEraser by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }

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
        }
    }

    fun goTo(target: YearMonth) {
        if (target == month) return
        saveCurrent(flush = true)
        isEraser = false
        month = target
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
            androidx.compose.runtime.key(month, penSettings) {
                MemoCanvas(
                    strokes = strokes,
                    penSettings = penSettings,
                    guidelineStyle = GuidelineStyle.None, // the grid is the guide
                    isEraserActive = isEraser,
                    onStrokeFinished = { stroke ->
                        strokes = strokes + listOf(stroke)
                        saveTick++
                    },
                    onStrokesErased = { remaining ->
                        strokes = remaining
                        saveTick++
                    },
                    modifier = Modifier.fillMaxSize(),
                    flushHandle = inkHandle,
                    backgroundRenderer = { canvas, cw, ch ->
                        drawMonthGrid(canvas, cw, ch, month, today, densityValue)
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
