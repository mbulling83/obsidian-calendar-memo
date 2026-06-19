package com.boxmemo.app.memo

/**
 * What a handwriting capture is bound to: a specific meeting entry, the
 * page-level Notes section, or unscoped (not yet attached to anything).
 *
 * [Meeting.meetingIndex] is the meeting's index within the day's meetings (file
 * order). It exists only as an in-session disambiguator for UI selection and
 * stroke keying: start times are not unique within a day, so keying selection
 * on time alone would make two meetings sharing one indistinguishable.
 *
 * Write-back to the note, by contrast, locates the meeting by its content
 * identity ([startTime], [endTime], [title]) rather than the index, so that
 * converted bullets land on the right line even if the file was re-ordered on
 * disk (e.g. by a concurrent LiveSync) since the day view was read. When that
 * identity matches more than one meeting the write is refused rather than
 * guessed — see DailyNoteRepository.addMeetingDetailBullets.
 */
sealed interface CaptureScope {
    data class Meeting(
        val meetingIndex: Int,
        val startTime: String = "",
        val endTime: String = "",
        val title: String = "",
    ) : CaptureScope
    object Notes : CaptureScope
    object Unscoped : CaptureScope
}
