package com.boxmemo.app.quickadd

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val HHMM = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Round [time] up to the next 15-minute mark so the steppers start on the grid.
 * Capped at 23:45 — opening the form just before midnight must not roll the
 * default start into 00:00 of the wrong day.
 */
private fun roundUpTo15(time: LocalTime): LocalTime {
    val truncated = time.withSecond(0).withNano(0).withMinute((time.minute / 15) * 15)
    val rounded = if (truncated == time.withSecond(0).withNano(0)) truncated else truncated.plusMinutes(15)
    return if (rounded.isBefore(truncated)) LocalTime.of(23, 45) else rounded
}

/**
 * Quick-add form for new meetings (R5). Times are entered via inline +/-
 * steppers (hours by 1, minutes by 15) — no nested dialog, no clock dial,
 * no free-text HH:MM typing — which avoids OCR errors, keyboard fiddliness
 * on e-ink, and corrupting the structured time the daily note parser depends on.
 */
@Composable
fun QuickAddForm(
    onAddMeeting: (startTime: String, endTime: String, title: String) -> Unit,
    onDone: () -> Unit = {},
) {
    val initialStart = remember { roundUpTo15(LocalTime.now()) }
    var startTime by remember { mutableStateOf(initialStart) }
    var endTime by remember { mutableStateOf(initialStart.plusMinutes(30)) }
    var title by remember { mutableStateOf("") }

    OutlinedCard(border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = "New meeting", style = MaterialTheme.typography.titleLarge)
            HorizontalDivider(thickness = 2.dp)
            TimeField(label = "Start", time = startTime, onTimeSelected = { startTime = it })
            EndTimeField(startTime = startTime, endTime = endTime, onEndTimeSelected = { endTime = it })
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                modifier = Modifier.fillMaxWidth(),
                // No end-after-start requirement: the note format is plain
                // HH:MM strings, and a meeting can legitimately cross midnight
                // (e.g. 23:45 – 00:15) — it's written as-is.
                enabled = title.isNotBlank(),
                onClick = {
                    onAddMeeting(startTime.format(HHMM), endTime.format(HHMM), title)
                    title = ""
                    onDone()
                },
            ) { Text("Add meeting") }
        }
    }
}
