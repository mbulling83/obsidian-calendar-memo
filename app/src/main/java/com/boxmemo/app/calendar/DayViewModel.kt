package com.boxmemo.app.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boxmemo.app.gcal.GoogleCalendarRepository
import com.boxmemo.app.vault.DailyNoteRepository
import com.boxmemo.app.vault.MeetingEntry
import com.boxmemo.app.vault.MeetingSectionParseResult
import com.boxmemo.app.vault.NoteWriteOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

data class DayUiState(
    val date: LocalDate,
    val events: List<DayEvent> = emptyList(),
    val noteLines: List<String> = emptyList(),
    val meetingsSectionMissing: Boolean = false,
    val meetingsHeading: String = com.boxmemo.app.vault.VaultSettings.DEFAULT_MEETINGS_HEADING,
    val isLoading: Boolean = false,
)

/**
 * Owns the merged day view for a selected date: re-fetches both the
 * Obsidian daily note and Google Calendar whenever the selected date
 * changes, so switching days never shows stale data from the previous
 * selection.
 */
class DayViewModel(
    private val dailyNoteRepository: DailyNoteRepository,
    private val googleCalendarRepository: GoogleCalendarRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DayUiState(date = LocalDate.now()))
    val uiState: StateFlow<DayUiState> = _uiState.asStateFlow()

    /** One-shot user-facing message (e.g. a failed quick-add), consumed by the UI. */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun messageShown() {
        _message.value = null
    }

    init {
        selectDate(LocalDate.now())
    }

    fun selectDate(date: LocalDate) {
        _uiState.value = DayUiState(date = date, meetingsHeading = dailyNoteRepository.meetingsHeading, isLoading = true)
        viewModelScope.launch(Dispatchers.IO) {
            val meetingsResult = dailyNoteRepository.readMeetings(date)
            val meetings = (meetingsResult as? MeetingSectionParseResult.Found)?.entries.orEmpty()
            val noteLines = dailyNoteRepository.readNotes(date)
            val googleEvents = googleCalendarRepository.fetchEvents(date)

            _uiState.value = DayUiState(
                date = date,
                events = mergeDayEvents(meetings, googleEvents),
                noteLines = noteLines,
                meetingsSectionMissing = meetingsResult == MeetingSectionParseResult.SectionNotFound,
                meetingsHeading = dailyNoteRepository.meetingsHeading,
                isLoading = false,
            )
        }
    }

    /** Quick-add a new meeting (R5/R6), then refresh the day view. */
    fun addMeeting(startTime: String, endTime: String, title: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val outcome = dailyNoteRepository.addMeeting(
                uiState.value.date,
                MeetingEntry(startTime, endTime, title, emptyList()),
            )
            _message.value = quickAddMessage(outcome, "meeting", dailyNoteRepository.meetingsHeading)
            selectDate(uiState.value.date)
        }
    }

    /** Quick-add a new note bullet (R5/R6), then refresh the day view. */
    fun addNote(text: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val outcome = dailyNoteRepository.addNote(uiState.value.date, text)
            _message.value = quickAddMessage(outcome, "note", dailyNoteRepository.notesHeading)
            selectDate(uiState.value.date)
        }
    }

    /** Maps a quick-add [outcome] to a user-facing warning, or null on success. */
    private fun quickAddMessage(outcome: NoteWriteOutcome, kind: String, section: String): String? =
        when (outcome) {
            NoteWriteOutcome.Written -> null
            NoteWriteOutcome.NoteMissing ->
                "This day's note doesn't exist yet, so the $kind wasn't added. Create the daily note first."
            NoteWriteOutcome.VaultNotConfigured ->
                "No vault is configured yet — set one in Settings before adding a $kind."
            NoteWriteOutcome.SectionMissing ->
                "This note has no \"$section\" section, so the $kind wasn't added."
            else ->
                "Couldn't write the $kind to the note."
        }
}
