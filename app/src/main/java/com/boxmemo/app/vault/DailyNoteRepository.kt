package com.boxmemo.app.vault

import java.io.File
import java.time.LocalDate

sealed interface DailyNoteReadResult {
    data class Found(val content: String) : DailyNoteReadResult
    object NoteDoesNotExist : DailyNoteReadResult
    object VaultNotConfigured : DailyNoteReadResult
}

/** Outcome of creating a not-yet-existing daily note from the configured template. */
sealed interface NoteCreateOutcome {
    /** The note was created; [usedTemplate] is false when the default scaffold was used. */
    data class Created(val usedTemplate: Boolean) : NoteCreateOutcome
    object VaultNotConfigured : NoteCreateOutcome
    object AlreadyExists : NoteCreateOutcome
    object WriteFailed : NoteCreateOutcome
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

    /** The configured section headings, exposed so the UI can name them in messages. */
    val meetingsHeading: String get() = vaultSettings.meetingsHeading
    val notesHeading: String get() = vaultSettings.notesHeading

    /**
     * Creates the daily note for [date] if it doesn't already exist, filling it
     * from the user's configured Templater template (rendered natively by
     * [TemplaterRenderer] — see CLAUDE.md, we replicate rather than execute
     * Templater) or, if no readable template is set, a minimal scaffold of the
     * configured section headings. Parent folders are created as needed and the
     * file is written via write-then-replace so LiveSync never sees a partial
     * note. Refuses to overwrite an existing note.
     */
    fun createNote(date: LocalDate): NoteCreateOutcome {
        val path = vaultSettings.resolveDailyNotePath(date) ?: return NoteCreateOutcome.VaultNotConfigured
        if (path.exists()) return NoteCreateOutcome.AlreadyExists

        val templateText = vaultSettings.resolveTemplateFile()
            ?.takeIf { it.isFile }
            ?.let { runCatching { it.readText() }.getOrNull() }
        val usedTemplate = templateText != null
        val body = if (templateText != null) {
            TemplaterRenderer.render(templateText, date, path.nameWithoutExtension)
        } else {
            vaultSettings.defaultNoteScaffold()
        }

        path.parentFile?.mkdirs()
        return if (writeNote(date, body)) NoteCreateOutcome.Created(usedTemplate) else NoteCreateOutcome.WriteFailed
    }

    fun readNote(date: LocalDate): DailyNoteReadResult {
        val path = vaultSettings.resolveDailyNotePath(date)
            ?: return DailyNoteReadResult.VaultNotConfigured
        if (!path.exists()) return DailyNoteReadResult.NoteDoesNotExist
        return DailyNoteReadResult.Found(path.readText())
    }

    fun readMeetings(date: LocalDate): MeetingSectionParseResult? {
        val read = readNote(date)
        val content = (read as? DailyNoteReadResult.Found)?.content ?: return null
        return parseMeetingsSection(content, vaultSettings.meetingsHeading)
    }

    /** Reads the existing bullet lines under the Notes section (empty if none/unreadable). */
    fun readNotes(date: LocalDate): List<String> {
        val content = (readNote(date) as? DailyNoteReadResult.Found)?.content ?: return emptyList()
        return parseNotesSection(content, vaultSettings.notesHeading)
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
        when (val result = insertMeeting(content, entry, vaultSettings.meetingsHeading)) {
            is MeetingWriteResult.Updated -> writeOutcome(date, result.content)
            // insertMeeting only ever yields Updated or SectionNotFound.
            else -> NoteWriteOutcome.SectionMissing
        }
    }

    /** Adds a new plain note bullet via the quick-add form (R5/R6). */
    fun addNote(date: LocalDate, text: String): NoteWriteOutcome = withNoteContent(date) { content ->
        when (val result = appendNoteBullet(content, text, vaultSettings.notesHeading)) {
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
        when (
            val result =
                insertMeetingDetailBullets(content, startTime, endTime, title, bulletLines, vaultSettings.meetingsHeading)
        ) {
            is MeetingWriteResult.Updated -> writeOutcome(date, result.content)
            MeetingWriteResult.SectionNotFound -> NoteWriteOutcome.SectionMissing
            MeetingWriteResult.MeetingNotFound -> NoteWriteOutcome.MeetingNotFound
            MeetingWriteResult.AmbiguousMeeting -> NoteWriteOutcome.AmbiguousMeeting
        }
    }

    /** Writes converted handwriting bullets under the page-level Notes section (R10). */
    fun addNoteLines(date: LocalDate, lines: List<String>): NoteWriteOutcome = withNoteContent(date) { content ->
        when (val result = appendNoteLines(content, lines, vaultSettings.notesHeading)) {
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
