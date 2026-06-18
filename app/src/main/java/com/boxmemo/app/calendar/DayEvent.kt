package com.boxmemo.app.calendar

import com.boxmemo.app.gcal.GoogleCalendarEvent
import com.boxmemo.app.vault.MeetingEntry
import java.time.LocalTime

/**
 * A single chronologically-orderable entry in the merged day view. Obsidian
 * meetings and Google Calendar events are kept as distinct variants (rather
 * than flattened into one shape) so the UI can render a visible distinction
 * between them (origin R3) and so Google-sourced entries are structurally
 * incapable of being edited or deleted through this app (origin R4).
 */
sealed interface DayEvent {
    val startTime: LocalTime
    val title: String

    data class ObsidianMeeting(val entry: MeetingEntry) : DayEvent {
        override val startTime: LocalTime = LocalTime.parse(entry.startTime)
        override val title: String = entry.title
    }

    data class FromGoogleCalendar(val event: GoogleCalendarEvent) : DayEvent {
        override val startTime: LocalTime = event.startTime
        override val title: String = event.title
    }
}

/**
 * Merges Obsidian meetings and Google Calendar events into one
 * chronologically-sorted list. Ties are broken with Obsidian entries first,
 * then by title, for a stable and predictable order.
 */
fun mergeDayEvents(
    meetings: List<MeetingEntry>,
    googleEvents: List<GoogleCalendarEvent>,
): List<DayEvent> {
    val obsidianEvents = meetings.map { DayEvent.ObsidianMeeting(it) }
    val googleDayEvents = googleEvents.map { DayEvent.FromGoogleCalendar(it) }

    return (obsidianEvents + googleDayEvents).sortedWith(
        compareBy(
            { it.startTime },
            { if (it is DayEvent.ObsidianMeeting) 0 else 1 },
            { it.title },
        ),
    )
}
