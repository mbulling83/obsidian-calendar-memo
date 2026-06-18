package com.boxmemo.app.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeetingSectionWriterTest {

    private fun newEntry(start: String, end: String, title: String) =
        MeetingEntry(start, end, title, emptyList())

    @Test
    fun `inserts a meeting at the chronologically correct position among existing meetings`() {
        val content = """
            # 👥 Meetings

            - 09:00 - 09:30: Standup
            	- Discussed release timeline
            - 14:00 - 15:00: Design review
            ---
            # Memos
        """.trimIndent()

        val result = insertMeeting(content, newEntry("11:00", "11:30", "1:1 with Sam")) as MeetingWriteResult.Updated

        val parsed = (parseMeetingsSection(result.content) as MeetingSectionParseResult.Found).entries
        assertEquals(listOf("Standup", "1:1 with Sam", "Design review"), parsed.map { it.title })
        // Existing entries' own detail bullets are untouched.
        assertEquals(listOf("\t- Discussed release timeline"), parsed[0].detailLines)
    }

    @Test
    fun `covers AE4 - adding to an empty Meetings section produces a single correctly-formatted line`() {
        val content = """
            # 👥 Meetings
            ---
            # Memos
        """.trimIndent()

        val result = insertMeeting(content, newEntry("09:00", "09:30", "Kickoff")) as MeetingWriteResult.Updated

        assertTrue(result.content.contains("- 09:00 - 09:30: Kickoff"))
        val parsed = (parseMeetingsSection(result.content) as MeetingSectionParseResult.Found).entries
        assertEquals(1, parsed.size)
        assertEquals("Kickoff", parsed.single().title)
    }

    @Test
    fun `a tied start time inserts adjacent without merging or overwriting the existing entry`() {
        val content = """
            # 👥 Meetings

            - 09:00 - 09:30: Standup
            ---
            # Memos
        """.trimIndent()

        val result = insertMeeting(content, newEntry("09:00", "09:15", "Quick sync")) as MeetingWriteResult.Updated

        val parsed = (parseMeetingsSection(result.content) as MeetingSectionParseResult.Found).entries
        assertEquals(2, parsed.size)
        assertEquals(setOf("Standup", "Quick sync"), parsed.map { it.title }.toSet())
    }

    @Test
    fun `returns SectionNotFound when the daily note has no Meetings heading`() {
        val content = """
            # 📝 Notes
            ---
            # Memos
        """.trimIndent()

        val result = insertMeeting(content, newEntry("09:00", "09:30", "Kickoff"))

        assertEquals(MeetingWriteResult.SectionNotFound, result)
    }
}
