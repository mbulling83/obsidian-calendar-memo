package com.boxmemo.app.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDate

class VaultScannerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun writeNoteFor(settings: VaultSettings, date: LocalDate) {
        val file = settings.resolveDailyNotePath(date)!!
        file.parentFile!!.mkdirs()
        file.writeText("# 👥 Meetings\n- 09:00 - 09:30: Standup\n")
    }

    @Test
    fun `scan samples a note on the last day inside the look-back window`() {
        val today = LocalDate.of(2026, 6, 17)
        val settings = VaultSettings(tempFolder.root.path)
        // lookBackDays = 3 covers today, -1 and -2 — the note on -2 is the last day in.
        writeNoteFor(settings, today.minusDays(2))

        val diagnosis = VaultScanner(settings).scan(today = today, lookBackDays = 3)

        assertTrue(diagnosis is VaultDiagnosis.Healthy)
    }

    @Test
    fun `scan does not look one day beyond the reported look-back window`() {
        val today = LocalDate.of(2026, 6, 17)
        val settings = VaultSettings(tempFolder.root.path)
        // With lookBackDays = 3, a note on -3 is outside the window and must not resolve.
        writeNoteFor(settings, today.minusDays(3))

        val diagnosis = VaultScanner(settings).scan(today = today, lookBackDays = 3)

        assertTrue(diagnosis is VaultDiagnosis.NoNotesFound)
        assertEquals(3, (diagnosis as VaultDiagnosis.NoNotesFound).daysChecked)
    }
}
