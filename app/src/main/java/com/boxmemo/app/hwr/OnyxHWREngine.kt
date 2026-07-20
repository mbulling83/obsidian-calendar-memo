package com.boxmemo.app.hwr

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.MemoryFile
import android.os.ParcelFileDescriptor
import com.boxmemo.app.memo.StrokePath
import com.onyx.android.sdk.hwr.service.HWRInputArgs
import com.onyx.android.sdk.hwr.service.HWROutputArgs
import com.onyx.android.sdk.hwr.service.HWROutputCallback
import com.onyx.android.sdk.hwr.service.IHWRService
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.FileDescriptor
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

private const val RECOGNITION_TIMEOUT_MS = 10_000L

/**
 * Binds to the Boox firmware's built-in MyScript handwriting recognizer
 * over the undocumented `com.onyx.android.ksync` AIDL service, and submits
 * strokes via its hand-rolled `HWRInputProto` protobuf wire format.
 *
 * Adapted from jdkruzr/aragonite (MIT) — both the AIDL contract
 * (app/src/main/aidl/.../IHWRService.aidl) and this protobuf encoding were
 * reverse-engineered there against the real service; there is no public
 * SDK documenting this wire format, so this logic is reused as-is rather
 * than re-derived, with our simpler [StrokePath] (x, y) point list in
 * place of their richer per-point pressure/timestamp model — pressure
 * defaults to 0.5 and timestamps are synthesized at a fixed 10ms interval
 * per point, which is an acceptable fidelity loss for triggering
 * recognition (not for replaying ink).
 */
object OnyxHWREngine {

    @Volatile private var service: IHWRService? = null
    @Volatile private var bound = false
    @Volatile private var initialized = false
    @Volatile private var connectLatch = CountDownLatch(1)
    // True once bindService has registered [connection]; with BIND_AUTO_CREATE
    // the framework re-delivers onServiceConnected after a service crash on its
    // own, so binding again would stack bindings instead of reconnecting.
    @Volatile private var connectionRegistered = false
    private val bindMutex = Mutex()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = IHWRService.Stub.asInterface(binder)
            bound = true
            connectLatch.countDown()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            bound = false
            initialized = false
            // Re-arm the latch so a bindAndAwait during the outage waits for
            // the framework's automatic reconnect instead of returning stale.
            connectLatch = CountDownLatch(1)
        }
    }

    /** Binds to the service and waits for connection (up to [timeoutMs]). */
    suspend fun bindAndAwait(context: Context, timeoutMs: Long = 2000): Boolean {
        if (bound && service != null) return true

        // Serialized so two concurrent callers can't both call bindService
        // (each successful call adds an independent binding to unwind).
        bindMutex.withLock {
            if (bound && service != null) return true

            if (!connectionRegistered) {
                connectLatch = CountDownLatch(1)
                val intent = Intent().apply {
                    component = ComponentName("com.onyx.android.ksync", "com.onyx.android.ksync.service.KHwrService")
                }
                val bindStarted = try {
                    context.applicationContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
                } catch (e: Exception) {
                    return false
                }
                if (!bindStarted) return false
                connectionRegistered = true
            }

            return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                connectLatch.await(timeoutMs, TimeUnit.MILLISECONDS)
            } && service != null
        }
    }

    fun unbind(context: Context) {
        if (connectionRegistered) {
            try {
                context.applicationContext.unbindService(connection)
            } catch (_: Exception) {
            }
            connectionRegistered = false
            bound = false
            service = null
            initialized = false
        }
    }

    /**
     * Initializes the recognizer if needed. Returns false when the init call
     * throws (dead binder), the callback reports the recognizer failed to
     * activate, or no callback arrives within [RECOGNITION_TIMEOUT_MS] — so
     * callers bail out instead of recognizing against an unready engine.
     */
    private suspend fun ensureInitialized(svc: IHWRService, viewWidth: Float, viewHeight: Float): Boolean {
        if (initialized) return true

        val inputArgs = HWRInputArgs().apply {
            lang = "en_US"
            contentType = "Text"
            recognizerType = "MS_ON_SCREEN"
            this.viewWidth = viewWidth
            this.viewHeight = viewHeight
            isTextEnable = true
        }

        val activated = withTimeoutOrNull(RECOGNITION_TIMEOUT_MS) {
            suspendCancellableCoroutine<Boolean> { cont ->
                // The binder callback may fire more than once; a second resume
                // on the same continuation throws, so gate it.
                val resumed = AtomicBoolean(false)
                try {
                    svc.init(inputArgs, true, object : HWROutputCallback.Stub() {
                        override fun read(args: HWROutputArgs?) {
                            if (resumed.compareAndSet(false, true)) {
                                cont.resume(args?.recognizerActivated == true)
                            }
                        }
                    })
                } catch (e: Exception) {
                    // RemoteException/DeadObjectException (service died) or a
                    // RuntimeException rethrown across the binder — treat all
                    // as "recognizer unavailable" rather than crashing.
                    if (resumed.compareAndSet(false, true)) cont.resume(false)
                }
            }
        } == true

        initialized = activated
        return activated
    }

    /**
     * Recognizes [strokes] using the built-in recognizer. Returns recognized
     * text, empty string for no-strokes/no-result, or null if the service
     * isn't bound, couldn't initialize, or the recognize call failed.
     */
    suspend fun recognizeStrokes(strokes: List<StrokePath>, viewWidth: Float, viewHeight: Float): String? {
        val svc = service ?: return null
        if (strokes.isEmpty()) return ""

        if (!ensureInitialized(svc, viewWidth, viewHeight)) return null

        val protoBytes = buildProtobuf(strokes, viewWidth, viewHeight)
        val pfd = createMemoryFilePfd(protoBytes) ?: return null

        return try {
            withTimeoutOrNull(RECOGNITION_TIMEOUT_MS) {
                suspendCancellableCoroutine<String?> { cont ->
                    val resumed = AtomicBoolean(false)
                    fun resumeOnce(value: String?) {
                        if (resumed.compareAndSet(false, true)) cont.resume(value)
                    }
                    try {
                        svc.batchRecognize(pfd, object : HWROutputCallback.Stub() {
                            override fun read(args: HWROutputArgs?) {
                                try {
                                    val errorJson = args?.hwrResult
                                    if (!errorJson.isNullOrBlank()) {
                                        resumeOnce("")
                                        return
                                    }
                                    val resultPfd = args?.pfd
                                    if (resultPfd == null) {
                                        resumeOnce("")
                                        return
                                    }
                                    // `use` closes the fd even when parsing throws.
                                    val json = resultPfd.use { readPfdAsString(it) }
                                    resumeOnce(parseHwrResult(json))
                                } catch (e: Exception) {
                                    resumeOnce(null)
                                }
                            }
                        })
                    } catch (e: Exception) {
                        // Binder call itself failed (service died mid-call).
                        resumeOnce(null)
                    }
                }
            }
        } finally {
            pfd.close()
        }
    }

    private fun readPfdAsString(pfd: ParcelFileDescriptor): String {
        val input = java.io.FileInputStream(pfd.fileDescriptor)
        val buffered = java.io.BufferedInputStream(input)
        val baos = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        var n: Int
        while (buffered.read(buf).also { n = it } != -1) {
            baos.write(buf, 0, n)
        }
        return baos.toString("UTF-8")
    }

    private fun parseHwrResult(json: String): String {
        return try {
            val obj = JSONObject(json)
            if (obj.has("exception")) return ""
            val result = obj.optJSONObject("result")
            if (result != null) return result.optString("label", "")
            obj.optString("label", "")
        } catch (e: Exception) {
            ""
        }
    }

    // --- Protobuf encoding (hand-rolled, field numbers from HWRInputDataProto.HWRInputProto) ---

    private fun buildProtobuf(strokes: List<StrokePath>, viewWidth: Float, viewHeight: Float): ByteArray {
        val out = ByteArrayOutputStream()

        writeTag(out, 1, 2); writeString(out, "en_US")
        writeTag(out, 2, 2); writeString(out, "Text")
        writeTag(out, 4, 2); writeString(out, "MS_ON_SCREEN")
        writeTag(out, 5, 5); writeFixed32(out, viewWidth)
        writeTag(out, 6, 5); writeFixed32(out, viewHeight)
        writeTag(out, 10, 0); writeVarint(out, 1)

        for (stroke in strokes) {
            if (stroke.isEmpty()) continue
            // A 1-point stroke (an i-dot, a period) is emitted as a duplicated
            // down+up pair so the recognizer sees the pen state close; see
            // [strokeEventTypes].
            val points = if (stroke.size == 1) listOf(stroke[0], stroke[0]) else stroke
            val eventTypes = strokeEventTypes(stroke.size)
            for ((i, point) in points.withIndex()) {
                val eventType = eventTypes[i]
                val timestamp = i * 10L
                val pointerBytes = encodePointerProto(
                    x = point.first,
                    y = point.second,
                    t = timestamp,
                    f = 0.5f,
                    pointerId = 0,
                    eventType = eventType,
                    pointerType = 0,
                )
                writeTag(out, 15, 2)
                writeBytes(out, pointerBytes)
            }
        }

        return out.toByteArray()
    }

    private fun encodePointerProto(
        x: Float, y: Float, t: Long, f: Float,
        pointerId: Int, eventType: Int, pointerType: Int,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        writeTag(out, 1, 5); writeFixed32(out, x)
        writeTag(out, 2, 5); writeFixed32(out, y)
        writeTag(out, 3, 0); writeVarint(out, (t shl 1) xor (t shr 63))
        writeTag(out, 4, 5); writeFixed32(out, f)
        writeTag(out, 5, 0)
        val zigzagPid = (pointerId shl 1) xor (pointerId shr 31)
        writeVarint(out, zigzagPid.toLong())
        writeTag(out, 6, 0); writeVarint(out, eventType.toLong())
        writeTag(out, 7, 0); writeVarint(out, pointerType.toLong())
        return out.toByteArray()
    }

    private fun writeTag(out: ByteArrayOutputStream, fieldNumber: Int, wireType: Int) {
        writeVarint(out, ((fieldNumber shl 3) or wireType).toLong())
    }

    private fun writeVarint(out: ByteArrayOutputStream, value: Long) {
        var v = value
        while (v and 0x7FL.inv() != 0L) {
            out.write(((v.toInt() and 0x7F) or 0x80))
            v = v ushr 7
        }
        out.write(v.toInt() and 0x7F)
    }

    private fun writeFixed32(out: ByteArrayOutputStream, value: Float) {
        val bits = java.lang.Float.floatToIntBits(value)
        out.write(bits and 0xFF)
        out.write((bits shr 8) and 0xFF)
        out.write((bits shr 16) and 0xFF)
        out.write((bits shr 24) and 0xFF)
    }

    private fun writeString(out: ByteArrayOutputStream, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeVarint(out, bytes.size.toLong())
        out.write(bytes)
    }

    private fun writeBytes(out: ByteArrayOutputStream, bytes: ByteArray) {
        writeVarint(out, bytes.size.toLong())
        out.write(bytes)
    }

    /**
     * The per-point pointer eventType sequence for a stroke of [pointCount]
     * points: down (0), moves (1), up (2). The up check runs *before* the down
     * check so the final point of any stroke always closes the pen state; a
     * single point is encoded as a down+up pair (the point is duplicated by
     * the caller) rather than a lone down that leaves the stroke open.
     */
    internal fun strokeEventTypes(pointCount: Int): List<Int> = when {
        pointCount <= 0 -> emptyList()
        pointCount == 1 -> listOf(0, 2)
        else -> List(pointCount) { i ->
            when {
                i == pointCount - 1 -> 2
                i == 0 -> 0
                else -> 1
            }
        }
    }

    /** Writes [data] to a MemoryFile and returns a dup'd ParcelFileDescriptor, via reflection (hidden API, requires hiddenapibypass). */
    private fun createMemoryFilePfd(data: ByteArray): ParcelFileDescriptor? {
        return try {
            val memFile = MemoryFile("hwr_input", data.size)
            memFile.writeBytes(data, 0, 0, data.size)
            val method = MemoryFile::class.java.getDeclaredMethod("getFileDescriptor")
            method.isAccessible = true
            val fd = method.invoke(memFile) as FileDescriptor
            val pfd = ParcelFileDescriptor.dup(fd)
            memFile.close()
            pfd
        } catch (e: Exception) {
            null
        }
    }
}
