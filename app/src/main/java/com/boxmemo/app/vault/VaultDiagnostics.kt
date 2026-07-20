package com.boxmemo.app.vault

import java.time.LocalDate

/** A daily-note file that resolved on disk, with its content, for analysis. */
data class DailyNoteSample(val date: LocalDate, val content: String)

/**
 * What a scan of the user's recent daily notes concluded about whether the
 * app is correctly wired to their vault. Drives the "no meetings anywhere"
 * warning and its one-tap recommendations.
 */
sealed interface VaultDiagnosis {

    /** No vault configured yet — nothing to diagnose. */
    object NotConfigured : VaultDiagnosis

    /** Recent notes resolve and the meetings section is found — healthy. */
    data class Healthy(val notesSampled: Int, val notesWithMeetings: Int) : VaultDiagnosis

    /**
     * Recent notes resolve but none contain the configured meetings heading, so
     * meetings will never show. Carries recommendations detected from the notes.
     */
    data class HeadingMismatch(
        val notesSampled: Int,
        val recommendedMeetingsHeading: String?,
        val recommendedNotesHeading: String?,
        val headingsSeen: List<String>,
    ) : VaultDiagnosis

    /**
     * No daily-note files resolved at all with the current vault root + folder
     * template — the path is probably wrong. If a date-named note was found
     * elsewhere under the vault, [recommendedTemplate] suggests a corrected one.
     */
    data class NoNotesFound(
        val daysChecked: Int,
        val recommendedTemplate: String?,
        val foundExampleRelPath: String?,
    ) : VaultDiagnosis
}

/** Pure analysis used by the (Android-facing) scanner; kept here for unit testing. */
object VaultDiagnostics {

    private val MEETING_LINE = Regex("""^- (\d{2}:\d{2}) - (\d{2}:\d{2}): .+$""")
    private val DATED_FILE = Regex("""(\d{4})-(\d{2})-(\d{2})\.md$""")

    /**
     * Concludes a diagnosis from the [samples] that resolved on disk. When no
     * sample contains the configured meetings heading, detects where meeting
     * times actually live and recommends that heading instead.
     */
    fun analyzeSamples(
        samples: List<DailyNoteSample>,
        meetingsHeading: String,
        notesHeading: String,
    ): VaultDiagnosis {
        val withMeetingsSection = samples.count {
            parseMeetingsSection(it.content, meetingsHeading) is MeetingSectionParseResult.Found
        }
        if (withMeetingsSection > 0) {
            return VaultDiagnosis.Healthy(samples.size, withMeetingsSection)
        }

        // No sample has the configured meetings heading — recommend a better one.
        val detectedMeetings = samples.firstNotNullOfOrNull { detectMeetingsHeading(it.content) }
        val detectedNotes = samples.firstNotNullOfOrNull { detectNotesHeading(it.content) }
        val headingsSeen = samples
            .flatMap { it.content.lines() }
            .mapNotNull { SectionHeading.headingText(it) }
            .distinct()
        return VaultDiagnosis.HeadingMismatch(
            notesSampled = samples.size,
            recommendedMeetingsHeading = detectedMeetings?.takeIf { !SectionHeading.matches(it, meetingsHeading) },
            recommendedNotesHeading = detectedNotes?.takeIf { !SectionHeading.matches(it, notesHeading) },
            headingsSeen = headingsSeen,
        )
    }

    /**
     * The full heading line (e.g. "# Calendar") directly above the first
     * `HH:MM - HH:MM:` meeting line in [content], or null if there are none.
     * This is where the user's meetings actually live.
     */
    fun detectMeetingsHeading(content: String): String? {
        val lines = content.lines()
        val firstMeeting = lines.indexOfFirst { line ->
            MEETING_LINE.matchEntire(line)?.let {
                isValidClockTime(it.groupValues[1]) && isValidClockTime(it.groupValues[2])
            } == true
        }
        if (firstMeeting == -1) return null
        for (i in firstMeeting - 1 downTo 0) {
            if (SectionHeading.headingText(lines[i]) != null) return lines[i].trim()
        }
        return null
    }

    /**
     * The full heading line of the most likely notes section: the first heading
     * whose text contains "note" (case-insensitive), or null. Deliberately
     * conservative — we don't want to point notes at an arbitrary bullet list.
     */
    fun detectNotesHeading(content: String): String? =
        content.lines().firstOrNull {
            val text = SectionHeading.headingText(it)
            text != null && text.contains("note", ignoreCase = true)
        }?.trim()

    /**
     * Given a date-named note found at [relPath] (relative to the vault root)
     * for [date], rebuilds a subpath template by replacing the date's parts with
     * {year}/{monthFolder}/{isoDate} tokens. Returns null if the filename's date
     * couldn't be tokenised (so the template would never resolve other days).
     */
    fun inferTemplate(relPath: String, date: LocalDate): String? {
        val normalized = relPath.replace('\\', '/').trimStart('/')
        if (!DATED_FILE.containsMatchIn(normalized)) return null

        var template = normalized
            // isoDate first — it contains the year, which we tokenise separately.
            .replace(date.toString(), "{isoDate}")
            .replace(VaultSettings.monthFolderFor(date), "{monthFolder}")
            .replace(date.year.toString(), "{year}")

        // The filename itself must vary by day, or every date maps to one file.
        if (!template.contains("{isoDate}")) return null
        return template
    }

    /** Extracts the date encoded in a daily-note file name, or null. */
    fun dateFromFileName(fileName: String): LocalDate? {
        val m = DATED_FILE.find(fileName) ?: return null
        return runCatching {
            LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
        }.getOrNull()
    }
}
