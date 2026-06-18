package com.boxmemo.app.memo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
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
        append(match.groupValues[1])
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

/**
 * Handwriting canvas for the currently selected scope (a meeting or Notes).
 * Scope selection is driven externally via the agenda panel. The toolbar row
 * hosts scope label, guideline picker, any injected toolbar actions (e.g.
 * Convert), and the Eraser chip — nothing below the canvas so the full
 * remaining height is available for writing.
 */
@Composable
fun MemoSection(
    date: LocalDate,
    selectedScope: CaptureScope,
    meetings: List<DayEvent.ObsidianMeeting>,
    strokeStore: StrokeStore,
    penSettings: PenSettings,
    modifier: Modifier = Modifier,
    toolbarContent: @Composable RowScope.(CaptureScope, List<StrokePath>) -> Unit = { _, _ -> },
) {
    var isEraserActive by remember { mutableStateOf(false) }
    var guidelineStyle by remember { mutableStateOf(GuidelineStyle.None) }
    var version by remember(date) { mutableIntStateOf(0) }

    // Reading `version` here makes the whole composable recompose on every
    // stroke, so `strokes` below is always current without a separate key block.
    @Suppress("UNUSED_EXPRESSION")
    version
    val strokes = strokeStore.strokesFor(date, selectedScope)

    Column(modifier = modifier.fillMaxWidth().fillMaxHeight().padding(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val scopeLabel = when (selectedScope) {
                is CaptureScope.Meeting -> {
                    val meeting = meetings.find { it.entry.startTime == selectedScope.startTime }
                    meeting?.entry?.title ?: selectedScope.startTime
                }
                CaptureScope.Notes -> "Notes"
                CaptureScope.Unscoped -> "Unscoped"
            }
            Text(
                text = scopeLabel,
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

            toolbarContent(selectedScope, strokes)

            FilterChip(
                selected = isEraserActive,
                onClick = { isEraserActive = !isEraserActive },
                label = { Text("Eraser") },
            )
        }

        val selectedMeeting = (selectedScope as? CaptureScope.Meeting)
            ?.let { scope -> meetings.find { it.entry.startTime == scope.startTime } }
        if (selectedMeeting != null && selectedMeeting.entry.detailLines.isNotEmpty()) {
            AlreadyNotedBlock(selectedMeeting.entry.detailLines)
        }

        key(selectedScope, penSettings, guidelineStyle) {
            MemoCanvas(
                strokes = strokeStore.strokesFor(date, selectedScope),
                penSettings = penSettings,
                guidelineStyle = guidelineStyle,
                isEraserActive = isEraserActive,
                onStrokeFinished = { stroke ->
                    strokeStore.addStroke(date, selectedScope, stroke)
                    version++
                },
                onStrokesErased = { remaining ->
                    strokeStore.setStrokes(date, selectedScope, remaining)
                    version++
                },
            )
        }
    }
}

@Composable
private fun AlreadyNotedBlock(detailLines: List<String>) {
    var expanded by rememberSaveable(detailLines.firstOrNull()) { mutableStateOf(true) }

    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier
                .clickable { expanded = !expanded }
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
            Column(modifier = Modifier.padding(start = 8.dp)) {
                detailLines.forEach { rawLine ->
                    val bullet = parseBulletLine(rawLine)
                    Row(
                        modifier = Modifier.padding(
                            start = (bullet.depth * 12).dp,
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
                            text = renderWikiLinks(bullet.text),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}
