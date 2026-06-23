package com.boxmemo.app.vault

/**
 * Tolerant matching of a daily-note section heading against the value a user
 * configured for it (e.g. "Meetings", "# Meetings", "## 👥 Meetings").
 *
 * People write their headings inconsistently, so matching is forgiving about
 * the things that don't change the *meaning* of a heading:
 *  - **number of leading `#`** — `#`, `##`, `###` all match (heading level ignored)
 *  - **surrounding whitespace** — leading/trailing space ignored
 *  - **case** — compared case-insensitively
 *
 * It is *not* forgiving about the heading text itself: the words (and any
 * emoji) must match. So a note whose heading is `# 👥 Meetings` matches a
 * configured `Meetings` only if the configured value also includes the emoji.
 * This keeps matching predictable rather than guessing.
 */
object SectionHeading {

    /** Leading `#` markers and surrounding whitespace removed, lower-cased — the comparable core. */
    fun normalize(raw: String): String =
        raw.trim().trimStart('#').trim().lowercase()

    /**
     * If [line] is an ATX markdown heading (`#`+ then whitespace then text),
     * returns its text with markers and surrounding whitespace stripped;
     * otherwise null. `#tag` (no space after the hashes) is not a heading.
     */
    fun headingText(line: String): String? {
        val trimmed = line.trim()
        if (!trimmed.startsWith("#")) return null
        val afterHashes = trimmed.trimStart('#')
        if (afterHashes.isEmpty() || !afterHashes.first().isWhitespace()) return null
        return afterHashes.trim()
    }

    /** True when [line] is the heading the user configured as [configured]. */
    fun matches(line: String, configured: String): Boolean {
        val target = normalize(configured)
        if (target.isEmpty()) return false
        val text = headingText(line) ?: return false
        return text.lowercase() == target
    }

    /**
     * True when [line] starts a new section and therefore ends the current one:
     * any ATX heading (regardless of level) or a `---` horizontal rule. Used to
     * find a section's exclusive end after its heading.
     */
    fun isSectionBoundary(line: String): Boolean =
        line.trim() == "---" || headingText(line) != null
}
