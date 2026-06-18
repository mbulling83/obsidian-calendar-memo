package com.boxmemo.app.gcal

import java.time.LocalDate
import java.time.LocalTime

/** A single event read from one of the user's selected Google Calendars. */
data class GoogleCalendarEvent(
    val title: String,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val isAllDay: Boolean,
    val calendarName: String,
)

/**
 * Read-only access to the user's selected Google Calendars (work, personal,
 * family, school). Never exposes write operations — Google Calendar is
 * display-only in this app (origin R4).
 */
interface GoogleCalendarRepository {
    suspend fun fetchEvents(date: LocalDate): List<GoogleCalendarEvent>
}

/**
 * Placeholder used until U3's OAuth-backed implementation is wired in.
 * Returns no events rather than failing, so the calendar UI (U4) can be
 * built and tested against the merge logic now.
 */
object NoOpGoogleCalendarRepository : GoogleCalendarRepository {
    override suspend fun fetchEvents(date: LocalDate): List<GoogleCalendarEvent> = emptyList()
}
