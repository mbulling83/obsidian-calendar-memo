package com.boxmemo.app.memo

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.boxmemo.app.hwr.HwrEngineType
import com.boxmemo.app.hwr.formatAsMeetingDetailLines
import com.boxmemo.app.hwr.formatAsNoteLines
import com.boxmemo.app.settings.HwrSettingsStore
import com.boxmemo.app.vault.DailyNoteRepository
import com.boxmemo.app.vault.NoteWriteOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Single manual conversion action (R8) using the Onyx built-in MyScript
 * recognizer to turn handwriting into Markdown bullets.
 */
@Composable
fun ConversionActions(
    date: LocalDate,
    scope: CaptureScope,
    strokes: List<StrokePath>,
    flushStrokes: () -> List<StrokePath>,
    dailyNoteRepository: DailyNoteRepository,
    onConverted: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val hwrSettingsStore = remember { HwrSettingsStore(context) }
    val engine by hwrSettingsStore.engine.collectAsState(initial = HwrEngineType.ONYX)
    var statusMessage by remember(scope) { mutableStateOf<String?>(null) }

    suspend fun writeBack(text: String) {
        val outcome = withContext(Dispatchers.IO) {
            when (scope) {
                is CaptureScope.Meeting ->
                    dailyNoteRepository.addMeetingDetailBullets(
                        date,
                        scope.startTime,
                        scope.endTime,
                        scope.title,
                        formatAsMeetingDetailLines(text),
                    )
                else ->
                    dailyNoteRepository.addNoteLines(date, formatAsNoteLines(text))
            }
        }
        statusMessage = when (outcome) {
            NoteWriteOutcome.Written -> "Converted and saved (${engine.label})."
            NoteWriteOutcome.AmbiguousMeeting ->
                "Converted, but more than one meeting matches this time and title — rename one so they're distinct, then convert again."
            NoteWriteOutcome.MeetingNotFound ->
                "Converted, but this meeting is no longer in the note (it may have changed on disk)."
            NoteWriteOutcome.SectionMissing ->
                "Converted, but the note has no matching section to write to."
            NoteWriteOutcome.NoteMissing ->
                "Converted, but this day's note doesn't exist yet."
            NoteWriteOutcome.VaultNotConfigured ->
                "Converted, but no vault is configured."
            NoteWriteOutcome.WriteFailed ->
                "Converted, but couldn't write to the note."
        }
        if (outcome == NoteWriteOutcome.Written) onConverted()
    }

    FilterChip(
        selected = false,
        // Always enabled: the freshly-written stroke isn't in `strokes` (the
        // store snapshot) until the on-click flush runs, so gating on it here
        // would leave Convert permanently greyed out. The flush below decides
        // whether there's anything to convert.
        onClick = {
            coroutineScope.launch {
                // Flush the pen buffer first so a stroke written since the last
                // store update is recognized too — not just what reached the
                // store before this tap.
                val strokes = flushStrokes().ifEmpty { strokes }
                if (strokes.isEmpty()) {
                    statusMessage = "Nothing to convert."
                    return@launch
                }
                statusMessage = "Recognizing (${engine.label})…"
                when (val outcome = recognizeStrokes(context, engine, strokes)) {
                    is RecognitionOutcome.Unavailable -> statusMessage = outcome.message
                    is RecognitionOutcome.Recognized ->
                        if (outcome.value.isNullOrBlank()) statusMessage = "Nothing recognized."
                        else writeBack(outcome.value)
                }
            }
        },
        label = { Text("Convert") },
    )

    statusMessage?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.bodySmall,
            modifier = androidx.compose.ui.Modifier.padding(start = 6.dp),
        )
    }
}
