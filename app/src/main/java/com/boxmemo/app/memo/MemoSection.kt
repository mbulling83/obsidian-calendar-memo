package com.boxmemo.app.memo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.boxmemo.app.calendar.DayEvent
import java.time.LocalDate

/**
 * Scope selector (Notes + the day's meetings) plus the drawing surface for
 * the selected scope. The actual handwriting/diagram conversion actions
 * (U7-U10) attach to this scope's current strokes.
 */
@Composable
fun MemoSection(
    date: LocalDate,
    meetings: List<DayEvent.ObsidianMeeting>,
    strokeStore: StrokeStore,
    content: @Composable (CaptureScope, List<StrokePath>, (StrokePath) -> Unit) -> Unit = { _, _, _ -> },
) {
    var selectedScope by remember(date) { mutableStateOf<CaptureScope>(CaptureScope.Notes) }

    Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(
                    selected = selectedScope == CaptureScope.Notes,
                    onClick = { selectedScope = CaptureScope.Notes },
                    label = { Text("Notes") },
                )
            }
            items(meetings) { meeting ->
                val scope = CaptureScope.Meeting(meeting.entry.startTime)
                FilterChip(
                    selected = selectedScope == scope,
                    onClick = { selectedScope = scope },
                    label = { Text(meeting.entry.title) },
                )
            }
        }

        // Surface what's already captured for this meeting in Obsidian, so
        // the user doesn't re-write something already there.
        val selectedMeeting = (selectedScope as? CaptureScope.Meeting)
            ?.let { scope -> meetings.find { it.entry.startTime == scope.startTime } }
        if (selectedMeeting != null && selectedMeeting.entry.detailLines.isNotEmpty()) {
            Column(modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)) {
                Text("Already noted:")
                selectedMeeting.entry.detailLines.forEach { line -> Text(line.trim()) }
            }
        }

        val strokes = strokeStore.strokesFor(date, selectedScope)
        MemoCanvas(
            strokes = strokes,
            onStrokeFinished = { stroke -> strokeStore.addStroke(date, selectedScope, stroke) },
        )

        content(selectedScope, strokes) { stroke -> strokeStore.addStroke(date, selectedScope, stroke) }
    }
}
