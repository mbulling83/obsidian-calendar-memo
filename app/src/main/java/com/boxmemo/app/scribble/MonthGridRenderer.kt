package com.boxmemo.app.scribble

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.ceil

private val WEEKDAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

/** Number of week rows needed to lay out [month] with weeks starting Monday. */
internal fun weekRowsFor(month: YearMonth): Int {
    val leadingBlanks = month.atDay(1).dayOfWeek.value - 1 // Mon=0 … Sun=6
    return ceil((leadingBlanks + month.lengthOfMonth()) / 7.0).toInt()
}

/**
 * Paints the month calendar grid (weekday header, cell borders, day numbers,
 * with [today] emphasised) onto the ink surface's own white buffer, under the
 * handwriting. Drawn in raw surface pixels so it lines up exactly with the ink
 * captured on the same surface. [density] scales text/line sizes for the device.
 *
 * Device-verified glue (no JVM test) — the cell-count math lives in
 * [weekRowsFor], which is unit-tested.
 */
fun drawMonthGrid(
    canvas: Canvas,
    width: Int,
    height: Int,
    month: YearMonth,
    today: LocalDate,
    density: Float,
) {
    if (width <= 0 || height <= 0) return

    val headerHeight = 36f * density
    val weeks = weekRowsFor(month)
    val cellWidth = width / 7f
    val gridTop = headerHeight
    val gridHeight = height - gridTop
    val cellHeight = gridHeight / weeks

    val linePaint = Paint().apply {
        color = Color.LTGRAY
        strokeWidth = 1.5f
        style = Paint.Style.STROKE
        isAntiAlias = false
    }
    val headerPaint = Paint().apply {
        color = Color.DKGRAY
        textSize = 13f * density
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    val dayPaint = Paint().apply {
        color = Color.GRAY
        textSize = 15f * density
        isAntiAlias = true
    }
    val todayPaint = Paint().apply {
        color = Color.BLACK
        textSize = 17f * density
        isAntiAlias = true
        isFakeBoldText = true
    }
    val todayCirclePaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        isAntiAlias = true
    }

    // Weekday header labels.
    val headerBaseline = headerHeight / 2f - (headerPaint.ascent() + headerPaint.descent()) / 2f
    for (d in 0 until 7) {
        canvas.drawText(WEEKDAYS[d], cellWidth * d + cellWidth / 2f, headerBaseline, headerPaint)
    }

    // Grid lines: verticals full height, horizontals across the grid body.
    for (c in 0..7) {
        val x = (cellWidth * c).coerceAtMost(width - 1f)
        canvas.drawLine(x, gridTop, x, height.toFloat(), linePaint)
    }
    for (r in 0..weeks) {
        val y = (gridTop + cellHeight * r).coerceAtMost(height - 1f)
        canvas.drawLine(0f, y, width.toFloat(), y, linePaint)
    }

    // Day numbers, top-left of each cell.
    val leadingBlanks = month.atDay(1).dayOfWeek.value - 1
    val daysInMonth = month.lengthOfMonth()
    val pad = 6f * density
    val isCurrentMonth = YearMonth.from(today) == month
    for (day in 1..daysInMonth) {
        val cellIndex = leadingBlanks + day - 1
        val col = cellIndex % 7
        val row = cellIndex / 7
        val cellLeft = cellWidth * col
        val cellTop = gridTop + cellHeight * row
        val isToday = isCurrentMonth && today.dayOfMonth == day
        val paint = if (isToday) todayPaint else dayPaint
        val baseline = cellTop + pad - paint.ascent()
        val textX = cellLeft + pad
        if (isToday) {
            val radius = paint.textSize * 0.85f
            canvas.drawCircle(textX + radius / 2.5f, baseline + paint.ascent() / 2.5f, radius, todayCirclePaint)
        }
        canvas.drawText(day.toString(), textX, baseline, paint)
    }
}
