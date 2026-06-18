package com.boxmemo.app.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boxmemo.app.gcal.GoogleCalendarRepository
import com.boxmemo.app.vault.DailyNoteRepository
import com.boxmemo.app.vault.MeetingSectionParseResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

data class DayUiState(
    val date: LocalDate,
    val events: List<DayEvent> = emptyList(),
    val meetingsSectionMissing: Boolean = false,
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

    init {
        selectDate(LocalDate.now())
    }

    fun selectDate(date: LocalDate) {
        _uiState.value = DayUiState(date = date, isLoading = true)
        viewModelScope.launch {
            val meetingsResult = dailyNoteRepository.readMeetings(date)
            val meetings = (meetingsResult as? MeetingSectionParseResult.Found)?.entries.orEmpty()
            val googleEvents = googleCalendarRepository.fetchEvents(date)

            _uiState.value = DayUiState(
                date = date,
                events = mergeDayEvents(meetings, googleEvents),
                meetingsSectionMissing = meetingsResult == MeetingSectionParseResult.SectionNotFound,
                isLoading = false,
            )
        }
    }
}
