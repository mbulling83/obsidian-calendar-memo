package com.boxmemo.app.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDate

class DailyNoteRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `reads an existing note's content`() {
        val date = LocalDate.of(2026, 6, 17)
        val noteFile = tempFolder.newFolder("Periodic Notes", "Daily Notes", "2026", "06 - June")
            .resolve("2026-06-17.md")
        noteFile.writeText("# 👥 Meetings\n- 09:00 - 09:30: Standup\n")

        val repo = DailyNoteRepository(VaultSettings(tempFolder.root.path))

        val result = repo.readNote(date)

        assertTrue(result is DailyNoteReadResult.Found)
        assertTrue((result as DailyNoteReadResult.Found).content.contains("Standup"))
    }

    @Test
    fun `reports note does not exist for a date with no file yet`() {
        val repo = DailyNoteRepository(VaultSettings(tempFolder.root.path))

        val result = repo.readNote(LocalDate.of(2026, 6, 20))

        assertEquals(DailyNoteReadResult.NoteDoesNotExist, result)
    }

    @Test
    fun `reports vault not configured when no vault root is set`() {
        val repo = DailyNoteRepository(VaultSettings(vaultRoot = null))

        val result = repo.readNote(LocalDate.of(2026, 6, 20))

        assertEquals(DailyNoteReadResult.VaultNotConfigured, result)
    }

    @Test
    fun `writeNote replaces content via write-then-replace without leaving a temp file behind`() {
        val date = LocalDate.of(2026, 6, 17)
        val noteDir = tempFolder.newFolder("Periodic Notes", "Daily Notes", "2026", "06 - June")
        val noteFile = noteDir.resolve("2026-06-17.md")
        noteFile.writeText("original")

        val repo = DailyNoteRepository(VaultSettings(tempFolder.root.path))
        val written = repo.writeNote(date, "updated content")

        assertTrue(written)
        assertEquals("updated content", noteFile.readText())
        assertTrue(noteDir.listFiles()!!.none { it.name.endsWith(".tmp") })
    }

    @Test
    fun `readMeetings parses the Meetings section of an existing note`() {
        val date = LocalDate.of(2026, 6, 17)
        val noteFile = tempFolder.newFolder("Periodic Notes", "Daily Notes", "2026", "06 - June")
            .resolve("2026-06-17.md")
        noteFile.writeText("# 👥 Meetings\n- 09:00 - 09:30: Standup\n")

        val repo = DailyNoteRepository(VaultSettings(tempFolder.root.path))

        val result = repo.readMeetings(date) as MeetingSectionParseResult.Found

        assertEquals("Standup", result.entries.single().title)
    }
}
