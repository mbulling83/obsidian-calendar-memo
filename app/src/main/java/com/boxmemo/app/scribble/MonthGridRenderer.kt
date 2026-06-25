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
 * [weekRowsFor] and the cell layout in [gridGeometry], both unit-tested.
 *
 * When [highlightDay] is set (a search result jumped here), that cell's day
 * number is drawn in an outlined rounded-square so it reads as distinct from the
 * filled "today" emphasis.
 */
fun drawMonthGrid(
    canvas: Canvas,
    width: Int,
    height: Int,
    month: YearMonth,
    today: LocalDate,
    density: Float,
    highlightDay: LocalDate? = null,
) {
    if (width <= 0 || height <= 0) return

    val geom = gridGeometry(month, width, height, density)
    val headerHeight = geom.headerHeight
    val weeks = geom.weeks
    val cellWidth = geom.cellWidth
    val gridTop = geom.gridTop
    val cellHeight = geom.cellHeight
    val gridBottom = geom.gridBottom

    val linePaint = Paint().apply {
        color = Color.GRAY
        strokeWidth = 1.8f * density
        style = Paint.Style.STROKE
        isAntiAlias = false
    }
    val headerPaint = Paint().apply {
        color = Color.BLACK
        textSize = 18f * density
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    val dayPaint = Paint().apply {
        color = Color.BLACK
        textSize = 22f * density
        isAntiAlias = true
        isFakeBoldText = true
    }
    val todayPaint = Paint().apply {
        color = Color.BLACK
        textSize = 24f * density
        isAntiAlias = true
        isFakeBoldText = true
    }
    val todayBoxPaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    val highlightBoxPaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
        isAntiAlias = true
    }
    val todayTextPaint = Paint().apply {
        color = Color.WHITE
        textSize = todayPaint.textSize
        isAntiAlias = true
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }

    // Weekday header labels.
    val headerBaseline = headerHeight / 2f - (headerPaint.ascent() + headerPaint.descent()) / 2f
    for (d in 0 until 7) {
        canvas.drawText(WEEKDAYS[d], cellWidth * d + cellWidth / 2f, headerBaseline, headerPaint)
    }

    // Grid lines: verticals and horizontals bounded to the (square-celled) grid.
    for (c in 0..7) {
        val x = (cellWidth * c).coerceAtMost(width - 1f)
        canvas.drawLine(x, gridTop, x, gridBottom, linePaint)
    }
    for (r in 0..weeks) {
        val y = gridTop + cellHeight * r
        canvas.drawLine(0f, y, width.toFloat(), y, linePaint)
    }

    // Day numbers, top-left of each cell.
    val leadingBlanks = geom.leadingBlanks
    val daysInMonth = geom.daysInMonth
    val pad = 6f * density
    val isCurrentMonth = YearMonth.from(today) == month
    val highlightDayOfMonth = highlightDay?.takeIf { YearMonth.from(it) == month }?.dayOfMonth
    for (day in 1..daysInMonth) {
        val cellIndex = leadingBlanks + day - 1
        val col = cellIndex % 7
        val row = cellIndex / 7
        val cellLeft = cellWidth * col
        val cellTop = gridTop + cellHeight * row
        val isToday = isCurrentMonth && today.dayOfMonth == day
        val isHighlight = !isToday && day == highlightDayOfMonth
        val paint = if (isToday) todayPaint else dayPaint
        val baseline = cellTop + pad - paint.ascent()
        val textX = cellLeft + pad
        if (isHighlight) {
            // Outlined rounded-square around the day number — distinct from the
            // filled "today" marker — to flag the cell a search jumped to.
            val boxPad = 5f * density
            val textWidth = paint.measureText(day.toString())
            val textHeight = paint.descent() - paint.ascent()
            val boxSize = maxOf(textWidth, textHeight) + boxPad * 2
            val boxLeft = cellLeft + pad - boxPad
            val boxTop = cellTop + pad - boxPad
            val corner = 4f * density
            canvas.drawRoundRect(
                boxLeft, boxTop, boxLeft + boxSize, boxTop + boxSize, corner, corner, highlightBoxPaint,
            )
            canvas.drawText(day.toString(), textX, baseline, paint)
        } else if (isToday) {
            // Filled black rounded-square in the cell corner, day number in white.
            val boxPad = 5f * density
            val textWidth = paint.measureText(day.toString())
            val textHeight = paint.descent() - paint.ascent()
            val boxSize = maxOf(textWidth, textHeight) + boxPad * 2
            val boxLeft = cellLeft + pad - boxPad
            val boxTop = cellTop + pad - boxPad
            val corner = 4f * density
            canvas.drawRoundRect(
                boxLeft, boxTop, boxLeft + boxSize, boxTop + boxSize, corner, corner, todayBoxPaint,
            )
            val centerX = boxLeft + boxSize / 2f
            val centerY = boxTop + boxSize / 2f - (paint.ascent() + paint.descent()) / 2f
            canvas.drawText(day.toString(), centerX, centerY, todayTextPaint)
        } else {
            canvas.drawText(day.toString(), textX, baseline, paint)
        }
    }

    // Notes section in the leftover space below the square grid.
    val notesTop = gridBottom + 12f * density
    if (height - notesTop > 48f * density) {
        val notesLabelPaint = Paint().apply {
            color = Color.BLACK
            textSize = 18f * density
            isAntiAlias = true
            isFakeBoldText = true
        }
        val ruledPaint = Paint().apply {
            color = Color.LTGRAY
            strokeWidth = 1.2f * density
            style = Paint.Style.STROKE
            isAntiAlias = false
        }
        val labelBaseline = notesTop - notesLabelPaint.ascent()
        canvas.drawText("Notes", 6f * density, labelBaseline, notesLabelPaint)
        // Light ruled lines inviting handwriting, starting below the label.
        val ruleSpacing = 40f * density
        var y = labelBaseline + notesLabelPaint.descent() + ruleSpacing
        while (y < height - 4f * density) {
            canvas.drawLine(6f * density, y, width - 6f * density, y, ruledPaint)
            y += ruleSpacing
        }
    }
}
