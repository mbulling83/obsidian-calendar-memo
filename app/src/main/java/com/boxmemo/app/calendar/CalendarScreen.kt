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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.boxmemo.app.hwr.RecognitionMethodPreference
import com.boxmemo.app.memo.CaptureScope
import com.boxmemo.app.memo.ConversionActions
import com.boxmemo.app.memo.MemoSection
import com.boxmemo.app.memo.PenSettingsStore
import com.boxmemo.app.memo.PenSettings
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
fun CalendarScreen(
    viewModel: DayViewModel,
    dailyNoteRepository: DailyNoteRepository,
    strokeStore: StrokeStore,
    penSettingsStore: PenSettingsStore,
    recognitionMethodPreference: RecognitionMethodPreference,
) {
    val uiState by viewModel.uiState.collectAsState()
    val penSettings by penSettingsStore.settings.collectAsState(initial = PenSettings())

    val meetings = uiState.events.filterIsInstance<DayEvent.ObsidianMeeting>()
    var selectedScope by remember(uiState.date) { mutableStateOf<CaptureScope>(CaptureScope.Notes) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().height(CALENDAR_SECTION_HEIGHT)) {
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                // calendar takes ~1/3 of the width
                CalendarView(
                    yearMonth = YearMonth.from(uiState.date),
                    selectedDate = uiState.date,
                    today = java.time.LocalDate.now(),
                    onPreviousMonth = { viewModel.selectDate(uiState.date.minusMonths(1)) },
                    onNextMonth = { viewModel.selectDate(uiState.date.plusMonths(1)) },
                    onToday = { viewModel.selectDate(java.time.LocalDate.now()) },
                    onDaySelected = { viewModel.selectDate(it) },
                )
            }
            VerticalDivider(modifier = Modifier.fillMaxHeight(), thickness = 2.dp)
            Column(modifier = Modifier.weight(2f).fillMaxHeight()) {
                // agenda takes ~2/3 of the width
                DayEventList(
                    date = uiState.date,
                    events = uiState.events,
                    meetingsSectionMissing = uiState.meetingsSectionMissing,
                    selectedScope = selectedScope,
                    onScopeSelected = { selectedScope = it },
                )
            }
        }
        HorizontalDivider(thickness = 2.dp)
        MemoSection(
            modifier = Modifier.weight(1f),
            date = uiState.date,
            selectedScope = selectedScope,
            meetings = meetings,
            strokeStore = strokeStore,
            penSettings = penSettings,
            toolbarContent = { scope, strokes, requestClear ->
                ConversionActions(
                    date = uiState.date,
                    scope = scope,
                    strokes = strokes,
                    dailyNoteRepository = dailyNoteRepository,
                    recognitionMethodPreference = recognitionMethodPreference,
                    onConverted = {
                        requestClear()
                        viewModel.selectDate(uiState.date)
                    },
                )
            },
        )
    }
}
