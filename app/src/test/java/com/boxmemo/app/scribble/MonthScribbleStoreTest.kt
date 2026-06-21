package com.boxmemo.app.scribble

import com.boxmemo.app.memo.StrokePath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.YearMonth

class MonthScribbleStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun stroke(vararg points: Pair<Float, Float>): StrokePath = points.toList()

    @Test
    fun `serialize then deserialize round-trips strokes and metadata`() {
        val scribble = MonthScribble(
            month = YearMonth.of(2026, 7),
            captureWidth = 1404,
            captureHeight = 1872,
            strokes = listOf(
                stroke(120.5f to 88.0f, 121.0f to 90.25f),
                stroke(10f to 20f, 11f to 21f, 12f to 22f),
            ),
        )

        val decoded = deserializeMonthScribble(serializeMonthScribble(scribble))

        assertEquals(scribble, decoded)
    }

    @Test
    fun `store save then load round-trips`() {
        val store = MonthScribbleStore(tempFolder.root)
        val scribble = MonthScribble(
            month = YearMonth.of(2026, 1),
            captureWidth = 800,
            captureHeight = 600,
            strokes = listOf(stroke(1f to 2f, 3f to 4f)),
        )

        store.save(scribble)

        assertEquals(scribble, store.load(YearMonth.of(2026, 1)))
    }

    @Test
    fun `load returns null for a month never saved`() {
        val store = MonthScribbleStore(tempFolder.root)
        assertNull(store.load(YearMonth.of(2030, 12)))
    }

    @Test
    fun `months are isolated from each other`() {
        val store = MonthScribbleStore(tempFolder.root)
        val july = MonthScribble(YearMonth.of(2026, 7), 100, 100, listOf(stroke(1f to 1f, 2f to 2f)))
        val august = MonthScribble(YearMonth.of(2026, 8), 100, 100, listOf(stroke(9f to 9f, 8f to 8f)))

        store.save(july)
        store.save(august)

        assertEquals(july, store.load(YearMonth.of(2026, 7)))
        assertEquals(august, store.load(YearMonth.of(2026, 8)))
    }

    @Test
    fun `save overwrites a month and leaves no temp file behind`() {
        val store = MonthScribbleStore(tempFolder.root)
        val month = YearMonth.of(2026, 3)
        store.save(MonthScribble(month, 100, 100, listOf(stroke(1f to 1f, 2f to 2f))))
        store.save(MonthScribble(month, 100, 100, listOf(stroke(5f to 5f, 6f to 6f))))

        assertEquals(
            listOf(stroke(5f to 5f, 6f to 6f)),
            store.load(month)!!.strokes,
        )
        val leftoverTmp = tempFolder.root.listFiles()?.any { it.name.endsWith(".tmp") } ?: false
        assertFalse("write-then-replace should not leave a .tmp file", leftoverTmp)
    }

    @Test
    fun `deserialize rejects an unversioned blob`() {
        assertNull(deserializeMonthScribble("month=2026-07\nsize=10x10\n"))
    }

    @Test
    fun `header-only blob deserializes to an empty stroke list`() {
        val decoded = deserializeMonthScribble("v1\nmonth=2026-05\nsize=10x20\n")
        assertEquals(YearMonth.of(2026, 5), decoded!!.month)
        assertTrue(decoded.strokes.isEmpty())
    }

    @Test
    fun `scaleStrokes is a no-op when sizes match`() {
        val strokes = listOf(stroke(10f to 20f, 30f to 40f))
        assertEquals(strokes, scaleStrokes(strokes, 100, 100, 100, 100))
    }

    @Test
    fun `scaleStrokes is a no-op when capture size is unknown`() {
        val strokes = listOf(stroke(10f to 20f))
        assertEquals(strokes, scaleStrokes(strokes, 0, 0, 100, 200))
    }

    @Test
    fun `scaleStrokes scales points by the per-axis ratio`() {
        val strokes = listOf(stroke(50f to 100f, 100f to 200f))
        val scaled = scaleStrokes(strokes, 100, 200, 200, 400)
        assertEquals(listOf(stroke(100f to 200f, 200f to 400f)), scaled)
    }
}
