package com.boxmemo.app.memo

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Bridges the toolbar's Convert/Erase actions to the live [OnyxInkSurfaceView],
 * which the toolbar can't reach directly (it lives inside an [AndroidView]).
 * [flush] forces the firmware to deliver any stroke it's still buffering and
 * returns the canvas's current strokes — so the buttons act on what's actually
 * on screen, not the last value that happened to reach the store.
 */
class InkFlushHandle {
    internal var surface: OnyxInkSurfaceView? = null

    /** Flushes the pen buffer and returns all strokes now on the canvas. */
    fun flush(): List<StrokePath> = surface?.flushAndGetStrokes().orEmpty()
}

/**
 * Drawing surface scoped to a date + [CaptureScope] (a meeting entry, the
 * Notes section, or unscoped). Captures handwriting, which the user later
 * manually converts to text — recognition happens on demand, not at
 * capture time.
 *
 * Backed by [OnyxInkSurfaceView] (Onyx Pen SDK raw drawing), the same
 * low-latency mechanism jdkruzr/aragonite uses — not a Compose `Canvas`
 * with `pointerInput`, which redraws every point through Compose's
 * recomposition pipeline and was both laggy and prone to losing strokes.
 */
@Composable
fun MemoCanvas(
    strokes: List<StrokePath>,
    penSettings: PenSettings,
    guidelineStyle: GuidelineStyle,
    isEraserActive: Boolean,
    onStrokeFinished: (StrokePath) -> Unit,
    onStrokesErased: (List<StrokePath>) -> Unit,
    modifier: Modifier = Modifier,
    flushHandle: InkFlushHandle? = null,
) {
    AndroidView(
        modifier = modifier.fillMaxWidth().fillMaxHeight(),
        factory = { context ->
            OnyxInkSurfaceView(context, strokes, penSettings, guidelineStyle, onStrokeFinished, onStrokesErased)
                .also { flushHandle?.surface = it }
        },
        update = { view ->
            view.isEraserActive = isEraserActive
            view.setGuidelineStyle(guidelineStyle)
            view.syncStrokes(strokes)
        },
        onRelease = { flushHandle?.surface = null },
    )
}
