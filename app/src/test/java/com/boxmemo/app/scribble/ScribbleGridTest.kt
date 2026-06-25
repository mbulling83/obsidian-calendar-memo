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
