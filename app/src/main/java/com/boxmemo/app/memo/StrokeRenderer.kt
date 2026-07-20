package com.boxmemo.app.memo

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.ceil

private const val DIAGRAM_PADDING = 24
private const val MIN_DIAGRAM_DIMENSION = 64

/**
 * Renders [strokes] to a white-background [Bitmap] cropped to the ink's bounds
 * (plus padding), using the same black Path/Paint as the on-screen redraw. No
 * guidelines are drawn — a saved diagram is just the ink. Strokes are
 * translated so the top-left of their bounding box sits at the padding offset,
 * so the image isn't padded out to wherever on the canvas the user happened to
 * draw.
 *
 * Callers must guard against empty input (an empty diagram isn't worth saving);
 * a fallback minimum-size blank bitmap is returned defensively if they don't.
 */
fun renderStrokesToBitmap(strokes: List<StrokePath>, penSettings: PenSettings): Bitmap {
    val points = strokes.flatten()
    val paint = Paint().apply {
        color = Color.BLACK
        strokeWidth = penSettings.strokeWidth
        style = Paint.Style.STROKE
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    if (points.isEmpty()) {
        return blankBitmap()
    }

    val minX = points.minOf { it.first }
    val minY = points.minOf { it.second }
    val maxX = points.maxOf { it.first }
    val maxY = points.maxOf { it.second }

    val width = maxOf(MIN_DIAGRAM_DIMENSION, ceil(maxX - minX).toInt() + DIAGRAM_PADDING * 2)
    val height = maxOf(MIN_DIAGRAM_DIMENSION, ceil(maxY - minY).toInt() + DIAGRAM_PADDING * 2)

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.WHITE)
    // Shift the ink so its bounding box's top-left lands at (padding, padding).
    val dx = DIAGRAM_PADDING - minX
    val dy = DIAGRAM_PADDING - minY
    for (stroke in strokes) {
        if (stroke.isEmpty()) continue
        if (stroke.size == 1) {
            // A 1-point stroke (i-dot, period) renders as a dot via the
            // round-capped stroke paint.
            val (x, y) = stroke[0]
            canvas.drawPoint(x + dx, y + dy, paint)
            continue
        }
        val path = Path()
        val (sx, sy) = stroke[0]
        path.moveTo(sx + dx, sy + dy)
        for (i in 1 until stroke.size) {
            val (x, y) = stroke[i]
            path.lineTo(x + dx, y + dy)
        }
        canvas.drawPath(path, paint)
    }
    return bitmap
}

private fun blankBitmap(): Bitmap {
    val bitmap = Bitmap.createBitmap(
        MIN_DIAGRAM_DIMENSION,
        MIN_DIAGRAM_DIMENSION,
        Bitmap.Config.ARGB_8888,
    )
    Canvas(bitmap).drawColor(Color.WHITE)
    return bitmap
}
