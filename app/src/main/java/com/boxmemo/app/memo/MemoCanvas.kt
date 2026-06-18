package com.boxmemo.app.memo

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Drawing surface scoped to a date + [CaptureScope] (a meeting entry, the
 * Notes section, or unscoped). Captures handwriting and diagrams alike —
 * the distinction between the two is made later, when the user manually
 * triggers conversion (U7-U10), not at capture time.
 *
 * Backed by [OnyxInkSurfaceView] (Onyx Pen SDK raw drawing), the same
 * low-latency mechanism jdkruzr/aragonite uses — not a Compose `Canvas`
 * with `pointerInput`, which redraws every point through Compose's
 * recomposition pipeline and was both laggy and prone to losing strokes.
 */
@Composable
fun MemoCanvas(strokes: List<StrokePath>, onStrokeFinished: (StrokePath) -> Unit) {
    AndroidView(
        modifier = Modifier.fillMaxWidth().height(320.dp),
        factory = { context -> OnyxInkSurfaceView(context, strokes, onStrokeFinished) },
    )
}
