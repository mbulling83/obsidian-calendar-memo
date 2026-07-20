package com.boxmemo.app.vault

private val MEETING_LINE = Regex("""^- (\d{2}:\d{2}) - (\d{2}:\d{2}): (.+)$""")
private val DETAIL_LINE = Regex("""^[\t ]+- """)

/** True when an `HH:MM` string is a real time — downstream does `LocalTime.parse`, which throws on `24:00`. */
internal fun isValidClockTime(time: String): Boolean {
    val hour = time.substring(0, 2).toIntOrNull() ?: return false
    val minute = time.substring(3, 5).toIntOrNull() ?: return false
    return hour <= 23 && minute <= 59
}

/**
 * Matches [line] as a meeting line, but only when both times are in range —
 * a `- 24:00 - 25:00: …` line is treated as non-meeting content, not an entry.
 */
private fun meetingLineMatch(line: String): MatchResult? =
    MEETING_LINE.matchEntire(line)?.takeIf {
        isValidClockTime(it.groupValues[1]) && isValidClockTime(it.groupValues[2])
    }

/**
 * Walks [index] back over blank lines (including the empty last element a
 * trailing `"\n"` produces when splitting) so an insert at a section/block end
 * lands directly after the last non-blank line — never below trailing blanks,
 * and never past [floor].
 */
internal fun backOverBlankLines(lines: List<String>, index: Int, floor: Int): Int {
    var i = index
    while (i > floor && lines[i - 1].isBlank()) i--
    return i
}

data class MeetingEntry(
    val startTime: String,
    val endTime: String,
    val title: String,
    val detailLines: List<String>,
)

sealed interface MeetingSectionParseResult {
    data class Found(val entries: List<MeetingEntry>) : MeetingSectionParseResult
    object SectionNotFound : MeetingSectionParseResult
}

sealed interface MeetingWriteResult {
    data class Updated(val content: String) : MeetingWriteResult
    object SectionNotFound : MeetingWriteResult

    /** No meeting in the section matches the requested start/end/title. */
    object MeetingNotFound : MeetingWriteResult

    /** More than one meeting matches the requested start/end/title — refuse rather than guess. */
    object AmbiguousMeeting : MeetingWriteResult
}

private data class SectionBounds(val headingIndex: Int, val sectionEnd: Int)

/** Locates the meetings [heading] and the exclusive end of its section, or null if absent. */
private fun findMeetingsSectionBounds(lines: List<String>, heading: String): SectionBounds? {
    val headingIndex = lines.indexOfFirst { SectionHeading.matches(it, heading) }
    if (headingIndex == -1) return null

    val sectionEnd = lines
        .withIndex()
        .drop(headingIndex + 1)
        .firstOrNull { (_, line) -> SectionHeading.isSectionBoundary(line) }
        ?.index
        ?: lines.size

    return SectionBounds(headingIndex, sectionEnd)
}

/**
 * Parses the `# 👥 Meetings` section of a daily note into structured entries,
 * without reading or mutating any other section (Notes, Memos, Dataview
 * blocks). A meeting is a non-indented `- HH:MM - HH:MM: Title` bullet;
 * subsequent indented bullet lines (tab or space indented) belong to that
 * meeting as detail lines, until the next meeting line or the section end.
 */
fun parseMeetingsSection(
    noteContent: String,
    heading: String = VaultSettings.DEFAULT_MEETINGS_HEADING,
): MeetingSectionParseResult {
    val lines = noteContent.lines()
    val bounds = findMeetingsSectionBounds(lines, heading)
        ?: return MeetingSectionParseResult.SectionNotFound
    val (headingIndex, sectionEnd) = bounds

    val sectionLines = lines.subList(headingIndex + 1, sectionEnd)

    val entries = mutableListOf<MeetingEntry>()
    var currentDetailLines: MutableList<String>? = null

    fun flushCurrent(start: String, end: String, title: String) {
        entries.add(MeetingEntry(start, end, title, currentDetailLines.orEmpty()))
    }

    var pendingMeeting: Triple<String, String, String>? = null

    for (line in sectionLines) {
        val meetingMatch = meetingLineMatch(line)
        when {
            meetingMatch != null -> {
                pendingMeeting?.let { (start, end, title) -> flushCurrent(start, end, title) }
                val (start, end, title) = meetingMatch.destructured
                pendingMeeting = Triple(start, end, title)
                currentDetailLines = mutableListOf()
            }
            pendingMeeting != null && DETAIL_LINE.containsMatchIn(line) -> {
                currentDetailLines?.add(line)
            }
            else -> {
                // Blank lines or non-bullet content between/around entries are ignored.
            }
        }
    }
    pendingMeeting?.let { (start, end, title) -> flushCurrent(start, end, title) }

    return MeetingSectionParseResult.Found(entries)
}

private fun formatMeetingLine(entry: MeetingEntry): String =
    "- ${entry.startTime} - ${entry.endTime}: ${entry.title}"

/**
 * Inserts [newEntry] into the `# 👥 Meetings` section in chronological order,
 * leaving every existing entry (and its detail bullets) untouched. Ties are
 * placed immediately after the existing entry with the same start time,
 * never merged into it. New entries carry no detail bullets (R5/R6 — quick-add
 * is form-only).
 */
fun insertMeeting(
    noteContent: String,
    newEntry: MeetingEntry,
    heading: String = VaultSettings.DEFAULT_MEETINGS_HEADING,
): MeetingWriteResult {
    val lines = noteContent.lines().toMutableList()
    val bounds = findMeetingsSectionBounds(lines, heading) ?: return MeetingWriteResult.SectionNotFound
    val (headingIndex, sectionEnd) = bounds

    // Anchor = (absolute line index of a meeting line, its startTime).
    val anchors = (headingIndex + 1 until sectionEnd)
        .mapNotNull { i -> meetingLineMatch(lines[i])?.let { i to it.groupValues[1] } }

    val insertionIndex = when {
        anchors.isEmpty() -> headingIndex + 1
        else -> {
            val targetAnchor = anchors.lastOrNull { (_, startTime) -> startTime <= newEntry.startTime }
            if (targetAnchor == null) {
                anchors.first().first
            } else {
                val targetAnchorPos = anchors.indexOf(targetAnchor)
                // End of the target anchor's block = next anchor's line, or the
                // section end minus any trailing blanks (so an append lands
                // directly under the last entry and a trailing "\n" survives).
                anchors.getOrNull(targetAnchorPos + 1)?.first
                    ?: backOverBlankLines(lines, sectionEnd, targetAnchor.first + 1)
            }
        }
    }

    lines.add(insertionIndex, formatMeetingLine(newEntry))
    return MeetingWriteResult.Updated(lines.joinToString("\n"))
}

/**
 * Appends [bulletLines] as the last detail lines under the meeting whose
 * start/end/title match [startTime], [endTime] and [title]. Matching on the
 * meeting's content identity (rather than a file-order index) keeps the write
 * correct even if the section was re-ordered on disk — e.g. by a concurrent
 * LiveSync — between the day view being read and this write. Start times are
 * not unique within a day, so end time and title are included to disambiguate.
 * Existing bullets and every other meeting are left untouched (origin AE1).
 *
 * Returns [MeetingWriteResult.SectionNotFound] if the section is absent,
 * [MeetingWriteResult.MeetingNotFound] if no meeting matches, or
 * [MeetingWriteResult.AmbiguousMeeting] if more than one does — no write
 * occurs in any of those cases.
 */
fun insertMeetingDetailBullets(
    noteContent: String,
    startTime: String,
    endTime: String,
    title: String,
    bulletLines: List<String>,
    heading: String = VaultSettings.DEFAULT_MEETINGS_HEADING,
): MeetingWriteResult {
    val lines = noteContent.lines().toMutableList()
    val bounds = findMeetingsSectionBounds(lines, heading) ?: return MeetingWriteResult.SectionNotFound
    val (headingIndex, sectionEnd) = bounds

    val anchors = (headingIndex + 1 until sectionEnd)
        .filter { i -> meetingLineMatch(lines[i]) != null }

    val matching = anchors.filter { i ->
        val match = MEETING_LINE.matchEntire(lines[i])!!
        match.groupValues[1] == startTime &&
            match.groupValues[2] == endTime &&
            match.groupValues[3] == title
    }

    when {
        matching.isEmpty() -> return MeetingWriteResult.MeetingNotFound
        matching.size > 1 -> return MeetingWriteResult.AmbiguousMeeting
    }

    val anchorLine = matching.single()
    val anchorPos = anchors.indexOf(anchorLine)
    // End of this meeting's block = the next meeting line, or the section end
    // minus any trailing blanks (so bullets land directly under the block and
    // a trailing "\n" survives).
    val blockEnd = anchors.getOrNull(anchorPos + 1)
        ?: backOverBlankLines(lines, sectionEnd, anchorLine + 1)
    lines.addAll(blockEnd, bulletLines)
    return MeetingWriteResult.Updated(lines.joinToString("\n"))
}
