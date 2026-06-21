package com.boxmemo.app.scribble

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.YearMonth

class MonthGridRendererTest {

    @Test
    fun `february 2026 starts on a Sunday and needs five rows`() {
        // 1 Feb 2026 is a Sunday → 6 leading blanks + 28 days = 34 cells → 5 rows.
        assertEquals(5, weekRowsFor(YearMonth.of(2026, 2)))
    }

    @Test
    fun `a 28-day february starting on Monday needs four rows`() {
        // Feb 2021: 1st is a Monday → 0 blanks + 28 days = exactly 4 rows.
        assertEquals(4, weekRowsFor(YearMonth.of(2021, 2)))
    }

    @Test
    fun `a 31-day month starting late in the week needs six rows`() {
        // 1 Aug 2026 is a Saturday → 5 blanks + 31 days = 36 cells → 6 rows.
        assertEquals(6, weekRowsFor(YearMonth.of(2026, 8)))
    }
}
