package com.boxmemo.app.memo

import androidx.compose.material3.AssistChip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import com.boxmemo.app.hwr.formatDiagramMeetingDetailLine
import com.boxmemo.app.hwr.formatDiagramNoteLine
import com.boxmemo.app.vault.DailyNoteRepository
import com.boxmemo.app.vault.DiagramRepository
import com.boxmemo.app.vault.DiagramSaveOutcome
import com.boxmemo.app.vault.NoteWriteOutcome
import com.boxmemo.app.vault.meetingDiagramBaseName
import com.boxmemo.app.vault.notesDiagramBaseName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime

/**
 * "Save diagram" action for the daily-note canvas: renders the current strokes
 * to a PNG under `attachments/Diagrams/<year>/W<week>` and inserts an Obsidian
 * `![[…]]` image bullet under the active meeting (or the Notes section).
 *
 * Unlike Convert, this does *not* clear the canvas on success — saving a diagram
 * doesn't consume the ink, so the user can keep drawing, convert it to text, or
 * save again. [onSaved] lets the caller refresh the day view to show the new
 * embed.
 */
@Composable
fun DiagramSaveAction(
    date: LocalDate,
    scope: CaptureScope,
    strokes: List<StrokePath>,
    flushStrokes: () -> List<StrokePath>,
    penSettings: PenSettings,
    diagramRepository: DiagramRepository,
    dailyNoteRepository: DailyNoteRepository,
    onSaved: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    var statusMessage by remember(scope) { mutableStateOf<String?>(null) }

    AssistChip(
        onClick = {
            coroutineScope.launch {
                // Flush the pen buffer first so a stroke drawn since the last
                // store update is included in the saved image.
                val current = flushStrokes().ifEmpty { strokes }
                if (current.isEmpty()) {
                    statusMessage = "Nothing to save."
                    return@launch
                }
                statusMessage = "Saving diagram…"
                val baseName = when (scope) {
                    is CaptureScope.Meeting ->
                        meetingDiagramBaseName(date, scope.startTime, scope.title)
                    else -> notesDiagramBaseName(date, LocalTime.now())
                }
                val outcome = withContext(Dispatchers.IO) {
                    val bitmap = renderStrokesToBitmap(current, penSettings)
                    when (val saved = diagramRepository.saveDiagram(bitmap, date, baseName)) {
                        is DiagramSaveOutcome.Saved -> {
                            val write = when (scope) {
                                is CaptureScope.Meeting ->
                                    dailyNoteRepository.addMeetingDetailBullets(
                                        date,
                                        scope.startTime,
                                        scope.endTime,
                                        scope.title,
                                        listOf(formatDiagramMeetingDetailLine(saved.fileName)),
                                    )
                                else ->
                                    dailyNoteRepository.addNoteLines(
                                        date,
                                        listOf(formatDiagramNoteLine(saved.fileName)),
                                    )
                            }
                            write
                        }
                        DiagramSaveOutcome.VaultNotConfigured -> NoteWriteOutcome.VaultNotConfigured
                        DiagramSaveOutcome.WriteFailed -> NoteWriteOutcome.WriteFailed
                    }
                }
                statusMessage = when (outcome) {
                    NoteWriteOutcome.Written -> "Diagram saved."
                    NoteWriteOutcome.AmbiguousMeeting ->
                        "Diagram saved, but more than one meeting matches this time and title — rename one so they're distinct."
                    NoteWriteOutcome.MeetingNotFound ->
                        "Diagram saved, but this meeting is no longer in the note."
                    NoteWriteOutcome.SectionMissing ->
                        "Diagram saved, but the note has no matching section to link it from."
                    NoteWriteOutcome.NoteMissing ->
                        "Diagram saved, but this day's note doesn't exist yet."
                    NoteWriteOutcome.VaultNotConfigured ->
                        "Couldn't save — no vault is configured."
                    NoteWriteOutcome.WriteFailed ->
                        "Couldn't save the diagram."
                }
                if (outcome == NoteWriteOutcome.Written) onSaved()
            }
        },
        label = { Text("Save diagram") },
    )

    statusMessage?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}
