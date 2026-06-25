package com.boxmemo.app.scribble

import com.boxmemo.app.memo.StrokePath
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.min

/**
 * Pure geometry of the month grid, shared by [drawMonthGrid] (which paints it)
 * and the search indexer (which maps strokes back to day cells). Keeping the
 * cell math here — with no Android `Canvas` dependency — means a stroke is
 * always bucketed to exactly the cell it was drawn over, and the math is
 * JVM-unit-testable.
 */
data class GridGeometry(
    val headerHeight: Float,
    val cellWidth: Float,
    val cellHeight: Float,
    val gridTop: Float,
    val gridBottom: Float,
    val weeks: Int,
    val leadingBlanks: Int,
    val daysInMonth: Int,
)

/** Header band height in pixels for the given [density]. */
internal fun headerHeightFor(density: Float) = 44f * density

/** Computes the grid layout for [month] on a [width]x[height] canvas at [density]. */
fun gridGeometry(month: YearMonth, width: Int, height: Int, density: Float): GridGeometry {
    val headerHeight = headerHeightFor(density)
    val weeks = weekRowsFor(month)
    val cellWidth = width / 7f
    val gridTop = headerHeight
    // Square cells, never taller than the space available (matches drawMonthGrid).
    val cellHeight = min(cellWidth, (height - gridTop) / weeks)
    val gridBottom = gridTop + cellHeight * weeks
    val leadingBlanks = month.atDay(1).dayOfWeek.value - 1 // Mon=0 … Sun=6
    return GridGeometry(
        headerHeight = headerHeight,
        cellWidth = cellWidth,
        cellHeight = cellHeight,
        gridTop = gridTop,
        gridBottom = gridBottom,
        weeks = weeks,
        leadingBlanks = leadingBlanks,
        daysInMonth = month.lengthOfMonth(),
    )
}

/**
 * The date whose grid cell contains the point ([x], [y]), or null when the point
 * falls outside the day grid — the header band, the notes area below the grid,
 * or a blank leading/trailing cell.
 */
fun dateAtPoint(month: YearMonth, geom: GridGeometry, x: Float, y: Float): LocalDate? {
    if (y < geom.gridTop || y >= geom.gridBottom) return null
    if (x < 0f || x >= geom.cellWidth * 7) return null
    val col = (x / geom.cellWidth).toInt()
    val row = ((y - geom.gridTop) / geom.cellHeight).toInt()
    if (col !in 0..6 || row !in 0 until geom.weeks) return null
    val day = row * 7 + col - geom.leadingBlanks + 1
    if (day < 1 || day > geom.daysInMonth) return null
    return month.atDay(day)
}

/**
 * Buckets each stroke of [scribble] into the day cell containing the midpoint of
 * its bounding box, so a stroke that slightly overflows a cell still lands on the
 * intended day. Strokes outside the day grid (page title / notes area, or blank
 * cells) collect under the `null` key — the month-level bucket.
 */
fun bucketStrokesByDay(scribble: MonthScribble, density: Float): Map<LocalDate?, List<StrokePath>> {
    val geom = gridGeometry(scribble.month, scribble.captureWidth, scribble.captureHeight, density)
    val buckets = LinkedHashMap<LocalDate?, MutableList<StrokePath>>()
    for (stroke in scribble.strokes) {
        if (stroke.isEmpty()) continue
        val midX = (stroke.minOf { it.first } + stroke.maxOf { it.first }) / 2f
        val midY = (stroke.minOf { it.second } + stroke.maxOf { it.second }) / 2f
        val date = dateAtPoint(scribble.month, geom, midX, midY)
        buckets.getOrPut(date) { mutableListOf() }.add(stroke)
    }
    return buckets
}
