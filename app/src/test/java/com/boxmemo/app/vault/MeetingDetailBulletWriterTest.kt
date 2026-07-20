package com.boxmemo.app.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeetingDetailBulletWriterTest {

    @Test
    fun `covers AE1 - appends bullets under the matching meeting's existing line, leaving others untouched`() {
        val content = """
            # 👥 Meetings

            - 09:00 - 09:30: Standup
            	- Discussed release timeline
            - 14:00 - 15:00: Design review
            	- Walked through mockups
            ---
            # Memos
        """.trimIndent()

        val result = insertMeetingDetailBullets(
            content,
            startTime = "09:00",
            endTime = "09:30",
            title = "Standup",
            bulletLines = listOf("\t- New point one", "\t- New point two"),
        ) as MeetingWriteResult.Updated

        val parsed = (parseMeetingsSection(result.content) as MeetingSectionParseResult.Found).entries
        assertEquals(
            listOf("\t- Discussed release timeline", "\t- New point one", "\t- New point two"),
            parsed[0].detailLines,
        )
        // The other meeting's bullets are untouched.
        assertEquals(listOf("\t- Walked through mockups"), parsed[1].detailLines)
    }

    @Test
    fun `targets the correct meeting when two share the same start time but differ in title`() {
        val content = """
            # 👥 Meetings

            - 09:00 - 09:30: Standup
            - 09:00 - 09:30: Parallel sync
            ---
            # Memos
        """.trimIndent()

        // The two meetings share a start time; title disambiguates them.
        val result = insertMeetingDetailBullets(
            content,
            startTime = "09:00",
            endTime = "09:30",
            title = "Parallel sync",
            bulletLines = listOf("\t- Goes on the second meeting"),
        ) as MeetingWriteResult.Updated

        val parsed = (parseMeetingsSection(result.content) as MeetingSectionParseResult.Found).entries
        assertEquals(emptyList<String>(), parsed[0].detailLines)
        assertEquals(listOf("\t- Goes on the second meeting"), parsed[1].detailLines)
    }

    @Test
    fun `appending to the last meeting lands under its block and keeps the trailing newline`() {
        val content = "# 👥 Meetings\n- 09:00 - 09:30: Standup\n"

        val result = insertMeetingDetailBullets(
            content,
            startTime = "09:00",
            endTime = "09:30",
            title = "Standup",
            bulletLines = listOf("\t- New point"),
        ) as MeetingWriteResult.Updated

        assertEquals("# 👥 Meetings\n- 09:00 - 09:30: Standup\n\t- New point\n", result.content)
    }

    @Test
    fun `returns MeetingNotFound when no meeting matches the identity`() {
        val content = """
            # 👥 Meetings

            - 09:00 - 09:30: Standup
            ---
            # Memos
        """.trimIndent()

        val result = insertMeetingDetailBullets(
            content,
            startTime = "10:00",
            endTime = "10:30",
            title = "Nope",
            bulletLines = listOf("\t- x"),
        )

        assertTrue(result is MeetingWriteResult.MeetingNotFound)
    }

    @Test
    fun `returns AmbiguousMeeting when two meetings share start, end and title`() {
        val content = """
            # 👥 Meetings

            - 09:00 - 09:30: Standup
            - 09:00 - 09:30: Standup
            ---
            # Memos
        """.trimIndent()

        val result = insertMeetingDetailBullets(
            content,
            startTime = "09:00",
            endTime = "09:30",
            title = "Standup",
            bulletLines = listOf("\t- x"),
        )

        assertTrue(result is MeetingWriteResult.AmbiguousMeeting)
    }
}
