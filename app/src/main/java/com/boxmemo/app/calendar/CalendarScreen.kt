package com.boxmemo.app.calendar

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.boxmemo.app.memo.ConversionActions
import com.boxmemo.app.memo.MemoSection
import com.boxmemo.app.memo.StrokeStore
import com.boxmemo.app.vault.DailyNoteRepository
import java.time.YearMonth

// Calendar header + 6 week rows at CalendarView's compact ROW_HEIGHT, plus
// a little breathing room. Fixed (not intrinsic) so the agenda's LazyColumn
// — which doesn't support intrinsic height measurement — has a bounded
// height to scroll within, and the row never stretches to fill the screen.
private val CALENDAR_SECTION_HEIGHT = 280.dp

/**
 * Month grid and the selected day's agenda side by side (mirroring the
 * native Boox Calendar+Memo layout), plus the handwriting memo section
 * below. Settings and quick-add live in modals owned by the caller.
 */
@Composable
fun CalendarScreen(viewModel: DayViewModel, dailyNoteRepository: DailyNoteRepository, strokeStore: StrokeStore) {
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().height(CALENDAR_SECTION_HEIGHT)) {
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                CalendarView(
                    yearMonth = YearMonth.from(uiState.date),
                    selectedDate = uiState.date,
                    today = java.time.LocalDate.now(),
                    onPreviousMonth = { viewModel.selectDate(uiState.date.minusMonths(1)) },
                    onNextMonth = { viewModel.selectDate(uiState.date.plusMonths(1)) },
                    onDaySelected = { viewModel.selectDate(it) },
                )
            }
            VerticalDivider(modifier = Modifier.fillMaxHeight())
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                DayEventList(
                    date = uiState.date,
                    events = uiState.events,
                    meetingsSectionMissing = uiState.meetingsSectionMissing,
                )
            }
        }
        HorizontalDivider()
        val meetings = uiState.events.filterIsInstance<DayEvent.ObsidianMeeting>()
        MemoSection(date = uiState.date, meetings = meetings, strokeStore = strokeStore) { scope, strokes, _ ->
            ConversionActions(
                date = uiState.date,
                scope = scope,
                strokes = strokes,
                dailyNoteRepository = dailyNoteRepository,
                onConverted = {
                    strokeStore.clear(uiState.date, scope)
                    viewModel.selectDate(uiState.date)
                },
            )
        }
    }
}
