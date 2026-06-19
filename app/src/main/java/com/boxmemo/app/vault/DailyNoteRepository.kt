package com.boxmemo.app.vault

import java.io.File
import java.time.LocalDate

sealed interface DailyNoteReadResult {
    data class Found(val content: String) : DailyNoteReadResult
    object NoteDoesNotExist : DailyNoteReadResult
    object VaultNotConfigured : DailyNoteReadResult
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
     * Adds a new meeting via the quick-add form (R5/R6). Returns false
     * without writing anything if the note can't be read or has no
     * `# 👥 Meetings` section — a failed insert must never partially
     * overwrite the file.
     */
    fun addMeeting(date: LocalDate, entry: MeetingEntry): Boolean {
        val content = (readNote(date) as? DailyNoteReadResult.Found)?.content ?: return false
        val result = insertMeeting(content, entry) as? MeetingWriteResult.Updated ?: return false
        return writeNote(date, result.content)
    }

    /** Adds a new plain note bullet via the quick-add form (R5/R6). */
    fun addNote(date: LocalDate, text: String): Boolean {
        val content = (readNote(date) as? DailyNoteReadResult.Found)?.content ?: return false
        val result = appendNoteBullet(content, text) as? NoteWriteResult.Updated ?: return false
        return writeNote(date, result.content)
    }

    /**
     * Writes converted handwriting bullets under the meeting at [meetingIndex]
     * — its position within the `# 👥 Meetings` section in file order
     * (R10/AE1). An index rather than a start time is used so meetings sharing
     * a start time stay individually addressable. Returns false without writing
     * anything if the note can't be read or the meeting can't be found.
     */
    fun addMeetingDetailBullets(date: LocalDate, meetingIndex: Int, bulletLines: List<String>): Boolean {
        val content = (readNote(date) as? DailyNoteReadResult.Found)?.content ?: return false
        val result = insertMeetingDetailBullets(content, meetingIndex, bulletLines) as? MeetingWriteResult.Updated
            ?: return false
        return writeNote(date, result.content)
    }

    /** Writes converted handwriting bullets under the page-level Notes section (R10). */
    fun addNoteLines(date: LocalDate, lines: List<String>): Boolean {
        val content = (readNote(date) as? DailyNoteReadResult.Found)?.content ?: return false
        val result = appendNoteLines(content, lines) as? NoteWriteResult.Updated ?: return false
        return writeNote(date, result.content)
    }
}
