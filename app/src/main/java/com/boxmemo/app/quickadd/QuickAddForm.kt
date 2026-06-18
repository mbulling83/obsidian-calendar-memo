package com.boxmemo.app.quickadd

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalTime
import java.time.format.DateTimeFormatter

enum class QuickAddTarget { MEETING, NOTE }

private val HHMM = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Quick-add form for new meetings/notes (R5/R6). Time is entered via a
 * Material3 time picker dialog (not handwriting, not free-text HH:MM
 * typing) — avoids both OCR errors and clunky manual digit entry corrupting
 * the structured time field the daily note's parser depends on.
 */
@Composable
fun QuickAddForm(
    onAddMeeting: (startTime: String, endTime: String, title: String) -> Unit,
    onAddNote: (text: String) -> Unit,
    onDone: () -> Unit = {},
) {
    var target by remember { mutableStateOf(QuickAddTarget.MEETING) }
    var startTime by remember { mutableStateOf<LocalTime?>(null) }
    var endTime by remember { mutableStateOf<LocalTime?>(null) }
    var title by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = target == QuickAddTarget.MEETING, onClick = { target = QuickAddTarget.MEETING })
                Text("Meeting")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = target == QuickAddTarget.NOTE, onClick = { target = QuickAddTarget.NOTE })
                Text("Note")
            }
        }

        if (target == QuickAddTarget.MEETING) {
            TimeField(label = "Start", time = startTime, onTimeSelected = { startTime = it })
            EndTimeField(startTime = startTime, endTime = endTime, onEndTimeSelected = { endTime = it })
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") })
            Button(
                enabled = startTime != null && endTime != null && title.isNotBlank(),
                onClick = {
                    onAddMeeting(startTime!!.format(HHMM), endTime!!.format(HHMM), title)
                    startTime = null
                    endTime = null
                    title = ""
                    onDone()
                },
            ) { Text("Add meeting") }
        } else {
            OutlinedTextField(value = noteText, onValueChange = { noteText = it }, label = { Text("Note") })
            Button(
                enabled = noteText.isNotBlank(),
                onClick = { onAddNote(noteText); noteText = ""; onDone() },
            ) { Text("Add note") }
        }
    }
}
