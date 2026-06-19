package com.boxmemo.app.vault

import java.io.File
import java.time.LocalDate

sealed interface DailyNoteReadResult {
    data class Found(val content: String) : DailyNoteReadResult
    object NoteDoesNotExist : DailyNoteReadResult
    object VaultNotConfigured : DailyNoteReadResult
}

/**
 * Outcome of a write into the daily note, carrying enough detail for the UI to
 * tell the user *why* nothing was added rather than failing silently — e.g. the
 * day's note hasn't been created yet, or the target section is missing.
 */
sealed interface NoteWriteOutcome {
    object Written : NoteWriteOutcome
    object VaultNotConfigured : NoteWriteOutcome
    object NoteMissing : NoteWriteOutcome
    object SectionMissing : NoteWriteOutcome

    /** Detail-bullet writes only: the target meeting is no longer in the note. */
    object MeetingNotFound : NoteWriteOutcome

    /** Detail-bullet writes only: more than one meeting matches the target identity. */
    object AmbiguousMeeting : NoteWriteOutcome

    object WriteFailed : NoteWriteOutcome
}

/**
 * Single owner of reading and writing the day's note file. Both the calendar
 * view and the conversion engines go through this repository rather than
 * touching the filesystem directly, so write-then-replace discipline and
 * section-aware parsing live in one place (see plan Key Technical Decisions).
 */
class DailyNoteRepository(private val vaultSettings: VaultSettings) {

    fun readNote(date: LocalDate): DailyNoteReadResult {
        val path = vaultSettings.resolveDailyNotePath(date)
            ?: return DailyNoteReadResult.VaultNotConfigured
        if (!path.exists()) return DailyNoteReadResult.NoteDoesNotExist
        return DailyNoteReadResult.Found(path.readText())
    }

    fun readMeetings(date: LocalDate): MeetingSectionParseResult? {
        val read = readNote(date)
        val content = (read as? DailyNoteReadResult.Found)?.content ?: return null
        return parseMeetingsSection(content)
    }

    /** Reads the existing bullet lines under the `# 📝 Notes` section (empty if none/unreadable). */
    fun readNotes(date: LocalDate): List<String> {
        val content = (readNote(date) as? DailyNoteReadResult.Found)?.content ?: return emptyList()
        return parseNotesSection(content)
    }

    /**
     * Writes [newContent] to the daily note for [date] via write-then-replace:
     * write to a sibling temp file, then atomically rename over the original,
     * so a concurrently-running LiveSync watcher never observes a
     * partially-written note.
     */
    fun writeNote(date: LocalDate, newContent: String): Boolean {
        val path = vaultSettings.resolveDailyNotePath(date) ?: return false
        val tempFile = File(path.parentFile, "${path.name}.tmp")
        tempFile.writeText(newContent)
        return tempFile.renameTo(path)
    }

    /**
     * Adds a new meeting via the quick-add form (R5/R6). Never partially
     * overwrites the file: the returned [NoteWriteOutcome] reports whether the
     * note was missing, lacked a `# 👥 Meetings` section, or was written.
     */
    fun addMeeting(date: LocalDate, entry: MeetingEntry): NoteWriteOutcome = withNoteContent(date) { content ->
        when (val result = insertMeeting(content, entry)) {
            is MeetingWriteResult.Updated -> writeOutcome(date, result.content)
            // insertMeeting only ever yields Updated or SectionNotFound.
            else -> NoteWriteOutcome.SectionMissing
        }
    }

    /** Adds a new plain note bullet via the quick-add form (R5/R6). */
    fun addNote(date: LocalDate, text: String): NoteWriteOutcome = withNoteContent(date) { content ->
        when (val result = appendNoteBullet(content, text)) {
            is NoteWriteResult.Updated -> writeOutcome(date, result.content)
            NoteWriteResult.SectionNotFound -> NoteWriteOutcome.SectionMissing
        }
    }

    /**
     * Writes converted handwriting bullets under the meeting identified by
     * [startTime]/[endTime]/[title] (R10/AE1). The meeting is located by content
     * identity rather than a file-order index so the write stays correct even if
     * the section was re-ordered on disk since the day view was read; if more
     * than one meeting shares that identity the write is refused. The returned
     * [NoteWriteOutcome] distinguishes not-found, ambiguous, and missing-section
     * from success so the caller can warn the user rather than failing silently.
     */
    fun addMeetingDetailBullets(
        date: LocalDate,
        startTime: String,
        endTime: String,
        title: String,
        bulletLines: List<String>,
    ): NoteWriteOutcome = withNoteContent(date) { content ->
        when (val result = insertMeetingDetailBullets(content, startTime, endTime, title, bulletLines)) {
            is MeetingWriteResult.Updated -> writeOutcome(date, result.content)
            MeetingWriteResult.SectionNotFound -> NoteWriteOutcome.SectionMissing
            MeetingWriteResult.MeetingNotFound -> NoteWriteOutcome.MeetingNotFound
            MeetingWriteResult.AmbiguousMeeting -> NoteWriteOutcome.AmbiguousMeeting
        }
    }

    /** Writes converted handwriting bullets under the page-level Notes section (R10). */
    fun addNoteLines(date: LocalDate, lines: List<String>): NoteWriteOutcome = withNoteContent(date) { content ->
        when (val result = appendNoteLines(content, lines)) {
            is NoteWriteResult.Updated -> writeOutcome(date, result.content)
            NoteWriteResult.SectionNotFound -> NoteWriteOutcome.SectionMissing
        }
    }

    /** Reads the note, mapping the unreadable cases to outcomes before running [block]. */
    private inline fun withNoteContent(date: LocalDate, block: (String) -> NoteWriteOutcome): NoteWriteOutcome =
        when (val read = readNote(date)) {
            is DailyNoteReadResult.Found -> block(read.content)
            DailyNoteReadResult.NoteDoesNotExist -> NoteWriteOutcome.NoteMissing
            DailyNoteReadResult.VaultNotConfigured -> NoteWriteOutcome.VaultNotConfigured
        }

    private fun writeOutcome(date: LocalDate, content: String): NoteWriteOutcome =
        if (writeNote(date, content)) NoteWriteOutcome.Written else NoteWriteOutcome.WriteFailed
}
