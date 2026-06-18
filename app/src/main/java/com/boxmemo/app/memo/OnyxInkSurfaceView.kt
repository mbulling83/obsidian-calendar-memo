package com.boxmemo.app.memo

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.onyx.android.sdk.data.note.TouchPoint
import com.onyx.android.sdk.pen.RawInputCallback
import com.onyx.android.sdk.pen.TouchHelper
import com.onyx.android.sdk.pen.data.TouchPointList

private const val ERASE_RADIUS = 20f

/**
 * Low-latency pen input via the Onyx Pen SDK's `TouchHelper`/raw-drawing
 * mode, the same mechanism jdkruzr/aragonite uses: while raw drawing is
 * enabled, the device's e-ink controller renders ink directly to the
 * display at hardware latency, bypassing our app's normal view/Compose
 * redraw pipeline entirely. We only receive each stroke's points
 * *after* it's complete, to persist it.
 *
 * Erasing works two ways:
 * - Hardware: pressing the stylus side button triggers Onyx's raw-erasing
 *   mode; the firmware handles the visual, we drop matching strokes from data.
 * - UI: when [isEraserActive] is set (by AndroidView's update lambda) drawing
 *   strokes are re-routed to erase logic without recreating the surface.
 *
 * Previously-captured strokes for this scope are redrawn once when the
 * surface is (re)created, since raw drawing only paints new ink — it
 * doesn't know about strokes from a prior surface instance (e.g. after
 * switching scope chips).
 */
class OnyxInkSurfaceView(
    context: Context,
    initialStrokes: List<StrokePath>,
    private val penSettings: PenSettings,
    private val onStrokeFinished: (StrokePath) -> Unit,
    private val onStrokesErased: (List<StrokePath>) -> Unit,
) : SurfaceView(context), SurfaceHolder.Callback {

    /** Toggled by MemoCanvas's AndroidView update lambda — no surface recreation. */
    var isEraserActive: Boolean = false

    private var touchHelper: TouchHelper? = null
    private val currentStrokes = initialStrokes.toMutableList()

    private val paint = Paint().apply {
        color = Color.BLACK
        strokeWidth = penSettings.strokeWidth
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
                setStrokeStyle(penSettings.penType.strokeStyle)
                setStrokeWidth(penSettings.strokeWidth)
                setStrokeColor(Color.BLACK)
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
            for (stroke in currentStrokes) {
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
            val stroke = points.map { it.x to it.y }
            if (isEraserActive) {
                eraseWithPoints(stroke)
            } else {
                currentStrokes.add(stroke)
                onStrokeFinished(stroke)
            }
        }

        override fun onBeginRawErasing(p0: Boolean, p1: TouchPoint?) {}
        override fun onEndRawErasing(p0: Boolean, p1: TouchPoint?) {}
        override fun onRawErasingTouchPointMoveReceived(p0: TouchPoint?) {}

        override fun onRawErasingTouchPointListReceived(plist: TouchPointList?) {
            val erasePoints = plist?.points?.map { it.x to it.y } ?: return
            if (erasePoints.isEmpty()) return
            eraseWithPoints(erasePoints)
        }
    }

    private fun eraseWithPoints(erasePoints: List<Pair<Float, Float>>) {
        val remaining = removeErasedStrokes(currentStrokes, erasePoints, ERASE_RADIUS)
        if (remaining.size != currentStrokes.size) {
            currentStrokes.clear()
            currentStrokes.addAll(remaining)
            // Onyx's firmware auto-renders raw *drawing* but not raw
            // erasing — it has no notion of which on-screen pixels are
            // "ours" to remove. We own the redraw so the erased strokes
            // actually disappear from the display, not just our data.
            redrawExistingStrokes(holder)
            onStrokesErased(remaining)
        }
    }
}
