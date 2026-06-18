package com.boxmemo.app.vault

private const val MEETINGS_HEADING = "# 👥 Meetings"
private val MEETING_LINE = Regex("""^- (\d{2}:\d{2}) - (\d{2}:\d{2}): (.+)$""")
private val DETAIL_LINE = Regex("""^[\t ]+- """)

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
}

private data class SectionBounds(val headingIndex: Int, val sectionEnd: Int)

/** Locates the `# 👥 Meetings` heading and the exclusive end of its section, or null if absent. */
private fun findMeetingsSectionBounds(lines: List<String>): SectionBounds? {
    val headingIndex = lines.indexOfFirst { it.trim() == MEETINGS_HEADING }
    if (headingIndex == -1) return null

    val sectionEnd = lines
        .withIndex()
        .drop(headingIndex + 1)
        .firstOrNull { (_, line) -> line.trim() == "---" || line.trim().startsWith("# ") }
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
fun parseMeetingsSection(noteContent: String): MeetingSectionParseResult {
    val lines = noteContent.lines()
    val bounds = findMeetingsSectionBounds(lines)
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
        val meetingMatch = MEETING_LINE.matchEntire(line)
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
fun insertMeeting(noteContent: String, newEntry: MeetingEntry): MeetingWriteResult {
    val lines = noteContent.lines().toMutableList()
    val bounds = findMeetingsSectionBounds(lines) ?: return MeetingWriteResult.SectionNotFound
    val (headingIndex, sectionEnd) = bounds

    // Anchor = (absolute line index of a meeting line, its startTime).
    val anchors = (headingIndex + 1 until sectionEnd)
        .mapNotNull { i -> MEETING_LINE.matchEntire(lines[i])?.let { i to it.groupValues[1] } }

    val insertionIndex = when {
        anchors.isEmpty() -> headingIndex + 1
        else -> {
            val targetAnchor = anchors.lastOrNull { (_, startTime) -> startTime <= newEntry.startTime }
            if (targetAnchor == null) {
                anchors.first().first
            } else {
                val targetAnchorPos = anchors.indexOf(targetAnchor)
                // End of the target anchor's block = next anchor's line, or section end.
                anchors.getOrNull(targetAnchorPos + 1)?.first ?: sectionEnd
            }
        }
    }

    lines.add(insertionIndex, formatMeetingLine(newEntry))
    return MeetingWriteResult.Updated(lines.joinToString("\n"))
}
