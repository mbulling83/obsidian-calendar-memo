package com.boxmemo.app.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class VaultDiagnosticsTest {

    private fun sample(content: String, date: LocalDate = LocalDate.of(2026, 6, 23)) =
        DailyNoteSample(date, content.trimIndent())

    @Test
    fun `healthy when a recent note has the configured meetings section`() {
        val notes = listOf(
            sample(
                """
                # 👥 Meetings
                - 09:00 - 09:30: Standup
                # 📝 Notes
                - a note
                """,
            ),
        )

        val result = VaultDiagnostics.analyzeSamples(notes, "# 👥 Meetings", "# 📝 Notes")

        assertTrue(result is VaultDiagnosis.Healthy)
        assertEquals(1, (result as VaultDiagnosis.Healthy).notesWithMeetings)
    }

    @Test
    fun `healthy even when the meetings section is present but empty`() {
        val notes = listOf(sample("# 👥 Meetings\n\n# 📝 Notes\n- x"))
        val result = VaultDiagnostics.analyzeSamples(notes, "# 👥 Meetings", "# 📝 Notes")
        assertTrue(result is VaultDiagnosis.Healthy)
    }

    @Test
    fun `recommends the heading where meeting times actually live`() {
        // Configured heading is "# 👥 Meetings" but the user's notes use "# Calendar".
        val notes = listOf(
            sample(
                """
                # Calendar
                - 09:00 - 09:30: Standup
                - 14:00 - 15:00: Review
                # Daily Notes
                - something
                """,
            ),
        )

        val result = VaultDiagnostics.analyzeSamples(notes, "# 👥 Meetings", "# 📝 Notes")

        assertTrue(result is VaultDiagnosis.HeadingMismatch)
        result as VaultDiagnosis.HeadingMismatch
        assertEquals("# Calendar", result.recommendedMeetingsHeading)
        assertTrue(result.headingsSeen.contains("Calendar"))
    }

    @Test
    fun `recommends a notes heading containing the word note`() {
        val notes = listOf(
            sample(
                """
                # Agenda
                - 09:00 - 09:30: Standup
                ## My Notes
                - a thought
                """,
            ),
        )

        val result = VaultDiagnostics.analyzeSamples(notes, "# Meetings", "# 📝 Notes") as VaultDiagnosis.HeadingMismatch

        assertEquals("# Agenda", result.recommendedMeetingsHeading)
        assertEquals("## My Notes", result.recommendedNotesHeading)
    }

    @Test
    fun `no recommendation when there are no meeting-time lines at all`() {
        val notes = listOf(sample("# Journal\n- just freeform text\n- more text"))
        val result = VaultDiagnostics.analyzeSamples(notes, "# Meetings", "# Notes") as VaultDiagnosis.HeadingMismatch
        assertNull(result.recommendedMeetingsHeading)
    }

    @Test
    fun `infers a folder template from a found dated note`() {
        val template = VaultDiagnostics.inferTemplate(
            "Periodic Notes/Daily Notes/2026/06 - June/2026-06-23.md",
            LocalDate.of(2026, 6, 23),
        )
        assertEquals("Periodic Notes/Daily Notes/{year}/{monthFolder}/{isoDate}.md", template)
    }

    @Test
    fun `infers a flat template too`() {
        val template = VaultDiagnostics.inferTemplate("Daily/2026-06-23.md", LocalDate.of(2026, 6, 23))
        assertEquals("Daily/{isoDate}.md", template)
    }

    @Test
    fun `inferTemplate returns null for a non-dated file`() {
        assertNull(VaultDiagnostics.inferTemplate("Notes/inbox.md", LocalDate.of(2026, 6, 23)))
    }

    @Test
    fun `dateFromFileName parses the encoded date`() {
        assertEquals(LocalDate.of(2026, 6, 23), VaultDiagnostics.dateFromFileName("2026-06-23.md"))
        assertNull(VaultDiagnostics.dateFromFileName("README.md"))
    }
}
