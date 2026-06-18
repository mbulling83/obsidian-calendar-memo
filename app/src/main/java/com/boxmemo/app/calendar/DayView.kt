package com.boxmemo.app.calendar

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val AGENDA_DATE_FORMAT = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

/**
 * Agenda panel listing the merged Obsidian + Google Calendar events for the
 * selected date, with start times prominent so the day's schedule is
 * scannable at a glance. Google-sourced entries carry a leading
 * "(Google)" label so they're visually distinguished from Obsidian
 * meetings (origin R3) without implying they're editable here (origin R4).
 */
@Composable
fun DayEventList(date: LocalDate, events: List<DayEvent>, meetingsSectionMissing: Boolean) {
    Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        Text(text = date.format(AGENDA_DATE_FORMAT), style = MaterialTheme.typography.titleMedium)
        if (meetingsSectionMissing) {
            Text("This day's note has no \"# 👥 Meetings\" section yet.")
        }
        if (events.isEmpty() && !meetingsSectionMissing) {
            Text("No events today", style = MaterialTheme.typography.bodySmall)
        }
        LazyColumn {
            items(events) { event -> DayEventRow(event) }
        }
    }
}

@Composable
private fun DayEventRow(event: DayEvent) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            text = event.startTime.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(text = "  ")
        when (event) {
            is DayEvent.ObsidianMeeting -> Text(text = event.title)
            is DayEvent.FromGoogleCalendar -> Text(text = "(Google · ${event.event.calendarName}) ${event.title}")
        }
    }
}
