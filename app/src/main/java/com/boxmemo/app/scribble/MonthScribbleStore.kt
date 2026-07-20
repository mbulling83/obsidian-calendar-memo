package com.boxmemo.app.scribble

import com.boxmemo.app.memo.StrokePath
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.time.YearMonth

/**
 * One month's worth of freeform ink for the scribble calendar.
 *
 * [captureWidth]/[captureHeight] record the canvas pixel size the strokes were
 * drawn at, so they can be rescaled if the canvas is ever a different size on
 * reopen (e.g. a device-orientation change). On a fixed-orientation Boox this is
 * a no-op, but it keeps ink aligned to the grid cells either way.
 */
data class MonthScribble(
    val month: YearMonth,
    val captureWidth: Int,
    val captureHeight: Int,
    val strokes: List<StrokePath>,
)

private const val FORMAT_VERSION = "v1"

/**
 * Serializes a [MonthScribble] to a compact, pure-text format (no JSON library,
 * so the round-trip is unit-testable on the JVM without Android stubs):
 *
 * ```
 * v1
 * month=2026-07
 * size=1404x1872
 * 120.5,88.0 121.0,90.2 ...   <- one line per stroke, "x,y" points space-separated
 * ```
 */
fun serializeMonthScribble(scribble: MonthScribble): String {
    val sb = StringBuilder()
    sb.append(FORMAT_VERSION).append('\n')
    sb.append("month=").append(scribble.month).append('\n')
    sb.append("size=").append(scribble.captureWidth).append('x').append(scribble.captureHeight).append('\n')
    for (stroke in scribble.strokes) {
        sb.append(stroke.joinToString(" ") { (x, y) -> "$x,$y" }).append('\n')
    }
    return sb.toString()
}

/** Parses what [serializeMonthScribble] wrote. Returns null on a malformed or unversioned blob. */
fun deserializeMonthScribble(text: String): MonthScribble? {
    val lines = text.split("\n")
    if (lines.isEmpty() || lines[0].trim() != FORMAT_VERSION) return null

    var month: YearMonth? = null
    var width = 0
    var height = 0
    val strokes = mutableListOf<StrokePath>()

    for (i in 1 until lines.size) {
        val line = lines[i]
        if (line.isBlank()) continue
        when {
            line.startsWith("month=") ->
                month = runCatching { YearMonth.parse(line.removePrefix("month=").trim()) }.getOrNull()
            line.startsWith("size=") -> {
                val parts = line.removePrefix("size=").trim().split("x")
                if (parts.size == 2) {
                    width = parts[0].toIntOrNull() ?: 0
                    height = parts[1].toIntOrNull() ?: 0
                }
            }
            else -> {
                val stroke = line.trim().split(" ").mapNotNull { pair ->
                    val xy = pair.split(",")
                    if (xy.size != 2) return@mapNotNull null
                    val x = xy[0].toFloatOrNull() ?: return@mapNotNull null
                    val y = xy[1].toFloatOrNull() ?: return@mapNotNull null
                    x to y
                }
                if (stroke.isNotEmpty()) strokes.add(stroke)
            }
        }
    }

    val resolvedMonth = month ?: return null
    return MonthScribble(resolvedMonth, width, height, strokes)
}

/**
 * Rescales [strokes] captured at [fromW]x[fromH] to a [toW]x[toH] canvas.
 * A no-op when the sizes match or the capture size is unknown (0).
 */
fun scaleStrokes(
    strokes: List<StrokePath>,
    fromW: Int,
    fromH: Int,
    toW: Int,
    toH: Int,
): List<StrokePath> {
    if (fromW <= 0 || fromH <= 0) return strokes
    if (fromW == toW && fromH == toH) return strokes
    val sx = toW.toFloat() / fromW
    val sy = toH.toFloat() / fromH
    return strokes.map { stroke -> stroke.map { (x, y) -> x * sx to y * sy } }
}

/**
 * Sole owner of the scribble calendar's on-device ink files. One file per month
 * under [baseDir] (the app's internal storage in production; a temp dir in
 * tests). Writes go to a `.tmp` sibling and are renamed over the target, the
 * same write-then-replace discipline the vault repositories use.
 */
class MonthScribbleStore(val baseDir: File) {

    // Serializes all save/load I/O: concurrent saves of the same month (e.g. a
    // straggling autosave racing the exit save) must not interleave, and a load
    // must never observe a half-finished replace.
    private val mutex = Mutex()

    private fun fileFor(month: YearMonth) = File(baseDir, "$month.ink")

    suspend fun load(month: YearMonth): MonthScribble? = mutex.withLock {
        val file = fileFor(month)
        if (!file.exists()) return@withLock null
        val parsed = runCatching { deserializeMonthScribble(file.readText()) }.getOrNull()
        if (parsed == null) {
            // The file exists but can't be read/parsed. Preserve it before the
            // app treats the month as empty — the next save would otherwise
            // destroy whatever is left of the original.
            backupCorrupt(file)
        }
        parsed
    }

    suspend fun save(scribble: MonthScribble): Unit = mutex.withLock {
        if (!baseDir.exists()) baseDir.mkdirs()
        val target = fileFor(scribble.month)
        // Unique tmp name: a fixed sibling name would let two in-flight saves
        // clobber each other's tmp before the rename.
        val tmp = File.createTempFile("${scribble.month}.ink-", ".tmp", baseDir)
        tmp.writeText(serializeMonthScribble(scribble))
        if (!tmp.renameTo(target)) {
            // renameTo can fail across some filesystems; fall back to a copy.
            // runCatching: a lost race (tmp already gone) must not throw out of
            // a fire-and-forget save.
            runCatching { target.writeText(tmp.readText()) }
            tmp.delete()
        }
    }

    /** Renames a corrupt ink file to `<name>.corrupt-<n>`, never overwriting an earlier backup. */
    private fun backupCorrupt(file: File) {
        var n = 1
        var backup = File(baseDir, "${file.name}.corrupt-$n")
        while (backup.exists()) {
            n++
            backup = File(baseDir, "${file.name}.corrupt-$n")
        }
        runCatching { file.renameTo(backup) }
    }
}
