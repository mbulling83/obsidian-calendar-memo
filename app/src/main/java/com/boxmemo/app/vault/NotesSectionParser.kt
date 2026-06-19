package com.boxmemo.app.vault

private const val NOTES_HEADING = "# 📝 Notes"

sealed interface NoteWriteResult {
    data class Updated(val content: String) : NoteWriteResult
    object SectionNotFound : NoteWriteResult
}

/**
 * Reads the existing content lines under the `# 📝 Notes` heading (blank lines
 * dropped), so the memo UI can show what's already been noted for the day in
 * the Notes scope — the page-level equivalent of a meeting's detail bullets.
 * Returns the lines verbatim (e.g. `- a note`), leaving bullet/indent parsing
 * to the renderer.
 */
fun parseNotesSection(noteContent: String): List<String> {
    val allLines = noteContent.lines()
    val (start, end) = notesSectionRange(allLines) ?: return emptyList()
    return allLines.subList(start, end).filter { it.isNotBlank() }
}

/**
 * Half-open `[start, end)` line range of the Notes section body (after the
 * heading, up to the next `---`/`# ` boundary), or null if there's no heading.
 */
private fun notesSectionRange(allLines: List<String>): Pair<Int, Int>? {
    val headingIndex = allLines.indexOfFirst { it.trim() == NOTES_HEADING }
    if (headingIndex == -1) return null
    val sectionEnd = allLines
        .withIndex()
        .drop(headingIndex + 1)
        .firstOrNull { (_, line) -> line.trim() == "---" || line.trim().startsWith("# ") }
        ?.index
        ?: allLines.size
    return (headingIndex + 1) to sectionEnd
}

/**
 * Appends [text] as a plain bullet under the `# 📝 Notes` heading. Notes
 * entries are page-level and untimed (per the user's existing format), so
 * unlike meetings there's no chronological insertion point — new notes are
 * appended at the end of the section's existing content.
 */
fun appendNoteBullet(noteContent: String, text: String): NoteWriteResult =
    appendNoteLines(noteContent, listOf("- $text"))

/** Appends already-formatted bullet lines (see [com.boxmemo.app.hwr.formatAsNoteLines]). */
fun appendNoteLines(noteContent: String, lines: List<String>): NoteWriteResult {
    val allLines = noteContent.lines().toMutableList()
    val (_, sectionEnd) = notesSectionRange(allLines) ?: return NoteWriteResult.SectionNotFound
    allLines.addAll(sectionEnd, lines)
    return NoteWriteResult.Updated(allLines.joinToString("\n"))
}
