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
    val vaultRoot: String?,
    /** Daily-note subpath template with {year}/{monthFolder}/{isoDate} tokens. */
    val dailyNoteSubpathTemplate: String = DEFAULT_TEMPLATE,
    /** Configured meetings-section heading; matched forgivingly (see [SectionHeading]). */
    val meetingsHeading: String = DEFAULT_MEETINGS_HEADING,
    /** Configured notes-section heading; matched forgivingly (see [SectionHeading]). */
    val notesHeading: String = DEFAULT_NOTES_HEADING,
    /**
     * Path to the Templater template used when the app creates a missing daily
     * note — relative to [vaultRoot] (e.g. "Templates/Daily Note.md") or
     * absolute. Null/blank means "no template": new notes get a minimal
     * [defaultNoteScaffold] of the configured section headings instead.
     */
    val dailyNoteTemplatePath: String? = null,
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

    /**
     * Resolves the configured daily-note template file, or null if none is set.
     * A relative path is resolved against [vaultRoot]; an absolute path is used
     * as-is so a template stored outside the vault still works.
     */
    fun resolveTemplateFile(): File? {
        val path = dailyNoteTemplatePath?.takeIf { it.isNotBlank() } ?: return null
        val file = File(path)
        if (file.isAbsolute) return file
        val root = vaultRoot?.takeIf { it.isNotBlank() } ?: return null
        return File(root, path)
    }

    /**
     * The fallback body for a new daily note when no template is configured (or
     * the template can't be read): just the two configured section headings, so
     * the parsers and quick-add have somewhere to write.
     */
    fun defaultNoteScaffold(): String = "$meetingsHeading\n\n$notesHeading\n"

    private fun renderTemplate(template: String, date: LocalDate): String {
        return template
            .replace("{year}", date.year.toString())
            .replace("{monthFolder}", monthFolderFor(date))
            .replace("{isoDate}", date.toString())
    }

    companion object {

        /** The `{monthFolder}` token's value for [date], e.g. "06 - June". */
        fun monthFolderFor(date: LocalDate): String {
            val monthName = date.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
            return "%02d - %s".format(date.monthValue, monthName)
        }

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
