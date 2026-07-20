package com.boxmemo.app.scribble

import com.boxmemo.app.memo.StrokePath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class ScribbleGridTest {

    // A simple, even canvas: 700 wide → 100px cells, density 1 → 44px header.
    private val month = YearMonth.of(2026, 7) // 1 Jul 2026 is a Wednesday → 2 leading blanks
    private val width = 700
    private val height = 744 // 44 header + 700 worth of cells leaves square-ish rows
    private val geom = gridGeometry(month, width, height, density = 1f)

    private fun centerOf(date: LocalDate): Pair<Float, Float> {
        val leading = month.atDay(1).dayOfWeek.value - 1
        val cellIndex = leading + date.dayOfMonth - 1
        val col = cellIndex % 7
        val row = cellIndex / 7
        val x = geom.cellWidth * col + geom.cellWidth / 2f
        val y = geom.gridTop + geom.cellHeight * row + geom.cellHeight / 2f
        return x to y
    }

    @Test
    fun `point in a day cell resolves to that date`() {
        val (x, y) = centerOf(LocalDate.of(2026, 7, 3))
        assertEquals(LocalDate.of(2026, 7, 3), dateAtPoint(month, geom, x, y))
    }

    @Test
    fun `first and last day of the month resolve correctly`() {
        val (x1, y1) = centerOf(LocalDate.of(2026, 7, 1))
        assertEquals(LocalDate.of(2026, 7, 1), dateAtPoint(month, geom, x1, y1))
        val (x31, y31) = centerOf(LocalDate.of(2026, 7, 31))
        assertEquals(LocalDate.of(2026, 7, 31), dateAtPoint(month, geom, x31, y31))
    }

    @Test
    fun `a leading blank cell resolves to null`() {
        // Column 0 of row 0 is a blank (July starts Wednesday, col 2).
        val x = geom.cellWidth / 2f
        val y = geom.gridTop + geom.cellHeight / 2f
        assertNull(dateAtPoint(month, geom, x, y))
    }

    @Test
    fun `a point in the header resolves to null`() {
        assertNull(dateAtPoint(month, geom, width / 2f, geom.gridTop / 2f))
    }

    @Test
    fun `a point below the grid resolves to null`() {
        assertNull(dateAtPoint(month, geom, width / 2f, geom.gridBottom + 1f))
    }

    @Test
    fun `the rightmost pixel column resolves to the last weekday column`() {
        // July 2026 starts Wednesday (2 leading blanks), so row 0 col 6 is day 5.
        val y = geom.gridTop + geom.cellHeight / 2f
        assertEquals(LocalDate.of(2026, 7, 5), dateAtPoint(month, geom, width - 0.5f, y))
        // At or beyond the canvas edge is out.
        assertNull(dateAtPoint(month, geom, width.toFloat(), y))
    }

    @Test
    fun `a canvas shorter than the header yields a zero-height grid, not an inverted one`() {
        val tiny = gridGeometry(month, 700, 10, density = 1f)
        assertTrue(tiny.cellHeight >= 0f)
        assertEquals(tiny.gridTop, tiny.gridBottom)
        // Nothing resolves to a date on a degenerate grid.
        assertNull(dateAtPoint(month, tiny, 350f, 5f))
    }
}

class BucketStrokesTest {

    private val month = YearMonth.of(2026, 7)
    private val width = 700
    private val height = 744
    private val density = 1f
    private val geom = gridGeometry(month, width, height, density)

    private fun cellStroke(date: LocalDate): StrokePath {
        val leading = month.atDay(1).dayOfWeek.value - 1
        val cellIndex = leading + date.dayOfMonth - 1
        val col = cellIndex % 7
        val row = cellIndex / 7
        val cx = geom.cellWidth * col + geom.cellWidth / 2f
        val cy = geom.gridTop + geom.cellHeight * row + geom.cellHeight / 2f
        // A small stroke centered in the cell.
        return listOf((cx - 5f) to (cy - 5f), (cx + 5f) to (cy + 5f))
    }

    @Test
    fun `strokes are bucketed to the day cell containing their bounding-box midpoint`() {
        val scribble = MonthScribble(
            month, width, height,
            strokes = listOf(cellStroke(LocalDate.of(2026, 7, 3)), cellStroke(LocalDate.of(2026, 7, 11))),
        )
        val buckets = bucketStrokesByDay(scribble, density)
        assertEquals(setOf(LocalDate.of(2026, 7, 3), LocalDate.of(2026, 7, 11)), buckets.keys.filterNotNull().toSet())
        assertEquals(1, buckets[LocalDate.of(2026, 7, 3)]!!.size)
    }

    @Test
    fun `a stroke that slightly overflows a cell still lands on that day via its midpoint`() {
        val date = LocalDate.of(2026, 7, 3)
        val base = cellStroke(date)
        // Extend one endpoint well past the cell edge; midpoint stays in-cell.
        val overflow = base + listOf((base.last().first + geom.cellWidth * 0.4f) to base.last().second)
        val scribble = MonthScribble(month, width, height, strokes = listOf(overflow))
        val buckets = bucketStrokesByDay(scribble, density)
        assertTrue(buckets.containsKey(date))
    }

    @Test
    fun `ink in the header goes to the null (month-level) bucket`() {
        val headerStroke = listOf(10f to 5f, 20f to 10f)
        val scribble = MonthScribble(month, width, height, strokes = listOf(headerStroke))
        val buckets = bucketStrokesByDay(scribble, density)
        assertEquals(setOf<LocalDate?>(null), buckets.keys)
        assertEquals(1, buckets[null]!!.size)
    }
}

class TransformStrokesForGridTest {

    // July 2026 (5 week rows, 2 leading blanks) on a 700-wide canvas at density
    // 1: header 44px, 100px square cells (clamped by cellWidth), grid band
    // 44..544, notes area below.
    private val month = YearMonth.of(2026, 7)
    private val density = 1f
    private val fromW = 700
    private val fromH = 744

    private fun strokeInCell(date: LocalDate, w: Int, h: Int): StrokePath {
        val geom = gridGeometry(month, w, h, density)
        val leading = month.atDay(1).dayOfWeek.value - 1
        val cellIndex = leading + date.dayOfMonth - 1
        val cx = geom.cellWidth * (cellIndex % 7) + geom.cellWidth / 2f
        val cy = geom.gridTop + geom.cellHeight * (cellIndex / 7) + geom.cellHeight / 2f
        return listOf((cx - 5f) to (cy - 5f), (cx + 5f) to (cy + 5f))
    }

    private fun bucketOf(strokes: List<StrokePath>, w: Int, h: Int): Set<LocalDate?> =
        bucketStrokesByDay(MonthScribble(month, w, h, strokes), density).keys

    @Test
    fun `height-only change keeps ink in its day cell where linear scaling would not`() {
        // Cells are min-clamped square (100px) at both heights, so the grid band
        // is identical — only the notes area grows. A linear rescale (744→1000)
        // would push this row-3 stroke down out of its cell.
        val date = LocalDate.of(2026, 7, 23)
        val stroke = strokeInCell(date, fromW, fromH)
        val toH = 1000

        val moved = transformStrokesForGrid(month, listOf(stroke), fromW, fromH, fromW, toH, density)

        assertEquals(setOf<LocalDate?>(date), bucketOf(moved, fromW, toH))
        // Grid unchanged → ink over the grid must be untouched.
        assertEquals(stroke, moved.single())
        // Sanity: the naive linear rescale does lose the day (guards the test).
        val linear = scaleStrokes(listOf(stroke), fromW, fromH, fromW, toH)
        assertTrue(bucketOf(linear, fromW, toH) != setOf<LocalDate?>(date))
    }

    @Test
    fun `shrinking the grid band rescales rows without changing the bucketed day`() {
        // At 700x400 the cell height clamps to (400-44)/5 = 71.2px — the band
        // genuinely scales, and every stroke must keep its day.
        val dates = listOf(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 15), LocalDate.of(2026, 7, 31))
        val strokes = dates.map { strokeInCell(it, fromW, fromH) }
        val toH = 400

        val moved = transformStrokesForGrid(month, strokes, fromW, fromH, fromW, toH, density)

        assertEquals(dates.toSet(), bucketOf(moved, fromW, toH))
    }

    @Test
    fun `width and height change together keeps every day bucket`() {
        val dates = listOf(LocalDate.of(2026, 7, 5), LocalDate.of(2026, 7, 20))
        val strokes = dates.map { strokeInCell(it, fromW, fromH) }
        val toW = 1400
        val toH = 1000

        val moved = transformStrokesForGrid(month, strokes, fromW, fromH, toW, toH, density)

        assertEquals(dates.toSet(), bucketOf(moved, toW, toH))
    }

    @Test
    fun `header and notes ink stays month-level across a resize`() {
        val headerStroke = listOf(10f to 5f, 20f to 10f)
        val fromGeom = gridGeometry(month, fromW, fromH, density)
        val notesStroke = listOf(50f to (fromGeom.gridBottom + 50f), 90f to (fromGeom.gridBottom + 60f))

        val moved = transformStrokesForGrid(
            month, listOf(headerStroke, notesStroke), fromW, fromH, fromW, 1000, density,
        )

        assertEquals(setOf<LocalDate?>(null), bucketOf(moved, fromW, 1000))
        // The header band has the same height on both canvases: untouched.
        assertEquals(headerStroke, moved.first())
    }

    @Test
    fun `transform is a no-op when sizes match or the capture size is unknown`() {
        val strokes = listOf(strokeInCell(LocalDate.of(2026, 7, 10), fromW, fromH))
        assertEquals(strokes, transformStrokesForGrid(month, strokes, fromW, fromH, fromW, fromH, density))
        assertEquals(strokes, transformStrokesForGrid(month, strokes, 0, 0, fromW, fromH, density))
    }

    @Test
    fun `round trip through a resize returns ink to its day`() {
        val date = LocalDate.of(2026, 7, 23)
        val stroke = strokeInCell(date, fromW, fromH)

        val there = transformStrokesForGrid(month, listOf(stroke), fromW, fromH, 1400, 400, density)
        val back = transformStrokesForGrid(month, there, 1400, 400, fromW, fromH, density)

        assertEquals(setOf<LocalDate?>(date), bucketOf(back, fromW, fromH))
    }
}
