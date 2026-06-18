package com.boxmemo.app.memo

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas

/**
 * Drawing surface scoped to a date + [CaptureScope] (a meeting entry, the
 * Notes section, or unscoped). Captures handwriting and diagrams alike —
 * the distinction between the two is made later, when the user manually
 * triggers conversion (U7-U10), not at capture time.
 *
 * Plain Compose Path-based capture rather than the full Jetpack Ink
 * authoring API: simpler and safe to get right without guessing at a large
 * unfamiliar API surface, and the OCR/diagram pipelines only need the
 * resulting bitmap, not Ink-specific stroke fidelity.
 */
@Composable
fun MemoCanvas(strokes: List<StrokePath>, onStrokeFinished: (StrokePath) -> Unit) {
    var currentStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp)
            .background(Color.White)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset -> currentStroke = listOf(offset) },
                    onDrag = { change, _ -> currentStroke = currentStroke + change.position },
                    onDragEnd = {
                        if (currentStroke.size > 1) {
                            onStrokeFinished(currentStroke.map { it.x to it.y })
                        }
                        currentStroke = emptyList()
                    },
                )
            },
    ) {
        fun drawStroke(points: List<Offset>) {
            if (points.size < 2) return
            val path = Path().apply {
                moveTo(points.first().x, points.first().y)
                points.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(path, color = Color.Black, style = Stroke(width = 4f))
        }

        strokes.forEach { stroke -> drawStroke(stroke.map { (x, y) -> Offset(x, y) }) }
        drawStroke(currentStroke)
    }
}
