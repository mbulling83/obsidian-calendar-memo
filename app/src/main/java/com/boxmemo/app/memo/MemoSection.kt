package com.boxmemo.app.memo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.boxmemo.app.calendar.DayEvent
import java.time.LocalDate

private val WIKI_LINK = Regex("""\[\[([^\]]+)]]""")

private fun renderWikiLinks(text: String): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    for (match in WIKI_LINK.findAll(text)) {
        append(text, cursor, match.range.first)
        pushStyle(SpanStyle(textDecoration = TextDecoration.Underline))
        append(match.groupValues[1].substringAfter('|'))
        pop()
        cursor = match.range.last + 1
    }
    append(text, cursor, text.length)
}

// Inline spans, in precedence order: wiki links, bold, strikethrough, inline
// code, then italic. Bold (`**`/`__`) is matched before italic (`*`/`_`) so a
// double marker isn't mistaken for two single ones. Each match contributes one
// non-nested style — adequate for the bullet detail lines shown here.
private val INLINE_MD = Regex(
    """\[\[([^\]]+)]]""" +      // 1: [[wiki link]] / [[target|alias]]
        """|\*\*([^*]+)\*\*""" + // 2: **bold**
        """|__([^_]+)__""" +     // 3: __bold__
        """|~~([^~]+)~~""" +     // 4: ~~strikethrough~~
        """|`([^`]+)`""" +       // 5: `code`
        """|\*([^*]+)\*""" +     // 6: *italic*
        """|_([^_]+)_""",        // 7: _italic_
)

/**
 * Renders the common inline Markdown found in daily-note bullets — bold,
 * italic, strikethrough, inline code and `[[wiki links]]` — into a styled
 * [AnnotatedString], with the markers themselves hidden. Anything unmatched is
 * passed through verbatim.
 */
private fun renderInlineMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    for (match in INLINE_MD.findAll(text)) {
        if (match.range.first < cursor) continue // overlaps a span already emitted
        append(text, cursor, match.range.first)

        val token = match.value
        when {
            token.startsWith("[[") -> {
                pushStyle(SpanStyle(textDecoration = TextDecoration.Underline))
                append(match.groupValues[1].substringAfter('|'))
            }
            token.startsWith("**") -> {
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                append(match.groupValues[2])
            }
            token.startsWith("__") -> {
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                append(match.groupValues[3])
            }
            token.startsWith("~~") -> {
                pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                append(match.groupValues[4])
            }
            token.startsWith("`") -> {
                pushStyle(SpanStyle(fontFamily = FontFamily.Monospace))
                append(match.groupValues[5])
            }
            token.startsWith("*") -> {
                pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                append(match.groupValues[6])
            }
            else -> { // _italic_
                pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                append(match.groupValues[7])
            }
        }
        pop()
        cursor = match.range.last + 1
    }
    append(text, cursor, text.length)
}

private data class BulletLine(val depth: Int, val text: String)

private fun parseBulletLine(rawLine: String): BulletLine {
    var i = 0
    var depth = 0
    var spaceRun = 0
    while (i < rawLine.length) {
        when (rawLine[i]) {
            '\t' -> { depth += 1 + spaceRun / 4; spaceRun = 0; i++ }
            ' ' -> { spaceRun++; i++ }
            else -> { depth += spaceRun / 4; break }
        }
    }
    val rest = rawLine.substring(i)
    val text = if (rest.startsWith("- ")) rest.substring(2) else rest
    return BulletLine(maxOf(0, depth - 1), text)
}

// Stacked "Already noted" scroll-area height (dp): a useful default, dragged
// between a couple of lines and most of the screen via the resize handle.
private const val DEFAULT_NOTED_HEIGHT_DP = 320f
private const val MIN_NOTED_HEIGHT_DP = 64f
private const val MAX_NOTED_HEIGHT_DP = 800f

/**
 * Handwriting canvas for the currently selected scope (a meeting or Notes).
 * Scope selection is driven externally via the agenda panel. The toolbar row
 * hosts scope label, guideline picker, any injected toolbar actions (e.g.
 * Convert), and the Erase-all action — nothing below the canvas so the full
 * remaining height is available for writing.
 */
@Composable
fun MemoSection(
    date: LocalDate,
    selectedScope: CaptureScope,
    meetings: List<DayEvent.ObsidianMeeting>,
    noteLines: List<String>,
    strokeStore: StrokeStore,
    penSettings: PenSettings,
    modifier: Modifier = Modifier,
    toolbarContent: @Composable RowScope.(
        scope: CaptureScope,
        strokes: List<StrokePath>,
        flushStrokes: () -> List<StrokePath>,
        requestClear: () -> Unit,
    ) -> Unit = { _, _, _, _ -> },
) {
    // Bridges Convert/Erase to the live ink surface so they flush the pen
    // buffer before acting — otherwise freshly-written ink isn't captured until
    // the user navigates away and back. See [InkFlushHandle].
    val inkHandle = remember { InkFlushHandle() }
    var guidelineStyle by remember { mutableStateOf(GuidelineStyle.None) }
    var showEraseAllConfirm by remember { mutableStateOf(false) }
    var columnSplit by rememberSaveable { mutableStateOf(false) }
    var version by remember(date) { mutableIntStateOf(0) }

    // Reading `version` here makes the whole composable recompose on every
    // stroke, so `strokes` below is always current without a separate key block.
    @Suppress("UNUSED_EXPRESSION")
    version
    val strokes = strokeStore.strokesFor(date, selectedScope)

    val selectedMeeting = (selectedScope as? CaptureScope.Meeting)
        ?.let { scope -> meetings.find { it.meetingIndex == scope.meetingIndex } }
    // "Already noted" content for the current scope: a meeting's detail bullets,
    // or the page-level Notes bullets when the Notes scope is selected.
    val detailLines = when (selectedScope) {
        is CaptureScope.Meeting -> selectedMeeting?.entry?.detailLines.orEmpty()
        CaptureScope.Notes -> noteLines
        CaptureScope.Unscoped -> emptyList()
    }
    val hasDetailLines = detailLines.isNotEmpty()
    var notesExpanded by rememberSaveable(detailLines.firstOrNull()) { mutableStateOf(true) }
    // Stacked-layout height of the "Already noted" scroll area, in dp, adjusted
    // by dragging the handle at its bottom edge with the stylus. Persisted as a
    // UI preference across meetings/days.
    var notedHeightDp by rememberSaveable { mutableFloatStateOf(DEFAULT_NOTED_HEIGHT_DP) }
    val density = LocalDensity.current

    Column(modifier = modifier.fillMaxWidth().fillMaxHeight().padding(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val scopeLabel = when (selectedScope) {
                is CaptureScope.Meeting -> {
                    val meeting = meetings.find { it.meetingIndex == selectedScope.meetingIndex }
                    meeting?.entry?.title ?: meeting?.entry?.startTime.orEmpty()
                }
                CaptureScope.Notes -> "Notes"
                CaptureScope.Unscoped -> "Unscoped"
            }
            Text(
                text = renderWikiLinks(scopeLabel),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
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
                        }
                    )
                },
            )

            toolbarContent(
                selectedScope,
                strokes,
                {
                    // Flush the pen buffer, push any just-finished stroke into
                    // the store, and hand the caller the full, current list.
                    val current = inkHandle.flush()
                    strokeStore.setStrokes(date, selectedScope, current)
                    version++
                    current
                },
            ) {
                strokeStore.clear(date, selectedScope)
                version++
            }

            if (hasDetailLines) {
                FilterChip(
                    selected = columnSplit,
                    onClick = { columnSplit = !columnSplit },
                    label = { Text("Split") },
                )
            }

            // "Erase all" wipes the current canvas in one action. Because any
            // strokes still on it are handwriting that hasn't been converted to
            // Markdown yet, clearing is destructive — so when strokes are
            // present we confirm first. (Per-stroke erasing still works via the
            // stylus side button in hardware.)
            AssistChip(
                onClick = {
                    // Flush first so ink written since the last store update is
                    // counted — otherwise a canvas that looks full reads as
                    // empty and the action no-ops until the user navigates away.
                    val current = inkHandle.flush()
                    strokeStore.setStrokes(date, selectedScope, current)
                    version++
                    if (current.isEmpty()) return@AssistChip
                    showEraseAllConfirm = true
                },
                label = { Text("Erase all") },
            )
        }

        if (showEraseAllConfirm) {
            AlertDialog(
                onDismissRequest = { showEraseAllConfirm = false },
                title = { Text("Erase all handwriting?") },
                text = {
                    Text(
                        "This clears every stroke on this canvas. Anything you " +
                            "haven't converted to notes yet will be lost. This " +
                            "can't be undone.",
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            strokeStore.clear(date, selectedScope)
                            version++
                            showEraseAllConfirm = false
                        },
                    ) { Text("Erase all") }
                },
                dismissButton = {
                    TextButton(onClick = { showEraseAllConfirm = false }) {
                        Text("Cancel")
                    }
                },
            )
        }

        val canvas: @Composable (Modifier) -> Unit = { canvasModifier ->
            key(date, selectedScope, penSettings) {
                MemoCanvas(
                    strokes = strokeStore.strokesFor(date, selectedScope),
                    penSettings = penSettings,
                    guidelineStyle = guidelineStyle,
                    // No UI eraser mode anymore — per-stroke erasing is handled
                    // by the stylus side button in hardware.
                    isEraserActive = false,
                    onStrokeFinished = { stroke ->
                        strokeStore.addStroke(date, selectedScope, stroke)
                        version++
                    },
                    onStrokesErased = { remaining ->
                        strokeStore.setStrokes(date, selectedScope, remaining)
                        version++
                    },
                    modifier = canvasModifier,
                    flushHandle = inkHandle,
                )
            }
        }

        if (hasDetailLines && columnSplit) {
            // Side-by-side: notes take the left half, canvas the right half.
            // When collapsed, the notes column shrinks to its header so the
            // canvas reclaims the freed width.
            Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                val notesModifier = if (notesExpanded) {
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(end = 8.dp)
                } else {
                    Modifier.padding(end = 8.dp)
                }
                AlreadyNotedBlock(
                    detailLines = detailLines,
                    expanded = notesExpanded,
                    onToggleExpanded = { notesExpanded = !notesExpanded },
                    modifier = notesModifier,
                )
                canvas(Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            if (hasDetailLines) {
                // Stacked above the canvas: the noted lines occupy a fixed,
                // stylus-resizable height and scroll within it, so a meeting
                // with many notes can't crowd out the writing canvas below.
                AlreadyNotedBlock(
                    detailLines = detailLines,
                    expanded = notesExpanded,
                    onToggleExpanded = { notesExpanded = !notesExpanded },
                    linesModifier = Modifier
                        .height(notedHeightDp.dp)
                        .verticalScroll(rememberScrollState()),
                )
                if (notesExpanded) {
                    NotedResizeHandle(
                        onDrag = { dragPx ->
                            val deltaDp = with(density) { dragPx.toDp().value }
                            notedHeightDp = (notedHeightDp + deltaDp)
                                .coerceIn(MIN_NOTED_HEIGHT_DP, MAX_NOTED_HEIGHT_DP)
                        },
                    )
                }
            }
            canvas(Modifier.weight(1f))
        }
    }
}

@Composable
private fun AlreadyNotedBlock(
    detailLines: List<String>,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
    linesModifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier
                .clickable { onToggleExpanded() }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (expanded) "▾" else "▸",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(end = 4.dp),
            )
            Text(
                text = "Already noted",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (expanded) {
            Column(modifier = linesModifier.padding(start = 8.dp)) {
                detailLines.forEach { rawLine ->
                    val bullet = parseBulletLine(rawLine)
                    Row(
                        modifier = Modifier.padding(
                            start = (bullet.depth * 24).dp,
                            top = 1.dp,
                            bottom = 1.dp,
                        ),
                    ) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(end = 6.dp),
                        )
                        Text(
                            text = renderInlineMarkdown(bullet.text),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

/**
 * A draggable divider sitting at the bottom edge of the stacked "Already noted"
 * section. Dragging it down with the stylus grows the noted area (and shrinks
 * the canvas); dragging up does the reverse. [onDrag] receives the vertical
 * drag delta in pixels (positive = downward).
 */
@Composable
private fun NotedResizeHandle(
    onDrag: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp)
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // Grab bar, sized to read as a draggable affordance on e-ink.
        Box(
            modifier = Modifier
                .width(48.dp)
                .height(4.dp)
                .background(
                    color = MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(2.dp),
                ),
        )
    }
}
