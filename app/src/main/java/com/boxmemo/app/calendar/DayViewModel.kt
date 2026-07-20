package com.boxmemo.app.calendar

import com.boxmemo.app.gcal.GoogleCalendarRepository
import com.boxmemo.app.vault.DailyNoteReadResult
import com.boxmemo.app.vault.DailyNoteRepository
import com.boxmemo.app.vault.MeetingEntry
import com.boxmemo.app.vault.MeetingSectionParseResult
import com.boxmemo.app.vault.NoteCreateOutcome
import com.boxmemo.app.vault.NoteWriteOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
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
    /** False when the day's note file doesn't exist yet, so the UI can offer to create it. */
    val noteExists: Boolean = true,
    val isLoading: Boolean = false,
)

/**
 * Owns the merged day view for a selected date: re-fetches both the
 * Obsidian daily note and Google Calendar whenever the selected date
 * changes, so switching days never shows stale data from the previous
 * selection.
 *
 * A plain class (not an androidx ViewModel): MainActivity rebuilds it whenever
 * the vault settings change, so lifecycle-owned clearing would never run.
 * Instead it owns its own scope, and the host calls [dispose] when replacing
 * an instance so stale loads can't leak or write into the new state.
 */
class DayViewModel(
    private val dailyNoteRepository: DailyNoteRepository,
    private val googleCalendarRepository: GoogleCalendarRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var loadJob: Job? = null

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

    /** Cancels in-flight work; call when this instance is replaced or discarded. */
    fun dispose() {
        scope.cancel()
    }

    fun selectDate(date: LocalDate) {
        // Cancel any in-flight load so a slow read of a previously selected
        // date can't finish late and overwrite this newer selection.
        loadJob?.cancel()
        _uiState.value = DayUiState(date = date, meetingsHeading = dailyNoteRepository.meetingsHeading, isLoading = true)
        loadJob = scope.launch(Dispatchers.IO) {
            val noteExists = dailyNoteRepository.readNote(date) is DailyNoteReadResult.Found
            val meetingsResult = dailyNoteRepository.readMeetings(date)
            val meetings = (meetingsResult as? MeetingSectionParseResult.Found)?.entries.orEmpty()
            val noteLines = dailyNoteRepository.readNotes(date)
            val googleEvents = googleCalendarRepository.fetchEvents(date)

            ensureActive()
            _uiState.value = DayUiState(
                date = date,
                events = mergeDayEvents(meetings, googleEvents),
                noteLines = noteLines,
                meetingsSectionMissing = meetingsResult == MeetingSectionParseResult.SectionNotFound,
                meetingsHeading = dailyNoteRepository.meetingsHeading,
                noteExists = noteExists,
                isLoading = false,
            )
        }
    }

    /**
     * Creates the selected day's note from the configured Templater template
     * (R: friends using Templater can stamp their own template into a fresh
     * note), then refreshes so the new note's sections appear.
     */
    fun createDailyNote() {
        scope.launch(Dispatchers.IO) {
            _message.value = when (val outcome = dailyNoteRepository.createNote(uiState.value.date)) {
                is NoteCreateOutcome.Created ->
                    if (outcome.usedTemplate) "Created today's note from your template." else "Created today's note."
                NoteCreateOutcome.AlreadyExists -> null
                NoteCreateOutcome.VaultNotConfigured ->
                    "No vault is configured yet — set one in Settings first."
                NoteCreateOutcome.WriteFailed ->
                    "Couldn't create the note — check the vault path and all-files access."
            }
            selectDate(uiState.value.date)
        }
    }

    /** Quick-add a new meeting (R5/R6), then refresh the day view. */
    fun addMeeting(startTime: String, endTime: String, title: String) {
        scope.launch(Dispatchers.IO) {
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
        scope.launch(Dispatchers.IO) {
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
