package com.boxmemo.app.scribble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class ScribbleIndexSerializationTest {

    private val index = ScribbleIndex(
        month = YearMonth.of(2026, 7),
        hash = "abc123",
        dayText = mapOf(
            LocalDate.of(2026, 7, 3) to "dentist appointment 2pm",
            LocalDate.of(2026, 7, 11) to "call alice re budget",
        ),
        monthText = "planning notes top of page",
    )

    @Test
    fun `round-trips through serialize and deserialize`() {
        val parsed = deserializeIndex(serializeIndex(index))
        assertEquals(index, parsed)
    }

    @Test
    fun `text with spaces is preserved`() {
        val parsed = deserializeIndex(serializeIndex(index))!!
        assertEquals("dentist appointment 2pm", parsed.dayText[LocalDate.of(2026, 7, 3)])
    }

    @Test
    fun `blank day and month text are dropped`() {
        val sparse = index.copy(
            dayText = index.dayText + (LocalDate.of(2026, 7, 20) to "   "),
            monthText = "",
        )
        val parsed = deserializeIndex(serializeIndex(sparse))!!
        assertNull(parsed.dayText[LocalDate.of(2026, 7, 20)])
        assertEquals("", parsed.monthText)
    }

    @Test
    fun `embedded tabs and newlines are flattened to spaces`() {
        val messy = index.copy(monthText = "line one\nline\ttwo")
        val parsed = deserializeIndex(serializeIndex(messy))!!
        assertTrue(parsed.monthText.none { it == '\n' || it == '\t' })
    }

    @Test
    fun `malformed or unversioned input returns null`() {
        assertNull(deserializeIndex("garbage"))
        assertNull(deserializeIndex(""))
        assertNull(deserializeIndex("month=2026-07\nhash=x")) // no version header
    }

    @Test
    fun `inkHash changes when ink text changes and is stable otherwise`() {
        val a = inkHash("v1\nmonth=2026-07\n10.0,20.0 11.0,21.0\n")
        val b = inkHash("v1\nmonth=2026-07\n10.0,20.0 11.0,21.0\n")
        val c = inkHash("v1\nmonth=2026-07\n10.0,20.0 11.0,99.0\n")
        assertEquals(a, b)
        assertNotEquals(a, c)
    }
}

class ScribbleSearchTest {

    private val july = ScribbleIndex(
        month = YearMonth.of(2026, 7),
        hash = "h1",
        dayText = mapOf(
            LocalDate.of(2026, 7, 3) to "Dentist appointment 2pm",
            LocalDate.of(2026, 7, 11) to "call alice re budget",
        ),
        monthText = "summer planning",
    )
    private val august = ScribbleIndex(
        month = YearMonth.of(2026, 8),
        hash = "h2",
        dayText = mapOf(LocalDate.of(2026, 8, 5) to "dentist follow-up"),
        monthText = "",
    )
    private val all = listOf(july, august)

    @Test
    fun `match is case-insensitive and finds day-level hits`() {
        val hits = searchIndex(all, "dentist")
        assertEquals(2, hits.size)
        assertTrue(hits.all { it.day != null })
    }

    @Test
    fun `month-level text is searchable and yields a null-day hit`() {
        val hits = searchIndex(all, "planning")
        assertEquals(1, hits.size)
        assertNull(hits.first().day)
        assertEquals(YearMonth.of(2026, 7), hits.first().month)
    }

    @Test
    fun `results are ordered newest-first`() {
        val hits = searchIndex(all, "dentist")
        // August 5 is newer than July 3, so it comes first.
        assertEquals(LocalDate.of(2026, 8, 5), hits.first().day)
    }

    @Test
    fun `no match returns empty`() {
        assertTrue(searchIndex(all, "zzzz").isEmpty())
    }

    @Test
    fun `blank query returns empty`() {
        assertTrue(searchIndex(all, "   ").isEmpty())
    }
}
