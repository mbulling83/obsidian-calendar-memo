package com.boxmemo.app.calendar

import com.boxmemo.app.gcal.GoogleCalendarEvent
import com.boxmemo.app.vault.MeetingEntry
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

class DayEventTest {

    private fun meeting(start: String, end: String, title: String) =
        MeetingEntry(start, end, title, emptyList())

    private fun gcalEvent(start: String, end: String, title: String, calendar: String = "Work") =
        GoogleCalendarEvent(title, LocalTime.parse(start), LocalTime.parse(end), isAllDay = false, calendarName = calendar)

    @Test
    fun `merges an Obsidian meeting and a Google event in chronological order`() {
        val merged = mergeDayEvents(
            meetings = listOf(meeting("14:00", "15:00", "Design review")),
            googleEvents = listOf(gcalEvent("09:00", "09:30", "Standup")),
        )

        assertEquals(listOf("Standup", "Design review"), merged.map { it.title })
    }

    @Test
    fun `breaks ties at the same start time with Obsidian entries first`() {
        val merged = mergeDayEvents(
            meetings = listOf(meeting("09:00", "09:30", "Obsidian standup")),
            googleEvents = listOf(gcalEvent("09:00", "09:30", "Google standup")),
        )

        assertEquals(listOf("Obsidian standup", "Google standup"), merged.map { it.title })
        assertEquals(DayEvent.ObsidianMeeting::class, merged[0]::class)
        assertEquals(DayEvent.FromGoogleCalendar::class, merged[1]::class)
    }

    @Test
    fun `meeting index reflects file order and survives the chronological re-sort`() {
        // Given in reverse-chronological file order, two share a start time.
        val merged = mergeDayEvents(
            meetings = listOf(
                meeting("14:00", "15:00", "Afternoon"),
                meeting("09:00", "09:30", "Standup"),
                meeting("09:00", "09:30", "Parallel sync"),
            ),
            googleEvents = emptyList(),
        )

        val byIndex = merged
            .filterIsInstance<DayEvent.ObsidianMeeting>()
            .associate { it.meetingIndex to it.title }
        // Indices stay tied to file order even though display order is sorted.
        assertEquals(mapOf(0 to "Afternoon", 1 to "Standup", 2 to "Parallel sync"), byIndex)
        // The two 09:00 meetings carry distinct indices, so they're addressable.
        // (Display order sorts ties by title: "Parallel sync" before "Standup".)
        assertEquals(listOf("Parallel sync", "Standup", "Afternoon"), merged.map { it.title })
    }

    @Test
    fun `an empty day with no meetings and no events merges to an empty list`() {
        val merged = mergeDayEvents(meetings = emptyList(), googleEvents = emptyList())

        assertEquals(emptyList<DayEvent>(), merged)
    }

    @Test
    fun `Google events remain tagged with their source calendar after merge`() {
        val merged = mergeDayEvents(
            meetings = emptyList(),
            googleEvents = listOf(gcalEvent("10:00", "11:00", "Pickup", calendar = "Family")),
        )

        val gcal = merged.single() as DayEvent.FromGoogleCalendar
        assertEquals("Family", gcal.event.calendarName)
    }
}
