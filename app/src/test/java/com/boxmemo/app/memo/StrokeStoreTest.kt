package com.boxmemo.app.memo

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StrokeStoreTest {

    private val date = LocalDate.of(2026, 6, 18)
    private val otherDate = LocalDate.of(2026, 6, 19)
    private val strokeA: StrokePath = listOf(0f to 0f, 1f to 1f)
    private val strokeB: StrokePath = listOf(5f to 5f, 6f to 6f)

    @Test
    fun `strokes persist for the same date and scope across reads`() {
        val store = StrokeStore()
        store.addStroke(date, CaptureScope.Notes, strokeA)

        assertEquals(listOf(strokeA), store.strokesFor(date, CaptureScope.Notes))
    }

    @Test
    fun `switching scope does not bleed strokes from one scope into another`() {
        val store = StrokeStore()
        store.addStroke(date, CaptureScope.Meeting("09:00"), strokeA)
        store.addStroke(date, CaptureScope.Meeting("14:00"), strokeB)

        assertEquals(listOf(strokeA), store.strokesFor(date, CaptureScope.Meeting("09:00")))
        assertEquals(listOf(strokeB), store.strokesFor(date, CaptureScope.Meeting("14:00")))
    }

    @Test
    fun `same scope on a different date does not bleed strokes`() {
        val store = StrokeStore()
        store.addStroke(date, CaptureScope.Notes, strokeA)

        assertEquals(emptyList<StrokePath>(), store.strokesFor(otherDate, CaptureScope.Notes))
    }

    @Test
    fun `an unknown date-scope pair returns an empty list, not an error`() {
        val store = StrokeStore()

        assertEquals(emptyList<StrokePath>(), store.strokesFor(date, CaptureScope.Unscoped))
    }

    @Test
    fun `clear removes only the targeted date-scope pair`() {
        val store = StrokeStore()
        store.addStroke(date, CaptureScope.Notes, strokeA)
        store.addStroke(date, CaptureScope.Meeting("09:00"), strokeB)

        store.clear(date, CaptureScope.Notes)

        assertEquals(emptyList<StrokePath>(), store.strokesFor(date, CaptureScope.Notes))
        assertEquals(listOf(strokeB), store.strokesFor(date, CaptureScope.Meeting("09:00")))
    }
}
