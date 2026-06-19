package com.boxmemo.app.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.boxmemo.app.memo.CaptureScope
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val AGENDA_DATE_FORMAT = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

private val WIKI_LINK = Regex("""\[\[([^\]]+)]]""")

/**
 * Renders [[Page]] as underlined "Page" with the brackets hidden, and
 * [[Long Name|alias]] as just the underlined "alias".
 */
private fun renderWikiLinks(text: String): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    for (match in WIKI_LINK.findAll(text)) {
        append(text, cursor, match.range.first)
        pushStyle(SpanStyle(textDecoration = TextDecoration.Underline))
        append(match.groupValues[1].substringAfter('|'))
        pop()
        cursor = match.range.last + 1
    }
    append(text, cursor, text.length)
}

/**
 * Agenda panel listing the merged Obsidian + Google Calendar events for the
 * selected date, with start times prominent so the day's schedule is
 * scannable at a glance. Google-sourced entries carry a leading
 * "(Google)" label so they're visually distinguished from Obsidian
 * meetings (origin R3) without implying they're editable here (origin R4).
 */
@Composable
fun DayEventList(
    date: LocalDate,
    events: List<DayEvent>,
    meetingsSectionMissing: Boolean,
    selectedScope: CaptureScope = CaptureScope.Notes,
    onScopeSelected: (CaptureScope) -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        Text(text = date.format(AGENDA_DATE_FORMAT), style = MaterialTheme.typography.titleMedium)
        if (meetingsSectionMissing) {
            Text("This day's note has no \"# 👥 Meetings\" section yet.")
        }
        if (events.isEmpty() && !meetingsSectionMissing) {
            Text("No events today", style = MaterialTheme.typography.bodySmall)
        }
        LazyColumn {
            items(events) { event ->
                DayEventRow(event, selectedScope, onScopeSelected)
            }
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                val isSelected = selectedScope == CaptureScope.Notes
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onScopeSelected(CaptureScope.Notes) }
                        .then(
                            if (isSelected) Modifier.background(MaterialTheme.colorScheme.secondaryContainer)
                            else Modifier
                        )
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (isSelected) "▶ Notes" else "  Notes",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Composable
private fun DayEventRow(
    event: DayEvent,
    selectedScope: CaptureScope,
    onScopeSelected: (CaptureScope) -> Unit,
) {
    when (event) {
        is DayEvent.ObsidianMeeting -> ObsidianMeetingRow(event, selectedScope, onScopeSelected)
        is DayEvent.FromGoogleCalendar -> GoogleEventRow(event)
    }
}

@Composable
private fun ObsidianMeetingRow(
    event: DayEvent.ObsidianMeeting,
    selectedScope: CaptureScope,
    onScopeSelected: (CaptureScope) -> Unit,
) {
    val scope = CaptureScope.Meeting(event.meetingIndex)
    val isSelected = selectedScope == scope

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onScopeSelected(scope) }
            .then(
                if (isSelected) Modifier.background(MaterialTheme.colorScheme.secondaryContainer)
                else Modifier
            )
            .padding(vertical = 4.dp, horizontal = 4.dp),
    ) {
        Text(
            text = if (isSelected) "▶" else "  ",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(end = 4.dp),
        )
        Text(
            text = event.startTime.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(text = "  ")
        Text(
            text = renderWikiLinks(event.title),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
        )
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
        Text(text = renderWikiLinks("(Google · ${event.event.calendarName}) ${event.title}"))
    }
}
