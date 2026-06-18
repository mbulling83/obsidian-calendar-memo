package com.boxmemo.app.calendar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val AGENDA_DATE_FORMAT = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

// Matches leading whitespace + "- " to strip the raw Markdown prefix from detail lines.
private val DETAIL_PREFIX = Regex("""^[\t ]*-\s""")

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
    when (event) {
        is DayEvent.ObsidianMeeting -> ObsidianMeetingRow(event)
        is DayEvent.FromGoogleCalendar -> GoogleEventRow(event)
    }
}

@Composable
private fun ObsidianMeetingRow(event: DayEvent.ObsidianMeeting) {
    val bullets = event.entry.detailLines
    val hasBullets = bullets.isNotEmpty()
    var expanded by rememberSaveable(event.entry.startTime) { mutableStateOf(true) }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (hasBullets) Modifier.clickable { expanded = !expanded } else Modifier)
                .padding(vertical = 2.dp),
        ) {
            Text(
                text = event.startTime.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(text = "  ")
            Text(
                text = if (hasBullets) "${if (expanded) "▾" else "▸"} ${event.title}"
                else event.title,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (hasBullets && expanded) {
            Column(modifier = Modifier.padding(start = 16.dp, bottom = 2.dp)) {
                bullets.forEach { rawLine ->
                    val text = DETAIL_PREFIX.replace(rawLine.trimStart('\t'), "")
                    Row(modifier = Modifier.padding(vertical = 1.dp)) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(end = 6.dp),
                        )
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GoogleEventRow(event: DayEvent.FromGoogleCalendar) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            text = event.startTime.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(text = "  ")
        Text(text = "(Google · ${event.event.calendarName}) ${event.title}")
    }
}
