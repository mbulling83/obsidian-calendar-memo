package com.boxmemo.app.quickadd

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalTime

/**
 * Inline time entry built from +/- steppers: hours step by 1 and minutes by 15.
 *
 * Replaces the Material3 clock-dial dialog, which (a) opened as a nested
 * AlertDialog inside the Quick-Add dialog — an unreliable combination that
 * often did nothing on tap — and (b) was fiddly to operate via a fine dial on
 * e-ink. Steppers are large, unambiguous tap targets that snap minutes to a
 * 15-minute grid, matching the durations offered by [EndTimeField].
 */
@Composable
fun TimeField(label: String, time: LocalTime, onTimeSelected: (LocalTime) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Stepper(
                value = "%02d".format(time.hour),
                onDecrement = { onTimeSelected(time.minusHours(1)) },
                onIncrement = { onTimeSelected(time.plusHours(1)) },
            )
            Text(text = ":", style = MaterialTheme.typography.headlineSmall)
            Stepper(
                value = "%02d".format(time.minute),
                onDecrement = { onTimeSelected(time.minusMinutes(15)) },
                onIncrement = { onTimeSelected(time.plusMinutes(15)) },
            )
        }
    }
}

@Composable
private fun Stepper(value: String, onDecrement: () -> Unit, onIncrement: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        OutlinedButton(onClick = onDecrement) { Text(text = "−", style = MaterialTheme.typography.titleLarge) }
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 44.dp).padding(horizontal = 4.dp),
        )
        OutlinedButton(onClick = onIncrement) { Text(text = "+", style = MaterialTheme.typography.titleLarge) }
    }
}
