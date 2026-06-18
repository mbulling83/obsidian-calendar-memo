package com.boxmemo.app.calendar

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Day panel listing the merged Obsidian + Google Calendar events for the
 * selected date. Google-sourced entries carry a leading "(Google)" label so
 * they're visually distinguished from Obsidian meetings (origin R3) without
 * implying they're editable here (origin R4).
 */
@Composable
fun DayEventList(events: List<DayEvent>, meetingsSectionMissing: Boolean) {
    Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        if (meetingsSectionMissing) {
            Text("This day's note has no \"# 👥 Meetings\" section yet.")
        }
        LazyColumn {
            items(events) { event -> DayEventRow(event) }
        }
    }
}

@Composable
private fun DayEventRow(event: DayEvent) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(text = event.startTime.toString())
        Text(text = "  ")
        when (event) {
            is DayEvent.ObsidianMeeting -> Text(text = event.title)
            is DayEvent.FromGoogleCalendar -> Text(text = "(Google · ${event.event.calendarName}) ${event.title}")
        }
    }
}
