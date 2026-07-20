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
    val width: Float,
    val height: Float,
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
    // Clamped to >= 0 so a transient tiny layout (height <= header) can't
    // produce an inverted grid.
    val cellHeight = min(cellWidth, (height - gridTop) / weeks).coerceAtLeast(0f)
    val gridBottom = gridTop + cellHeight * weeks
    val leadingBlanks = month.atDay(1).dayOfWeek.value - 1 // Mon=0 … Sun=6
    return GridGeometry(
        width = width.toFloat(),
        height = height.toFloat(),
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
    // Bound by the canvas width, not cellWidth * 7: float rounding could make
    // cellWidth * 7 fall a fraction short of the width and reject the rightmost
    // pixel column. The clamp keeps that edge in column 6.
    if (x < 0f || x >= geom.width) return null
    val col = min((x / geom.cellWidth).toInt(), 6)
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

/**
 * Rescales [strokes] captured on a [fromW]x[fromH] canvas to a [toW]x[toH]
 * canvas *through the grid geometry*, so ink stays in the day cell it was
 * written in. A plain linear rescale ([scaleStrokes]) drifts ink across cell
 * boundaries because the grid has a fixed-height header band and a min-clamped
 * (square) cell height — neither scales linearly with the canvas.
 *
 * x is a simple width scale (columns scale linearly). y is mapped band by band,
 * using [gridGeometry] for both sizes as the single source of truth:
 * - header band: kept as-is (the header height depends only on density);
 * - day grid band: header subtracted, scaled by the cell-height ratio, new
 *   header re-added — so a point keeps its row;
 * - notes band below the grid: scaled within the leftover space.
 *
 * A no-op when the sizes match; falls back to [scaleStrokes] when either
 * geometry is degenerate (unknown capture size or a zero-height grid).
 */
fun transformStrokesForGrid(
    month: YearMonth,
    strokes: List<StrokePath>,
    fromW: Int,
    fromH: Int,
    toW: Int,
    toH: Int,
    density: Float,
): List<StrokePath> {
    if (fromW <= 0 || fromH <= 0) return strokes
    if (fromW == toW && fromH == toH) return strokes
    val from = gridGeometry(month, fromW, fromH, density)
    val to = gridGeometry(month, toW, toH, density)
    if (from.cellHeight <= 0f || to.cellHeight <= 0f) {
        return scaleStrokes(strokes, fromW, fromH, toW, toH)
    }
    val sx = toW.toFloat() / fromW
    val bandScale = to.cellHeight / from.cellHeight
    val fromNotes = fromH - from.gridBottom
    val toNotes = toH - to.gridBottom
    val notesScale = if (fromNotes > 0f) toNotes / fromNotes else 1f
    return strokes.map { stroke ->
        stroke.map { (x, y) ->
            val ny = when {
                y < from.gridTop -> y // header band: same height on both canvases
                y < from.gridBottom -> to.gridTop + (y - from.gridTop) * bandScale
                else -> to.gridBottom + (y - from.gridBottom) * notesScale
            }
            x * sx to ny
        }
    }
}
