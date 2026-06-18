package com.boxmemo.app.memo

/**
 * What a handwriting capture is bound to: a specific meeting entry, the
 * page-level Notes section, or unscoped (not yet attached to anything).
 * Meetings are identified by start time, the stable key the daily note's
 * meeting format already guarantees uniqueness for within a day.
 */
sealed interface CaptureScope {
    data class Meeting(val startTime: String) : CaptureScope
    object Notes : CaptureScope
    object Unscoped : CaptureScope
}
