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
    fun `returns SectionNotFound when no meeting matches the given start time`() {
        val content = """
            # 👥 Meetings

            - 09:00 - 09:30: Standup
            ---
            # Memos
        """.trimIndent()

        val result = insertMeetingDetailBullets(content, startTime = "11:00", bulletLines = listOf("\t- x"))

        assertTrue(result is MeetingWriteResult.SectionNotFound)
    }
}
