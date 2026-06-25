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
        val outcome = repo.addMeeting(date, MeetingEntry("14:00", "14:30", "Kickoff", emptyList()))

        assertEquals(NoteWriteOutcome.Written, outcome)
        val entries = (repo.readMeetings(date) as MeetingSectionParseResult.Found).entries
        assertEquals(listOf("Standup", "Kickoff"), entries.map { it.title })
    }

    @Test
    fun `addMeeting reports NoteMissing and writes nothing when the day's note does not exist`() {
        val date = LocalDate.of(2026, 6, 17)
        val repo = DailyNoteRepository(VaultSettings(tempFolder.root.path))

        val outcome = repo.addMeeting(date, MeetingEntry("14:00", "14:30", "Kickoff", emptyList()))

        assertEquals(NoteWriteOutcome.NoteMissing, outcome)
        assertTrue(repo.readNote(date) is DailyNoteReadResult.NoteDoesNotExist)
    }

    @Test
    fun `addNote reports NoteMissing when the day's note does not exist`() {
        val date = LocalDate.of(2026, 6, 17)
        val repo = DailyNoteRepository(VaultSettings(tempFolder.root.path))

        val outcome = repo.addNote(date, "remember to follow up")

        assertEquals(NoteWriteOutcome.NoteMissing, outcome)
    }

    @Test
    fun `addMeeting reports SectionMissing and writes nothing when the note has no Meetings section`() {
        val date = LocalDate.of(2026, 6, 17)
        val noteFile = tempFolder.newFolder("Periodic Notes", "Daily Notes", "2026", "06 - June")
            .resolve("2026-06-17.md")
        noteFile.writeText("# 📝 Notes\n")

        val repo = DailyNoteRepository(VaultSettings(tempFolder.root.path))
        val outcome = repo.addMeeting(date, MeetingEntry("14:00", "14:30", "Kickoff", emptyList()))

        assertEquals(NoteWriteOutcome.SectionMissing, outcome)
        assertEquals("# 📝 Notes\n", noteFile.readText())
    }

    @Test
    fun `addNote appends a plain bullet to the Notes section via quick-add`() {
        val date = LocalDate.of(2026, 6, 17)
        val noteFile = tempFolder.newFolder("Periodic Notes", "Daily Notes", "2026", "06 - June")
            .resolve("2026-06-17.md")
        noteFile.writeText("# 📝 Notes\n---\n# 👥 Meetings\n")

        val repo = DailyNoteRepository(VaultSettings(tempFolder.root.path))
        val outcome = repo.addNote(date, "remember to follow up")

        assertEquals(NoteWriteOutcome.Written, outcome)
        assertTrue(noteFile.readText().contains("- remember to follow up"))
    }

    @Test
    fun `addMeetingDetailBullets writes converted bullets under the matching meeting`() {
        val date = LocalDate.of(2026, 6, 17)
        val noteFile = tempFolder.newFolder("Periodic Notes", "Daily Notes", "2026", "06 - June")
            .resolve("2026-06-17.md")
        noteFile.writeText("# 👥 Meetings\n\n- 09:00 - 09:30: Standup\n---\n# Memos\n")

        val repo = DailyNoteRepository(VaultSettings(tempFolder.root.path))
        val outcome = repo.addMeetingDetailBullets(date, "09:00", "09:30", "Standup", listOf("\t- Converted point"))

        assertEquals(NoteWriteOutcome.Written, outcome)
        val entries = (repo.readMeetings(date) as MeetingSectionParseResult.Found).entries
        assertEquals(listOf("\t- Converted point"), entries.single().detailLines)
    }

    @Test
    fun `addMeetingDetailBullets writes to the right meeting when two share a start time`() {
        val date = LocalDate.of(2026, 6, 17)
        val noteFile = tempFolder.newFolder("Periodic Notes", "Daily Notes", "2026", "06 - June")
            .resolve("2026-06-17.md")
        noteFile.writeText(
            "# 👥 Meetings\n\n- 09:00 - 09:30: Standup\n- 09:00 - 09:30: Parallel sync\n---\n# Memos\n",
        )

        val repo = DailyNoteRepository(VaultSettings(tempFolder.root.path))
        val outcome = repo.addMeetingDetailBullets(date, "09:00", "09:30", "Parallel sync", listOf("\t- Second meeting note"))

        assertEquals(NoteWriteOutcome.Written, outcome)
        val entries = (repo.readMeetings(date) as MeetingSectionParseResult.Found).entries
        assertEquals(emptyList<String>(), entries[0].detailLines)
        assertEquals(listOf("\t- Second meeting note"), entries[1].detailLines)
    }

    @Test
    fun `addMeetingDetailBullets refuses to write when two meetings share the same identity`() {
        val date = LocalDate.of(2026, 6, 17)
        val original = "# 👥 Meetings\n\n- 09:00 - 09:30: Standup\n- 09:00 - 09:30: Standup\n---\n# Memos\n"
        val noteFile = tempFolder.newFolder("Periodic Notes", "Daily Notes", "2026", "06 - June")
            .resolve("2026-06-17.md")
        noteFile.writeText(original)

        val repo = DailyNoteRepository(VaultSettings(tempFolder.root.path))
        val outcome = repo.addMeetingDetailBullets(date, "09:00", "09:30", "Standup", listOf("\t- x"))

        assertEquals(NoteWriteOutcome.AmbiguousMeeting, outcome)
        assertEquals(original, noteFile.readText())
    }

    @Test
    fun `createNote renders the configured Templater template into a new note, creating folders`() {
        val date = LocalDate.of(2026, 6, 25)
        val template = tempFolder.newFile("DailyTemplate.md")
        template.writeText("# <% tp.file.title %>\n\nDate:: <% tp.date.now(\"YYYY-MM-DD\") %>\n# 👥 Meetings\n")

        val repo = DailyNoteRepository(
            VaultSettings(tempFolder.root.path, dailyNoteTemplatePath = "DailyTemplate.md"),
        )

        val outcome = repo.createNote(date)

        assertEquals(NoteCreateOutcome.Created(usedTemplate = true), outcome)
        val noteFile = tempFolder.root
            .resolve("Periodic Notes/Daily Notes/2026/06 - June/2026-06-25.md")
        assertTrue(noteFile.exists())
        assertEquals("# 2026-06-25\n\nDate:: 2026-06-25\n# 👥 Meetings\n", noteFile.readText())
    }

    @Test
    fun `createNote falls back to a heading scaffold when no template is configured`() {
        val date = LocalDate.of(2026, 6, 25)
        val repo = DailyNoteRepository(VaultSettings(tempFolder.root.path))

        val outcome = repo.createNote(date)

        assertEquals(NoteCreateOutcome.Created(usedTemplate = false), outcome)
        val noteFile = tempFolder.root
            .resolve("Periodic Notes/Daily Notes/2026/06 - June/2026-06-25.md")
        assertEquals("# 👥 Meetings\n\n# 📝 Notes\n", noteFile.readText())
    }

    @Test
    fun `createNote refuses to overwrite an existing note`() {
        val date = LocalDate.of(2026, 6, 25)
        val noteFile = tempFolder.newFolder("Periodic Notes", "Daily Notes", "2026", "06 - June")
            .resolve("2026-06-25.md")
        noteFile.writeText("existing content")

        val repo = DailyNoteRepository(VaultSettings(tempFolder.root.path))
        val outcome = repo.createNote(date)

        assertEquals(NoteCreateOutcome.AlreadyExists, outcome)
        assertEquals("existing content", noteFile.readText())
    }

    @Test
    fun `createNote falls back to the scaffold when the configured template is missing`() {
        val date = LocalDate.of(2026, 6, 25)
        val repo = DailyNoteRepository(
            VaultSettings(tempFolder.root.path, dailyNoteTemplatePath = "Templates/Nope.md"),
        )

        val outcome = repo.createNote(date)

        assertEquals(NoteCreateOutcome.Created(usedTemplate = false), outcome)
    }

    @Test
    fun `createNote reports vault not configured when no vault root is set`() {
        val repo = DailyNoteRepository(VaultSettings(vaultRoot = null))

        assertEquals(NoteCreateOutcome.VaultNotConfigured, repo.createNote(LocalDate.of(2026, 6, 25)))
    }

    @Test
    fun `addNoteLines writes converted bullets under the Notes section`() {
        val date = LocalDate.of(2026, 6, 17)
        val noteFile = tempFolder.newFolder("Periodic Notes", "Daily Notes", "2026", "06 - June")
            .resolve("2026-06-17.md")
        noteFile.writeText("# 📝 Notes\n---\n# 👥 Meetings\n")

        val repo = DailyNoteRepository(VaultSettings(tempFolder.root.path))
        val outcome = repo.addNoteLines(date, listOf("- Converted point one", "- Converted point two"))

        assertEquals(NoteWriteOutcome.Written, outcome)
        val content = noteFile.readText()
        assertTrue(content.contains("- Converted point one"))
        assertTrue(content.contains("- Converted point two"))
    }
}
