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
fun appendNoteBullet(noteContent: String, text: String): NoteWriteResult {
    val lines = noteContent.lines().toMutableList()
    val headingIndex = lines.indexOfFirst { it.trim() == NOTES_HEADING }
    if (headingIndex == -1) return NoteWriteResult.SectionNotFound

    val sectionEnd = lines
        .withIndex()
        .drop(headingIndex + 1)
        .firstOrNull { (_, line) -> line.trim() == "---" || line.trim().startsWith("# ") }
        ?.index
        ?: lines.size

    lines.add(sectionEnd, "- $text")
    return NoteWriteResult.Updated(lines.joinToString("\n"))
}
