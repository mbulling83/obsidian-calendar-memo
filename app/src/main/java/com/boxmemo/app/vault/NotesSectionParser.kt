package com.boxmemo.app.vault

private const val NOTES_HEADING = "# 📝 Notes"

sealed interface NoteWriteResult {
    data class Updated(val content: String) : NoteWriteResult
    object SectionNotFound : NoteWriteResult
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
    val headingIndex = allLines.indexOfFirst { it.trim() == NOTES_HEADING }
    if (headingIndex == -1) return NoteWriteResult.SectionNotFound

    val sectionEnd = allLines
        .withIndex()
        .drop(headingIndex + 1)
        .firstOrNull { (_, line) -> line.trim() == "---" || line.trim().startsWith("# ") }
        ?.index
        ?: allLines.size

    allLines.addAll(sectionEnd, lines)
    return NoteWriteResult.Updated(allLines.joinToString("\n"))
}
