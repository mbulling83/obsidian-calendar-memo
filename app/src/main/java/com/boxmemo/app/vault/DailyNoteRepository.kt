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
}
