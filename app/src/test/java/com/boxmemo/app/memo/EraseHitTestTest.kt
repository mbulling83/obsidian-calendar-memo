package com.boxmemo.app.memo

import org.junit.Assert.assertEquals
import org.junit.Test

class EraseHitTestTest {

    @Test
    fun `removes strokes with a point inside the erase radius, leaves others untouched`() {
        val nearStroke: StrokePath = listOf(10f to 10f, 12f to 12f)
        val farStroke: StrokePath = listOf(500f to 500f, 510f to 510f)

        val remaining = removeErasedStrokes(
            strokes = listOf(nearStroke, farStroke),
            erasePoints = listOf(11f to 11f),
            eraseRadius = 20f,
        )

        assertEquals(listOf(farStroke), remaining)
    }

    @Test
    fun `erasing far from every stroke removes nothing`() {
        val stroke: StrokePath = listOf(10f to 10f)

        val remaining = removeErasedStrokes(
            strokes = listOf(stroke),
            erasePoints = listOf(900f to 900f),
            eraseRadius = 20f,
        )

        assertEquals(listOf(stroke), remaining)
    }

    @Test
    fun `no erase points removes nothing`() {
        val stroke: StrokePath = listOf(10f to 10f)

        val remaining = removeErasedStrokes(strokes = listOf(stroke), erasePoints = emptyList(), eraseRadius = 20f)

        assertEquals(listOf(stroke), remaining)
    }
}
