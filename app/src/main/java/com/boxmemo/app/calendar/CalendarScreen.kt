package com.boxmemo.app.calendar

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.boxmemo.app.gcal.GoogleCalendarRepository
import com.boxmemo.app.quickadd.QuickAddForm
import com.boxmemo.app.vault.DailyNoteRepository
import java.time.YearMonth

/** Top-level screen combining the month grid and the selected day's merged event list. */
@Composable
fun CalendarScreen(
    dailyNoteRepository: DailyNoteRepository,
    googleCalendarRepository: GoogleCalendarRepository,
) {
    val viewModel = remember { DayViewModel(dailyNoteRepository, googleCalendarRepository) }
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        CalendarView(
            yearMonth = YearMonth.from(uiState.date),
            selectedDate = uiState.date,
            today = java.time.LocalDate.now(),
            onPreviousMonth = { viewModel.selectDate(uiState.date.minusMonths(1)) },
            onNextMonth = { viewModel.selectDate(uiState.date.plusMonths(1)) },
            onDaySelected = { viewModel.selectDate(it) },
        )
        HorizontalDivider()
        DayEventList(events = uiState.events, meetingsSectionMissing = uiState.meetingsSectionMissing)
        HorizontalDivider()
        QuickAddForm(
            onAddMeeting = { startTime, endTime, title -> viewModel.addMeeting(startTime, endTime, title) },
            onAddNote = { text -> viewModel.addNote(text) },
        )
    }
}
