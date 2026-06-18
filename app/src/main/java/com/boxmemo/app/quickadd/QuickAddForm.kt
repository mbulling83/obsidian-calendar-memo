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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

enum class QuickAddTarget { MEETING, NOTE }

/**
 * Quick-add form for new meetings/notes (R5/R6). Time and title are entered
 * via fields, never handwriting — avoids OCR errors corrupting the
 * structured time field the daily note's parser depends on.
 */
@Composable
fun QuickAddForm(
    onAddMeeting: (startTime: String, endTime: String, title: String) -> Unit,
    onAddNote: (text: String) -> Unit,
) {
    var target by remember { mutableStateOf(QuickAddTarget.MEETING) }
    var startTime by remember { mutableStateOf("") }
    var endTime by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row {
            RadioButton(selected = target == QuickAddTarget.MEETING, onClick = { target = QuickAddTarget.MEETING })
            Text("Meeting")
            RadioButton(selected = target == QuickAddTarget.NOTE, onClick = { target = QuickAddTarget.NOTE })
            Text("Note")
        }

        if (target == QuickAddTarget.MEETING) {
            OutlinedTextField(value = startTime, onValueChange = { startTime = it }, label = { Text("Start (HH:MM)") })
            OutlinedTextField(value = endTime, onValueChange = { endTime = it }, label = { Text("End (HH:MM)") })
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") })
            Button(onClick = { onAddMeeting(startTime, endTime, title) }) { Text("Add meeting") }
        } else {
            OutlinedTextField(value = noteText, onValueChange = { noteText = it }, label = { Text("Note") })
            Button(onClick = { onAddNote(noteText) }) { Text("Add note") }
        }
    }
}
