package com.boxmemo.app.memo

import android.content.Context
import com.boxmemo.app.hwr.HwrEngineType
import com.boxmemo.app.hwr.MlKitHWREngine
import com.boxmemo.app.hwr.OnyxHWREngine
import kotlin.math.ceil

private const val CAPTURE_MARGIN = 40
private const val MIN_CAPTURE_WIDTH = 400
private const val MIN_CAPTURE_HEIGHT = 200

/**
 * Sizes the HWR capture to contain all ink. The canvas fills the remaining
 * screen — wider/taller than a fixed default on a U7 — so a hardcoded size
 * would silently clip strokes written low or to the right, dropping that ink
 * from recognition. Deriving from the strokes' extent guarantees nothing is lost.
 */
fun captureSizeFor(strokes: List<StrokePath>): Pair<Int, Int> {
    val points = strokes.flatten()
    if (points.isEmpty()) return MIN_CAPTURE_WIDTH to MIN_CAPTURE_HEIGHT
    val width = maxOf(MIN_CAPTURE_WIDTH, ceil(points.maxOf { it.first }).toInt() + CAPTURE_MARGIN)
    val height = maxOf(MIN_CAPTURE_HEIGHT, ceil(points.maxOf { it.second }).toInt() + CAPTURE_MARGIN)
    return width to height
}

/** Outcome of a recognition attempt across either engine. */
sealed interface RecognitionOutcome {
    /** Recognition ran; [value] is null or blank when nothing was recognized. */
    data class Recognized(val value: String?) : RecognitionOutcome

    /** The selected engine couldn't be readied; [message] explains why. */
    data class Unavailable(val message: String) : RecognitionOutcome
}

/**
 * Runs handwriting recognition for [strokes] using the selected [engine],
 * sizing the capture to the ink. Shared by the daily-note conversion flow and
 * the Vault Notes screen so the Onyx/ML Kit branching lives in one place.
 * Suspends; call from a coroutine.
 */
suspend fun recognizeStrokes(
    context: Context,
    engine: HwrEngineType,
    strokes: List<StrokePath>,
): RecognitionOutcome {
    val (width, height) = captureSizeFor(strokes)
    return when (engine) {
        HwrEngineType.ONYX -> {
            if (!OnyxHWREngine.bindAndAwait(context)) {
                RecognitionOutcome.Unavailable("Onyx HWR unavailable.")
            } else {
                RecognitionOutcome.Recognized(
                    OnyxHWREngine.recognizeStrokes(strokes, width.toFloat(), height.toFloat()),
                )
            }
        }
        HwrEngineType.ML_KIT -> {
            if (!MlKitHWREngine.ensureReady()) {
                RecognitionOutcome.Unavailable(
                    "ML Kit unavailable — its language model needs a one-time download " +
                        "(connect to a network, or use the Download button in Settings).",
                )
            } else {
                RecognitionOutcome.Recognized(
                    MlKitHWREngine.recognizeStrokes(strokes, width.toFloat(), height.toFloat()),
                )
            }
        }
    }
}
