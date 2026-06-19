package com.boxmemo.app.memo

/**
 * What a handwriting capture is bound to: a specific meeting entry, the
 * page-level Notes section, or unscoped (not yet attached to anything).
 * Meetings are identified by their index within the day's meetings (file
 * order) rather than start time: start times are not unique within a day,
 * so keying on the time would make two meetings sharing one indistinguishable
 * — both would select together and only the first could be written to.
 */
sealed interface CaptureScope {
    data class Meeting(val meetingIndex: Int) : CaptureScope
    object Notes : CaptureScope
    object Unscoped : CaptureScope
}
