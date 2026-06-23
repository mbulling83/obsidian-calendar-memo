package com.boxmemo.app.vault

import java.io.File
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Resolves the absolute path of a daily note file from a configured vault root.
 *
 * Direct [File] access against a user-configured path, gated behind
 * MANAGE_EXTERNAL_STORAGE — not Storage Access Framework — matching the
 * confirmed-working pattern from jdkruzr/aragonite (see plan U1).
 */
class VaultSettings(
    private val vaultRoot: String?,
    private val dailyNoteSubpathTemplate: String = DEFAULT_TEMPLATE,
    /** Configured meetings-section heading; matched forgivingly (see [SectionHeading]). */
    val meetingsHeading: String = DEFAULT_MEETINGS_HEADING,
    /** Configured notes-section heading; matched forgivingly (see [SectionHeading]). */
    val notesHeading: String = DEFAULT_NOTES_HEADING,
) {

    /**
     * Resolves the absolute path for [date]'s daily note, or null if the vault
     * root has not been configured yet.
     */
    fun resolveDailyNotePath(date: LocalDate): File? {
        val root = vaultRoot?.takeIf { it.isNotBlank() } ?: return null
        val subpath = renderTemplate(dailyNoteSubpathTemplate, date)
        return File(root, subpath)
    }

    private fun renderTemplate(template: String, date: LocalDate): String {
        val monthName = date.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        val monthFolder = "%02d - %s".format(date.monthValue, monthName)
        return template
            .replace("{year}", date.year.toString())
            .replace("{monthFolder}", monthFolder)
            .replace("{isoDate}", date.toString())
    }

    companion object {
        /**
         * Matches the user's confirmed Periodic Notes convention:
         * Periodic Notes/Daily Notes/YYYY/MM - Month/YYYY-MM-DD.md
         */
        const val DEFAULT_TEMPLATE =
            "Periodic Notes/Daily Notes/{year}/{monthFolder}/{isoDate}.md"

        /** Default daily-note section headings (the author's own convention). */
        const val DEFAULT_MEETINGS_HEADING = "# 👥 Meetings"
        const val DEFAULT_NOTES_HEADING = "# 📝 Notes"
    }
}
