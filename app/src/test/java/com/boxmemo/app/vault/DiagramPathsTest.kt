package com.boxmemo.app.vault

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class DiagramPathsTest {

    @Test
    fun `sanitize strips wiki brackets, illegal chars and collapses whitespace`() {
        assertEquals("1 1 with Bob", sanitizeDiagramName("1:1 with [[Bob]]"))
        assertEquals("Plan A B", sanitizeDiagramName("Plan A/B"))
        assertEquals("Sprint review", sanitizeDiagramName("  Sprint   review  "))
    }

    @Test
    fun `sanitize strips Obsidian heading and block subpath chars`() {
        // `#` and `^` are parsed as heading/block subpaths inside `![[...]]` embeds.
        assertEquals("Sprint 12 review", sanitizeDiagramName("Sprint #12 ^review"))
    }

    @Test
    fun `meeting base name uses date, dotted start time and sanitized title`() {
        val base = meetingDiagramBaseName(
            date = LocalDate.of(2026, 6, 21),
            startTime = "14:30",
            title = "Standup",
        )
        assertEquals("2026-06-21 14.30 Standup", base)
    }

    @Test
    fun `meeting base name strips wiki links and illegal chars from title`() {
        val base = meetingDiagramBaseName(
            date = LocalDate.of(2026, 6, 21),
            startTime = "10:00",
            title = "1:1 with [[Person]]",
        )
        assertEquals("2026-06-21 10.00 1 1 with Person", base)
    }

    @Test
    fun `notes base name uses date and dotted time`() {
        val base = notesDiagramBaseName(LocalDate.of(2026, 6, 21), LocalTime.of(9, 5))
        assertEquals("2026-06-21 09.05 Notes", base)
    }

    @Test
    fun `file base name uses date, time and sanitized filename without md suffix`() {
        val base = fileDiagramBaseName(
            date = LocalDate.of(2026, 6, 21),
            time = LocalTime.of(16, 42),
            fileName = "Project Plan.md",
        )
        assertEquals("2026-06-21 16.42 Project Plan", base)
    }

    @Test
    fun `relative dir groups by ISO week-based year and week`() {
        assertEquals(
            "attachments/Diagrams/2026/W25",
            diagramRelativeDir(LocalDate.of(2026, 6, 21)),
        )
    }

    @Test
    fun `relative dir uses week-based year across a year boundary`() {
        // 2025-12-29 (Mon) falls in ISO week 1 of week-based-year 2026.
        assertEquals(
            "attachments/Diagrams/2026/W01",
            diagramRelativeDir(LocalDate.of(2025, 12, 29)),
        )
    }

    @Test
    fun `unique filename appends png when no collision`() {
        assertEquals(
            "2026-06-21 14.30 Standup.png",
            uniqueDiagramFileName("2026-06-21 14.30 Standup", emptySet()),
        )
    }

    @Test
    fun `unique filename suffixes incrementally on collision`() {
        val existing = setOf(
            "2026-06-21 14.30 Standup.png",
            "2026-06-21 14.30 Standup (2).png",
        )
        assertEquals(
            "2026-06-21 14.30 Standup (3).png",
            uniqueDiagramFileName("2026-06-21 14.30 Standup", existing),
        )
    }
}
