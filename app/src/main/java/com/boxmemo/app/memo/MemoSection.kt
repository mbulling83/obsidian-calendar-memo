package com.boxmemo.app.memo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.boxmemo.app.calendar.DayEvent
import java.time.LocalDate

/**
 * Handwriting canvas for the currently selected scope (a meeting or Notes).
 * Scope selection is driven externally via the agenda in the top-right panel.
 */
@Composable
fun MemoSection(
    date: LocalDate,
    selectedScope: CaptureScope,
    meetings: List<DayEvent.ObsidianMeeting>,
    strokeStore: StrokeStore,
    penSettings: PenSettings,
    content: @Composable (CaptureScope, List<StrokePath>, (StrokePath) -> Unit) -> Unit = { _, _, _ -> },
) {
    var isEraserActive by remember { mutableStateOf(false) }

    var version by remember(date) { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
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
                selected = isEraserActive,
                onClick = { isEraserActive = !isEraserActive },
                label = { Text("Eraser") },
            )
        }

        val selectedMeeting = (selectedScope as? CaptureScope.Meeting)
            ?.let { scope -> meetings.find { it.entry.startTime == scope.startTime } }
        if (selectedMeeting != null && selectedMeeting.entry.detailLines.isNotEmpty()) {
            Column(modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)) {
                Text("Already noted:")
                selectedMeeting.entry.detailLines.forEach { line -> Text(line.trim()) }
            }
        }

        key(selectedScope, penSettings) {
            MemoCanvas(
                strokes = strokeStore.strokesFor(date, selectedScope),
                penSettings = penSettings,
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

        key(selectedScope, version) {
            val strokes = strokeStore.strokesFor(date, selectedScope)
            content(selectedScope, strokes) { stroke ->
                strokeStore.addStroke(date, selectedScope, stroke)
                version++
            }
        }
    }
}
