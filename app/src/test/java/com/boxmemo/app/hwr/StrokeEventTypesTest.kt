package com.boxmemo.app.hwr

import org.junit.Assert.assertEquals
import org.junit.Test

class StrokeEventTypesTest {

    @Test
    fun `multi-point stroke is down, moves, up`() {
        assertEquals(listOf(0, 1, 1, 2), OnyxHWREngine.strokeEventTypes(4))
    }

    @Test
    fun `two-point stroke is down then up — the up check wins over the down check on the last point`() {
        assertEquals(listOf(0, 2), OnyxHWREngine.strokeEventTypes(2))
    }

    @Test
    fun `single-point stroke emits a down-up pair so the pen state closes`() {
        assertEquals(listOf(0, 2), OnyxHWREngine.strokeEventTypes(1))
    }

    @Test
    fun `empty stroke emits nothing`() {
        assertEquals(emptyList<Int>(), OnyxHWREngine.strokeEventTypes(0))
    }

    @Test
    fun `every stroke ends with exactly one up event`() {
        for (size in 1..6) {
            val types = OnyxHWREngine.strokeEventTypes(size)
            assertEquals("size=$size", 2, types.last())
            assertEquals("size=$size", 1, types.count { it == 2 })
            assertEquals("size=$size", 0, types.first())
        }
    }
}
