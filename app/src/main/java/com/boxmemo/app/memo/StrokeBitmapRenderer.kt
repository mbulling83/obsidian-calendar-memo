package com.boxmemo.app.memo

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

/**
 * Renders a capture's strokes to a bitmap for the OCR (U7/U8) and diagram
 * (U10) pipelines, which both need an image of the capture rather than raw
 * point data. Requires the Android graphics framework, so it's exercised
 * via androidTest rather than a JVM unit test.
 */
fun renderStrokesToBitmap(strokes: List<StrokePath>, width: Int, height: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.WHITE)

    val paint = Paint().apply {
        color = Color.BLACK
        strokeWidth = 4f
        style = Paint.Style.STROKE
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    for (stroke in strokes) {
        if (stroke.size < 2) continue
        val path = android.graphics.Path().apply {
            val (startX, startY) = stroke.first()
            moveTo(startX, startY)
            stroke.drop(1).forEach { (x, y) -> lineTo(x, y) }
        }
        canvas.drawPath(path, paint)
    }

    return bitmap
}
