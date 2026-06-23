package com.boxmemo.app.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeetingSectionParserTest {

    // Trimmed from the user's real 2026-06-17.md, preserving exact bullet
    // markers and tab indentation.
    private val realDailyNote = """
        ---
        created: 2026-06-17 12:21
        tags:
          - dailynotes
        ---
        # 📝 Notes

        ---
        # 👥 Meetings

        - 12:30 - 13:00: 1:1 with [[Ioana Havsfrid]]
        	- Measurement proposals for Podcasts
        	- Video partnerships
        		- working with Apple, going to general availability next week.
        	- September
        		- Visit to Stockholm in the first couple of weeks.
        ---
        # Memos

        ---
        # Notes created today
        ```dataview
        List FROM "" WHERE file.cday = date("2026-06-17") SORT file.ctime asc
        ```
    """.trimIndent()

    @Test
    fun `parses a meeting line with nested detail bullets at multiple indentation levels`() {
        val result = parseMeetingsSection(realDailyNote)

        assertTrue(result is MeetingSectionParseResult.Found)
        val entries = (result as MeetingSectionParseResult.Found).entries
        assertEquals(1, entries.size)

        val entry = entries.single()
        assertEquals("12:30", entry.startTime)
        assertEquals("13:00", entry.endTime)
        assertEquals("1:1 with [[Ioana Havsfrid]]", entry.title)
        assertEquals(
            listOf(
                "\t- Measurement proposals for Podcasts",
                "\t- Video partnerships",
                "\t\t- working with Apple, going to general availability next week.",
                "\t- September",
                "\t\t- Visit to Stockholm in the first couple of weeks.",
            ),
            entry.detailLines,
        )
    }

    @Test
    fun `parses multiple meetings into distinct entries, each owning only its own bullets`() {
        val content = """
            # 👥 Meetings

            - 09:00 - 09:30: Standup
            	- Discussed release timeline
            - 14:00 - 15:00: Design review [[Project Alpha]]
            	- Walked through mockups
            	- Agreed on next steps
            ---
            # Memos
        """.trimIndent()

        val result = parseMeetingsSection(content) as MeetingSectionParseResult.Found

        assertEquals(2, result.entries.size)
        assertEquals("Standup", result.entries[0].title)
        assertEquals(listOf("\t- Discussed release timeline"), result.entries[0].detailLines)
        assertEquals("Design review [[Project Alpha]]", result.entries[1].title)
        assertEquals(
            listOf("\t- Walked through mockups", "\t- Agreed on next steps"),
            result.entries[1].detailLines,
        )
    }

    @Test
    fun `empty Meetings section with only the heading parses to an empty list`() {
        val content = """
            # 👥 Meetings
            -
            ---
            # Memos
        """.trimIndent()

        val result = parseMeetingsSection(content) as MeetingSectionParseResult.Found

        assertEquals(emptyList<MeetingEntry>(), result.entries)
    }

    @Test
    fun `daily note missing the Meetings heading returns SectionNotFound`() {
        val content = """
            # 📝 Notes
            - some note
            ---
            # Memos
        """.trimIndent()

        val result = parseMeetingsSection(content)

        assertEquals(MeetingSectionParseResult.SectionNotFound, result)
    }

    @Test
    fun `parses a custom meetings heading, forgiving of hash level and case`() {
        val content = """
            ## calendar
            - 09:00 - 09:30: Standup
            ---
            # Notes
        """.trimIndent()

        val result = parseMeetingsSection(content, heading = "Calendar") as MeetingSectionParseResult.Found

        assertEquals(1, result.entries.size)
        assertEquals("Standup", result.entries.single().title)
    }

    @Test
    fun `inserts a meeting under a custom heading`() {
        val content = """
            # Agenda
            - 09:00 - 09:30: Standup
        """.trimIndent()

        val result = insertMeeting(
            content,
            MeetingEntry("11:00", "11:30", "Review", emptyList()),
            heading = "# Agenda",
        )

        assertTrue(result is MeetingWriteResult.Updated)
        assertTrue((result as MeetingWriteResult.Updated).content.contains("- 11:00 - 11:30: Review"))
    }

    @Test
    fun `default heading still resolves the emoji section`() {
        // Regression: callers that don't pass a heading get the author's default.
        val result = parseMeetingsSection(realDailyNote) as MeetingSectionParseResult.Found
        assertEquals(1, result.entries.size)
    }

    @Test
    fun `parsing does not include content from Notes, Memos, or dataview sections`() {
        val result = parseMeetingsSection(realDailyNote) as MeetingSectionParseResult.Found

        val allText = result.entries.joinToString { it.title + it.detailLines.joinToString() }
        assertTrue(!allText.contains("dataview"))
        assertTrue(!allText.contains("Notes created today"))
    }
}
