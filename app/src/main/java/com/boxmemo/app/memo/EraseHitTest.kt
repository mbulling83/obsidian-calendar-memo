package com.boxmemo.app.memo

import kotlin.math.hypot

/**
 * Whole-stroke eraser (not pixel-level partial erase): any stroke with at
 * least one point within [eraseRadius] of any erase point is removed
 * entirely. Simpler than partial erasing and a reasonable v1 — matches
 * the stylus eraser button triggering Onyx's raw-erasing callback.
 */
fun removeErasedStrokes(strokes: List<StrokePath>, erasePoints: List<Pair<Float, Float>>, eraseRadius: Float): List<StrokePath> {
    if (erasePoints.isEmpty()) return strokes
    return strokes.filterNot { stroke ->
        stroke.any { (sx, sy) -> erasePoints.any { (ex, ey) -> hypot((sx - ex).toDouble(), (sy - ey).toDouble()) <= eraseRadius } }
    }
}
