package com.boxmemo.app.memo

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.onyx.android.sdk.data.note.TouchPoint
import com.onyx.android.sdk.pen.RawInputCallback
import com.onyx.android.sdk.pen.TouchHelper
import com.onyx.android.sdk.pen.data.TouchPointList

/**
 * Low-latency pen input via the Onyx Pen SDK's `TouchHelper`/raw-drawing
 * mode, the same mechanism jdkruzr/aragonite uses: while raw drawing is
 * enabled, the device's e-ink controller renders ink directly to the
 * display at hardware latency, bypassing our app's normal view/Compose
 * redraw pipeline entirely. We only receive each stroke's points
 * *after* it's complete, to persist it — we never repaint the live
 * stroke ourselves, which is what made the previous Compose-Canvas
 * approach laggy and prone to losing strokes on recomposition.
 *
 * Previously-captured strokes for this scope are redrawn once when the
 * surface is (re)created, since raw drawing only paints new ink — it
 * doesn't know about strokes from a prior surface instance (e.g. after
 * switching scope chips).
 */
class OnyxInkSurfaceView(
    context: Context,
    private val initialStrokes: List<StrokePath>,
    private val onStrokeFinished: (StrokePath) -> Unit,
) : SurfaceView(context), SurfaceHolder.Callback {

    private var touchHelper: TouchHelper? = null

    private val paint = Paint().apply {
        color = Color.BLACK
        strokeWidth = 4f
        style = Paint.Style.STROKE
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    init {
        holder.addCallback(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        redrawExistingStrokes(holder)

        touchHelper = try {
            TouchHelper.create(this, rawInputCallback).apply {
                setLimitRect(Rect(0, 0, width, height), ArrayList())
                openRawDrawing()
                setRawDrawingEnabled(true)
            }
        } catch (e: Throwable) {
            // Not an Onyx device, or the pen service isn't available — no
            // raw-drawing fallback is offered here; AI vision OCR (U8)
            // doesn't depend on having drawn anything via this surface.
            null
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        touchHelper?.setRawDrawingEnabled(false)
        touchHelper?.closeRawDrawing()
        touchHelper = null
    }

    private fun redrawExistingStrokes(holder: SurfaceHolder) {
        val canvas = try {
            holder.lockCanvas()
        } catch (e: Exception) {
            null
        } ?: return
        try {
            canvas.drawColor(Color.WHITE)
            for (stroke in initialStrokes) {
                if (stroke.size < 2) continue
                val path = android.graphics.Path().apply {
                    val (startX, startY) = stroke.first()
                    moveTo(startX, startY)
                    stroke.drop(1).forEach { (x, y) -> lineTo(x, y) }
                }
                canvas.drawPath(path, paint)
            }
        } finally {
            holder.unlockCanvasAndPost(canvas)
        }
    }

    private val rawInputCallback = object : RawInputCallback() {
        override fun onBeginRawDrawing(p0: Boolean, p1: TouchPoint?) {}
        override fun onEndRawDrawing(p0: Boolean, p1: TouchPoint?) {}
        override fun onRawDrawingTouchPointMoveReceived(p0: TouchPoint?) {}

        override fun onRawDrawingTouchPointListReceived(plist: TouchPointList) {
            val points = plist.points
            if (points.size < 2) return
            onStrokeFinished(points.map { it.x to it.y })
        }

        override fun onBeginRawErasing(p0: Boolean, p1: TouchPoint?) {}
        override fun onEndRawErasing(p0: Boolean, p1: TouchPoint?) {}
        override fun onRawErasingTouchPointMoveReceived(p0: TouchPoint?) {}
        override fun onRawErasingTouchPointListReceived(plist: TouchPointList?) {}
    }
}
