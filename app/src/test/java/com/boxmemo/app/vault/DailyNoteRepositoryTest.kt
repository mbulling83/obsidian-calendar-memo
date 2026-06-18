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

    @Test
    fun `addMeeting writes a new meeting line into the existing note via quick-add`() {
        val date = LocalDate.of(2026, 6, 17)
        val noteFile = tempFolder.newFolder("Periodic Notes", "Daily Notes", "2026", "06 - June")
            .resolve("2026-06-17.md")
        noteFile.writeText("# 👥 Meetings\n\n- 09:00 - 09:30: Standup\n---\n# Memos\n")

        val repo = DailyNoteRepository(VaultSettings(tempFolder.root.path))
        val added = repo.addMeeting(date, MeetingEntry("14:00", "14:30", "Kickoff", emptyList()))

        assertTrue(added)
        val entries = (repo.readMeetings(date) as MeetingSectionParseResult.Found).entries
        assertEquals(listOf("Standup", "Kickoff"), entries.map { it.title })
    }

    @Test
    fun `addMeeting returns false and writes nothing when the note has no Meetings section`() {
        val date = LocalDate.of(2026, 6, 17)
        val noteFile = tempFolder.newFolder("Periodic Notes", "Daily Notes", "2026", "06 - June")
            .resolve("2026-06-17.md")
        noteFile.writeText("# 📝 Notes\n")

        val repo = DailyNoteRepository(VaultSettings(tempFolder.root.path))
        val added = repo.addMeeting(date, MeetingEntry("14:00", "14:30", "Kickoff", emptyList()))

        assertTrue(!added)
        assertEquals("# 📝 Notes\n", noteFile.readText())
    }

    @Test
    fun `addNote appends a plain bullet to the Notes section via quick-add`() {
        val date = LocalDate.of(2026, 6, 17)
        val noteFile = tempFolder.newFolder("Periodic Notes", "Daily Notes", "2026", "06 - June")
            .resolve("2026-06-17.md")
        noteFile.writeText("# 📝 Notes\n---\n# 👥 Meetings\n")

        val repo = DailyNoteRepository(VaultSettings(tempFolder.root.path))
        val added = repo.addNote(date, "remember to follow up")

        assertTrue(added)
        assertTrue(noteFile.readText().contains("- remember to follow up"))
    }

    @Test
    fun `addMeetingDetailBullets writes converted bullets under the matching meeting`() {
        val date = LocalDate.of(2026, 6, 17)
        val noteFile = tempFolder.newFolder("Periodic Notes", "Daily Notes", "2026", "06 - June")
            .resolve("2026-06-17.md")
        noteFile.writeText("# 👥 Meetings\n\n- 09:00 - 09:30: Standup\n---\n# Memos\n")

        val repo = DailyNoteRepository(VaultSettings(tempFolder.root.path))
        val added = repo.addMeetingDetailBullets(date, "09:00", listOf("\t- Converted point"))

        assertTrue(added)
        val entries = (repo.readMeetings(date) as MeetingSectionParseResult.Found).entries
        assertEquals(listOf("\t- Converted point"), entries.single().detailLines)
    }

    @Test
    fun `addNoteLines writes converted bullets under the Notes section`() {
        val date = LocalDate.of(2026, 6, 17)
        val noteFile = tempFolder.newFolder("Periodic Notes", "Daily Notes", "2026", "06 - June")
            .resolve("2026-06-17.md")
        noteFile.writeText("# 📝 Notes\n---\n# 👥 Meetings\n")

        val repo = DailyNoteRepository(VaultSettings(tempFolder.root.path))
        val added = repo.addNoteLines(date, listOf("- Converted point one", "- Converted point two"))

        assertTrue(added)
        val content = noteFile.readText()
        assertTrue(content.contains("- Converted point one"))
        assertTrue(content.contains("- Converted point two"))
    }
}
