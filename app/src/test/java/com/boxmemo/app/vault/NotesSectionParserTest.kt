package com.boxmemo.app.vault

import org.junit.Assert.assertEquals
import org.junit.Test

class NotesSectionParserTest {

    @Test
    fun `appends a plain bullet under the Notes heading`() {
        val content = """
            # 📝 Notes
            ---
            # 👥 Meetings
        """.trimIndent()

        val result = appendNoteBullet(content, "Demoing placeholder text") as NoteWriteResult.Updated

        val notesSection = result.content
            .substringAfter("# 📝 Notes")
            .substringBefore("---")
        assertEquals(true, notesSection.contains("- Demoing placeholder text"))
    }

    @Test
    fun `appends after existing notes without disturbing them`() {
        val content = """
            # 📝 Notes
            - existing note
            ---
            # 👥 Meetings
        """.trimIndent()

        val result = appendNoteBullet(content, "new note") as NoteWriteResult.Updated

        val notesSection = result.content.substringAfter("# 📝 Notes").substringBefore("---")
        assertEquals(true, notesSection.contains("- existing note"))
        assertEquals(true, notesSection.contains("- new note"))
        assertEquals(
            notesSection.indexOf("existing note") < notesSection.indexOf("new note"),
            true,
        )
    }

    @Test
    fun `returns SectionNotFound when the daily note has no Notes heading`() {
        val content = """
            # 👥 Meetings
            ---
            # Memos
        """.trimIndent()

        val result = appendNoteBullet(content, "new note")

        assertEquals(NoteWriteResult.SectionNotFound, result)
    }
}
