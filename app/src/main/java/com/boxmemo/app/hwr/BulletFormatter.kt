package com.boxmemo.app.hwr

private val SENTENCE_SPLIT = Regex("""(?<=[.!?])\s+""")

/**
 * Splits recognized text into bullet-sized chunks at sentence boundaries.
 * Text with no sentence-ending punctuation (a short phrase) becomes a
 * single bullet.
 */
fun formatAsBullets(text: String): List<String> {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return emptyList()
    return trimmed.split(SENTENCE_SPLIT).map { it.trim() }.filter { it.isNotEmpty() }
}

/** Nested detail bullets matching the existing tab-indented meeting style. */
fun formatAsMeetingDetailLines(text: String): List<String> =
    formatAsBullets(text).map { "\t- $it" }

/** Plain (non-nested) bullets matching the page-level Notes section style. */
fun formatAsNoteLines(text: String): List<String> =
    formatAsBullets(text).map { "- $it" }
