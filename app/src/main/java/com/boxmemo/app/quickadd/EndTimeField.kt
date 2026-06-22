package com.boxmemo.app.quickadd

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import java.time.LocalTime

private val QUICK_DURATIONS_MINUTES = listOf(15L, 30L, 45L, 60L)

/**
 * End time entry as quick duration offsets from the start time (15/30/45/60
 * minutes), with the same inline stepper as [TimeField] for anything else.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EndTimeField(startTime: LocalTime, endTime: LocalTime, onEndTimeSelected: (LocalTime) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QUICK_DURATIONS_MINUTES.forEach { minutes ->
                val candidate = startTime.plusMinutes(minutes)
                FilterChip(
                    selected = candidate == endTime,
                    onClick = { onEndTimeSelected(startTime.plusMinutes(minutes)) },
                    label = { Text("${minutes}m") },
                )
            }
        }
        TimeField(label = "End (custom)", time = endTime, onTimeSelected = onEndTimeSelected)
    }
}
