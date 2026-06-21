package com.boxmemo.app.hwr

private val SENTENCE_SPLIT = Regex("""(?<=[.!?])\s+""")

/**
 * Splits recognized text into bullet-sized chunks at sentence boundaries.
 * Text with no sentence-ending punctuation (a short phrase) becomes a
 * single bullet.
 */
fun formatAsBullets(text: String): List<String> {
    val normalized = text.trim().replace(Regex("""\s+"""), " ")
    if (normalized.isEmpty()) return emptyList()
    return normalized.split(SENTENCE_SPLIT).map { it.trim() }.filter { it.isNotEmpty() }
}

/** Nested detail bullets matching the existing tab-indented meeting style. */
fun formatAsMeetingDetailLines(text: String): List<String> =
    formatAsBullets(text).map { "\t- $it" }

/** Plain (non-nested) bullets matching the page-level Notes section style. */
fun formatAsNoteLines(text: String): List<String> =
    formatAsBullets(text).map { "- $it" }

/**
 * Embeds a saved diagram image as a nested detail bullet under a meeting,
 * using Obsidian's `![[filename]]` wiki-embed (resolves by filename anywhere in
 * the vault).
 */
fun formatDiagramMeetingDetailLine(fileName: String): String = "\t- ![[$fileName]]"

/** Embeds a saved diagram image as a plain (non-nested) bullet, e.g. for the Notes section. */
fun formatDiagramNoteLine(fileName: String): String = "- ![[$fileName]]"
