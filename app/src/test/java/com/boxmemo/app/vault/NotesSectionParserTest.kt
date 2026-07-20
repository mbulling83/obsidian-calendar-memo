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
    fun `appends and reads a custom notes heading, forgiving of hash level and case`() {
        val content = """
            # Meetings
            - 09:00 - 09:30: Standup
            ---
            ## journal
        """.trimIndent()

        val appended = appendNoteBullet(content, "scratch", heading = "Journal") as NoteWriteResult.Updated
        val read = parseNotesSection(appended.content, heading = "# journal")

        assertEquals(listOf("- scratch"), read)
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
    fun `appending to a file ending in a newline lands under the last bullet and keeps the trailing newline`() {
        val result = appendNoteLines("# 📝 Notes\n- a\n", listOf("- b")) as NoteWriteResult.Updated

        assertEquals("# 📝 Notes\n- a\n- b\n", result.content)
    }

    @Test
    fun `appending skips back over trailing blank lines in the section`() {
        val content = "# 📝 Notes\n- a\n\n\n---\n# 👥 Meetings\n"

        val result = appendNoteBullet(content, "b") as NoteWriteResult.Updated

        assertEquals("# 📝 Notes\n- a\n- b\n\n\n---\n# 👥 Meetings\n", result.content)
    }

    @Test
    fun `reads existing note bullets, dropping blank lines`() {
        val content = """
            # 📝 Notes
            - first note
            - second note

            ---
            # 👥 Meetings
            09:00 - 10:00: Standup
        """.trimIndent()

        assertEquals(listOf("- first note", "- second note"), parseNotesSection(content))
    }

    @Test
    fun `reads notes through end of file when no trailing section`() {
        val content = """
            # 📝 Notes
            - only note
        """.trimIndent()

        assertEquals(listOf("- only note"), parseNotesSection(content))
    }

    @Test
    fun `returns empty list when there is no Notes heading`() {
        val content = """
            # 👥 Meetings
            09:00 - 10:00: Standup
        """.trimIndent()

        assertEquals(emptyList<String>(), parseNotesSection(content))
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
