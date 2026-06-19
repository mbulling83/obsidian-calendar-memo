package com.boxmemo.app.hwr

import com.boxmemo.app.memo.StrokePath
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.recognition.Ink
import com.google.mlkit.vision.digitalink.recognition.RecognitionContext
import com.google.mlkit.vision.digitalink.recognition.WritingArea
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Handwriting recognition via Google ML Kit Digital Ink Recognition — fully
 * on-device and offline after a one-time language-model download. Unlike
 * [OnyxHWREngine] it touches no Boox firmware, hidden APIs, or reverse-
 * engineered wire format: it works on a plain in-memory stroke list. Mirrors
 * [OnyxHWREngine]'s shape ([recognizeStrokes] returning text / "" / null) so
 * the two are interchangeable behind the Convert action.
 */
object MlKitHWREngine {

    private const val LANGUAGE_TAG = "en-US"

    @Volatile private var recognizer: DigitalInkRecognizer? = null
    @Volatile private var modelReady = false

    private fun model(): DigitalInkRecognitionModel? {
        val identifier = try {
            DigitalInkRecognitionModelIdentifier.fromLanguageTag(LANGUAGE_TAG)
        } catch (e: Exception) {
            null
        } ?: return null
        return DigitalInkRecognitionModel.builder(identifier).build()
    }

    /** True if the language model is already on the device (no network call). */
    suspend fun isModelDownloaded(): Boolean {
        val model = model() ?: return false
        return try {
            RemoteModelManager.getInstance().isModelDownloaded(model).await()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Ensures the model is downloaded (over network, the first time) and a
     * recognizer is ready. Returns false if anything fails — e.g. no network
     * for the initial download, or ML Kit unavailable on this device.
     */
    suspend fun ensureReady(downloadIfMissing: Boolean = true): Boolean {
        if (modelReady && recognizer != null) return true
        val model = model() ?: return false
        val manager = RemoteModelManager.getInstance()
        return try {
            if (!manager.isModelDownloaded(model).await()) {
                if (!downloadIfMissing) return false
                manager.download(model, DownloadConditions.Builder().build()).await()
            }
            recognizer = DigitalInkRecognition.getClient(
                DigitalInkRecognizerOptions.builder(model).build(),
            )
            modelReady = true
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Recognizes [strokes] (canvas pixel coordinates). Returns recognized text,
     * "" for no strokes / no result, or null if the engine couldn't run (model
     * missing and undownloadable, or recognition error).
     */
    suspend fun recognizeStrokes(strokes: List<StrokePath>, viewWidth: Float, viewHeight: Float): String? {
        if (strokes.isEmpty()) return ""
        if (!ensureReady()) return null
        val rec = recognizer ?: return null

        val inkBuilder = Ink.builder()
        for (stroke in strokes) {
            if (stroke.isEmpty()) continue
            val strokeBuilder = Ink.Stroke.builder()
            stroke.forEachIndexed { i, (x, y) ->
                // Timestamps are synthesized (10ms/point); ML Kit tolerates this.
                strokeBuilder.addPoint(Ink.Point.create(x, y, i * 10L))
            }
            inkBuilder.addStroke(strokeBuilder.build())
        }

        return try {
            // preContext is a *required* property of RecognitionContext.Builder
            // in this ML Kit version — omitting it makes build() throw
            // IllegalStateException. Empty = no text precedes this writing.
            val context = RecognitionContext.builder()
                .setPreContext("")
                .setWritingArea(WritingArea(viewWidth, viewHeight))
                .build()
            withTimeoutOrNull(10_000) {
                rec.recognize(inkBuilder.build(), context).await()
                    .candidates.firstOrNull()?.text.orEmpty()
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Bridges a Play-services [Task] to a coroutine without an extra dependency. */
    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resumeWithException(it) }
        addOnCanceledListener { cont.cancel() }
    }
}
