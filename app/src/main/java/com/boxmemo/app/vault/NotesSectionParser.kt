package com.boxmemo.app.vault

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
fun parseNotesSection(
    noteContent: String,
    heading: String = VaultSettings.DEFAULT_NOTES_HEADING,
): List<String> {
    val allLines = noteContent.lines()
    val (start, end) = notesSectionRange(allLines, heading) ?: return emptyList()
    return allLines.subList(start, end).filter { it.isNotBlank() }
}

/**
 * Half-open `[start, end)` line range of the Notes section body (after the
 * heading, up to the next heading/`---` boundary), or null if there's no heading.
 */
private fun notesSectionRange(allLines: List<String>, heading: String): Pair<Int, Int>? {
    val headingIndex = allLines.indexOfFirst { SectionHeading.matches(it, heading) }
    if (headingIndex == -1) return null
    val sectionEnd = allLines
        .withIndex()
        .drop(headingIndex + 1)
        .firstOrNull { (_, line) -> SectionHeading.isSectionBoundary(line) }
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
fun appendNoteBullet(
    noteContent: String,
    text: String,
    heading: String = VaultSettings.DEFAULT_NOTES_HEADING,
): NoteWriteResult =
    appendNoteLines(noteContent, listOf("- $text"), heading)

/** Appends already-formatted bullet lines (see [com.boxmemo.app.hwr.formatAsNoteLines]). */
fun appendNoteLines(
    noteContent: String,
    lines: List<String>,
    heading: String = VaultSettings.DEFAULT_NOTES_HEADING,
): NoteWriteResult {
    val allLines = noteContent.lines().toMutableList()
    val (_, sectionEnd) = notesSectionRange(allLines, heading) ?: return NoteWriteResult.SectionNotFound
    allLines.addAll(sectionEnd, lines)
    return NoteWriteResult.Updated(allLines.joinToString("\n"))
}
