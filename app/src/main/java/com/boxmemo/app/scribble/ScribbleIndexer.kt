package com.boxmemo.app.scribble

import android.content.Context
import com.boxmemo.app.hwr.HwrEngineType
import com.boxmemo.app.memo.RecognitionOutcome
import com.boxmemo.app.memo.recognizeStrokes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.YearMonth

/**
 * Builds and reads the searchable text sidecars (`.idx`) for the scribble
 * calendar. Each month's ink (`<YearMonth>.ink`) gets a sibling
 * `<YearMonth>.idx` holding the recognised text, so the handwriting on the page
 * is never touched.
 *
 * Recognition runs at the screen's existing flush moments (leaving a month or
 * closing the screen) and on a one-time backfill of months that predate the
 * feature. Re-indexing is skipped when the `.ink` is unchanged (hash match), and
 * a month is left un-indexed — to retry next time — if the HWR engine can't be
 * readied, rather than recording empty text.
 *
 * The pure parts (cell bucketing, serialization, search) live in [ScribbleGrid]
 * and [ScribbleIndex] and are unit-tested; this class is the Android glue
 * (recognition + file I/O), exercised on device.
 */
class ScribbleIndexer(
    private val context: Context,
    private val store: MonthScribbleStore,
    private val baseDir: File,
    private val density: Float,
    private val engine: () -> HwrEngineType,
) {

    private fun inkFile(month: YearMonth) = File(baseDir, "$month.ink")
    private fun idxFile(month: YearMonth) = File(baseDir, "$month.idx")

    /**
     * Re-indexes [month] if its ink has changed since the last index. No-op when
     * the month has no ink, the index is already current, or recognition is
     * unavailable. Safe to call after every flush.
     */
    suspend fun reindexMonth(month: YearMonth) = withContext(Dispatchers.IO) {
        val ink = inkFile(month)
        if (!ink.exists()) return@withContext
        val inkText = runCatching { ink.readText() }.getOrNull() ?: return@withContext
        val hash = inkHash(inkText)

        val existing = readIndexFile(month)
        if (existing?.hash == hash) return@withContext // unchanged — nothing to do

        val scribble = store.load(month) ?: return@withContext
        if (scribble.strokes.isEmpty()) return@withContext

        val buckets = bucketStrokesByDay(scribble, density)
        val selectedEngine = engine()

        val dayText = mutableMapOf<java.time.LocalDate, String>()
        var monthText = ""
        for ((date, strokes) in buckets) {
            val recognized = when (val outcome = recognizeStrokes(context, selectedEngine, strokes)) {
                is RecognitionOutcome.Recognized -> outcome.value?.trim().orEmpty()
                is RecognitionOutcome.Unavailable -> return@withContext // retry on a later flush
            }
            if (recognized.isEmpty()) continue
            if (date == null) {
                monthText = if (monthText.isEmpty()) recognized else "$monthText $recognized"
            } else {
                dayText[date] = recognized
            }
        }

        // Non-empty ink that recognised to nothing at all is treated like
        // Unavailable: skip the write so a later flush retries, rather than
        // recording a permanently empty index for the month.
        if (dayText.isEmpty() && monthText.isBlank()) return@withContext

        writeIndexFile(ScribbleIndex(month, hash, dayText, monthText))
    }

    /** Re-indexes every month whose `.ink` lacks a current `.idx`. One-time cost per month. */
    suspend fun backfill() = withContext(Dispatchers.IO) {
        val inkFiles = baseDir.listFiles { f -> f.name.endsWith(".ink") } ?: return@withContext
        for (file in inkFiles) {
            val month = runCatching { YearMonth.parse(file.name.removeSuffix(".ink")) }.getOrNull() ?: continue
            reindexMonth(month)
        }
    }

    /** Loads all current `.idx` files, for searching. */
    fun loadAllIndexes(): List<ScribbleIndex> {
        val idxFiles = baseDir.listFiles { f -> f.name.endsWith(".idx") } ?: return emptyList()
        return idxFiles.mapNotNull { file ->
            runCatching { deserializeIndex(file.readText()) }.getOrNull()
        }
    }

    private fun readIndexFile(month: YearMonth): ScribbleIndex? {
        val file = idxFile(month)
        if (!file.exists()) return null
        return runCatching { deserializeIndex(file.readText()) }.getOrNull()
    }

    private fun writeIndexFile(index: ScribbleIndex) {
        // The index is a rebuildable cache: any I/O failure here (including a
        // lost race on the tmp file) just means a retry on a later flush, so
        // nothing may throw out of the calling coroutine.
        runCatching {
            if (!baseDir.exists()) baseDir.mkdirs()
            val target = idxFile(index.month)
            // Unique tmp name so concurrent reindexes can't clobber each other.
            val tmp = File.createTempFile("${index.month}.idx-", ".tmp", baseDir)
            tmp.writeText(serializeIndex(index))
            if (!tmp.renameTo(target)) {
                runCatching { target.writeText(tmp.readText()) }
                tmp.delete()
            }
        }
    }
}
