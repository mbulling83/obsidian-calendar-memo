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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
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

    // StrokeStore is a plain (non-Compose-observable) map by design, so it
    // stays testable as pure Kotlin. `version` is the recomposition trigger:
    // bumping it after every mutation is what makes a just-finished stroke
    // actually show up, instead of disappearing the moment the gesture ends.
    var version by remember(date) { mutableIntStateOf(0) }

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

        // Keyed on scope only (not version) — the underlying SurfaceView and
        // its TouchHelper raw-drawing session persist across multiple
        // strokes within the same scope; recreating it per-stroke would
        // needlessly tear down and rebind the Onyx pen service every time.
        key(selectedScope) {
            MemoCanvas(
                strokes = strokeStore.strokesFor(date, selectedScope),
                onStrokeFinished = { stroke ->
                    strokeStore.addStroke(date, selectedScope, stroke)
                    version++
                },
            )
        }

        // Keyed on version too — the conversion buttons' enabled state and
        // the strokes they read need to refresh after every captured stroke.
        key(selectedScope, version) {
            val strokes = strokeStore.strokesFor(date, selectedScope)
            content(selectedScope, strokes) { stroke ->
                strokeStore.addStroke(date, selectedScope, stroke)
                version++
            }
        }
    }
}
