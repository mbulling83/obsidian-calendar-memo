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

/**
 * Parses the `# 👥 Meetings` section of a daily note into structured entries,
 * without reading or mutating any other section (Notes, Memos, Dataview
 * blocks). A meeting is a non-indented `- HH:MM - HH:MM: Title` bullet;
 * subsequent indented bullet lines (tab or space indented) belong to that
 * meeting as detail lines, until the next meeting line or the section end.
 */
fun parseMeetingsSection(noteContent: String): MeetingSectionParseResult {
    val lines = noteContent.lines()
    val headingIndex = lines.indexOfFirst { it.trim() == MEETINGS_HEADING }
    if (headingIndex == -1) return MeetingSectionParseResult.SectionNotFound

    val sectionEnd = lines
        .withIndex()
        .drop(headingIndex + 1)
        .firstOrNull { (_, line) -> line.trim() == "---" || line.trim().startsWith("# ") }
        ?.index
        ?: lines.size

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
