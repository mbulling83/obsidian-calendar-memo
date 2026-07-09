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
    private var guidelineStyle: GuidelineStyle,
    private val onStrokeFinished: (StrokePath) -> Unit,
    private val onStrokesErased: (List<StrokePath>) -> Unit,
    // Optional caller-drawn background (e.g. the month-scribble calendar grid),
    // painted onto the surface's own white buffer under the ink. Drawn here
    // rather than behind the SurfaceView because the surface is opaque and would
    // otherwise hide anything composed behind it.
    private val backgroundRenderer: ((android.graphics.Canvas, Int, Int) -> Unit)? = null,
) : SurfaceView(context), SurfaceHolder.Callback {

    /** Toggled by MemoCanvas's AndroidView update lambda — no surface recreation. */
    var isEraserActive: Boolean = false

    /**
     * Suspends raw-drawing pen capture without recreating the surface.
     *
     * Onyx raw drawing captures the pen *screen-wide*, bypassing the view
     * hierarchy — so while it's enabled, strokes the user makes on the system
     * handwriting keyboard (e.g. the Vault Notes / month-scribble search field)
     * are grabbed by this surface and painted onto the panel's raw layer, where
     * they persist as stray ink. The soft keyboard doesn't steal *window* focus,
     * so [onWindowFocusChanged] never fires to disable capture for it. Screens
     * with a focusable text field over the canvas drive this flag from the
     * field's focus state instead.
     */
    private var penCaptureSuspended: Boolean = false

    fun setPenCaptureSuspended(suspended: Boolean) {
        if (suspended == penCaptureSuspended) return
        penCaptureSuspended = suspended
        val helper = touchHelper ?: return
        if (suspended) {
            // Disable capture and repaint our normal buffer, which also clears
            // any stray ink the firmware left on the raw layer.
            helper.setRawDrawingEnabled(false)
            redrawExistingStrokes(holder)
        } else if (hasWindowFocus()) {
            helper.setRawDrawingEnabled(true)
        }
    }

    /**
     * Resyncs on-screen ink to [strokes] when it differs from what's currently
     * drawn — driven by MemoCanvas's update lambda so external changes (e.g. a
     * conversion clearing the scope) actually wipe the ink. A user's own
     * freshly-finished stroke is already in [currentStrokes] (added in the raw
     * callback) and in the store, so the lists match and this is a no-op,
     * avoiding a redundant repaint/flash on every recomposition.
     */
    fun syncStrokes(strokes: List<StrokePath>) {
        if (strokes == currentStrokes) return
        currentStrokes.clear()
        currentStrokes.addAll(strokes)
        repaintClearingRawLayer()
    }

    /**
     * Forces the firmware to deliver any stroke it's still buffering, then
     * returns every stroke now on this canvas.
     *
     * Onyx only hands a finished stroke to [rawInputCallback] when raw drawing
     * is *disabled* — until then the ink is painted by the e-ink controller but
     * hasn't reached our data. Previously that flush happened only in
     * [surfaceDestroyed] (on navigation), so freshly-written ink wasn't in the
     * store and Convert/Erase saw nothing until the user left and came back.
     * Toggling raw drawing off→on here reproduces that delivery on demand: the
     * pending stroke arrives synchronously via the callback (added to
     * [currentStrokes] and pushed to the store) before this returns, so the
     * caller gets a complete, up-to-date list without a round-trip.
     */
    fun flushAndGetStrokes(): List<StrokePath> {
        touchHelper?.let { helper ->
            helper.setRawDrawingEnabled(false)
            helper.setRawDrawingEnabled(true)
        }
        return currentStrokes.toList()
    }

    /** Changes guidelines without recreating the surface (just a repaint). */
    fun setGuidelineStyle(style: GuidelineStyle) {
        if (style == guidelineStyle) return
        guidelineStyle = style
        repaintClearingRawLayer()
    }

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

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        // A resize (e.g. collapsing the side-by-side notes pane widens the
        // canvas) hands us a fresh, unpainted surface buffer — left untouched
        // it shows as a black box. Repaint our strokes/guidelines and move the
        // raw-drawing input region to the new bounds so the pen still maps.
        val helper = touchHelper
        if (helper == null) {
            redrawExistingStrokes(holder)
            return
        }
        helper.setRawDrawingEnabled(false)
        helper.setLimitRect(Rect(0, 0, width, height), ArrayList())
        redrawExistingStrokes(holder)
        helper.setRawDrawingEnabled(true)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        touchHelper?.setRawDrawingEnabled(false)
        touchHelper?.closeRawDrawing()
        touchHelper = null
    }

    /**
     * Suspends pen capture while another window holds focus.
     *
     * A Compose dialog (e.g. the Quick-Add meeting form) opens in its *own*
     * window, stealing focus from the Activity window this surface lives in.
     * Onyx raw drawing bypasses the normal view hierarchy and keeps capturing
     * the pen even while the dialog sits on top — so taps meant for the dialog's
     * buttons landed as ink on the canvas underneath instead. Disabling raw
     * drawing on focus loss hands input back to the dialog; we redraw our strokes
     * onto the normal buffer (revealed when the firmware's raw layer goes away)
     * so nothing flickers, and re-enable capture once focus returns.
     */
    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        val helper = touchHelper ?: return
        if (hasWindowFocus && !penCaptureSuspended) {
            helper.setRawDrawingEnabled(true)
        } else {
            helper.setRawDrawingEnabled(false)
            redrawExistingStrokes(holder)
        }
    }

    private val guidelinePaint = Paint().apply {
        color = Color.GRAY
        strokeWidth = 2.5f
        style = Paint.Style.STROKE
        isAntiAlias = false
    }

    private fun drawGuidelines(canvas: android.graphics.Canvas) {
        if (guidelineStyle == GuidelineStyle.None) return
        val density = resources.displayMetrics.density
        val spacing = 40 * density
        when (guidelineStyle) {
            GuidelineStyle.Lines -> {
                var y = spacing
                while (y < height) {
                    canvas.drawLine(0f, y, width.toFloat(), y, guidelinePaint)
                    y += spacing
                }
            }
            GuidelineStyle.DotGrid -> {
                val dotPaint = Paint().apply {
                    color = Color.DKGRAY
                    strokeWidth = 6f
                    strokeCap = Paint.Cap.ROUND
                    style = Paint.Style.STROKE
                    isAntiAlias = true
                }
                var y = spacing
                while (y < height) {
                    var x = spacing
                    while (x < width) {
                        canvas.drawPoint(x, y, dotPaint)
                        x += spacing
                    }
                    y += spacing
                }
            }
            GuidelineStyle.None -> Unit
        }
    }

    /**
     * Repaints the surface from [currentStrokes], clearing the Onyx raw-drawing
     * layer first. The firmware composites raw ink on its own hardware layer
     * that a plain [redrawExistingStrokes] (lockCanvas on the normal buffer)
     * doesn't touch — so erased or converted ink stayed visible even though it
     * was gone from our data. Toggling raw drawing off reveals our normal
     * canvas; we repaint there, then re-enable to resume pen capture.
     */
    private fun repaintClearingRawLayer() {
        val helper = touchHelper
        if (helper == null) {
            redrawExistingStrokes(holder)
            return
        }
        helper.setRawDrawingEnabled(false)
        redrawExistingStrokes(holder)
        helper.setRawDrawingEnabled(true)
    }

    private fun redrawExistingStrokes(holder: SurfaceHolder) {
        val canvas = try {
            holder.lockCanvas()
        } catch (e: Exception) {
            null
        } ?: return
        try {
            canvas.drawColor(Color.WHITE)
            drawGuidelines(canvas)
            backgroundRenderer?.invoke(canvas, width, height)
            for (stroke in currentStrokes) {
                if (stroke.size < 2) continue
                val path = android.graphics.Path()
                val (startX, startY) = stroke[0]
                path.moveTo(startX, startY)
                for (i in 1 until stroke.size) {
                    val (x, y) = stroke[i]
                    path.lineTo(x, y)
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
            repaintClearingRawLayer()
            onStrokesErased(remaining)
        }
    }
}
