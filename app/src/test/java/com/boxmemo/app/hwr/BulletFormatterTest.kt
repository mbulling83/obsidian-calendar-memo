package com.boxmemo.app.hwr

import org.junit.Assert.assertEquals
import org.junit.Test

class BulletFormatterTest {

    @Test
    fun `splits multi-sentence text into multiple bullets at sentence boundaries`() {
        val bullets = formatAsBullets("Discussed the roadmap. Agreed on next steps. Follow up next week.")

        assertEquals(
            listOf(
                "Discussed the roadmap.",
                "Agreed on next steps.",
                "Follow up next week.",
            ),
            bullets,
        )
    }

    @Test
    fun `a single short phrase produces exactly one bullet`() {
        val bullets = formatAsBullets("pick up dry cleaning")

        assertEquals(listOf("pick up dry cleaning"), bullets)
    }

    @Test
    fun `blank text produces no bullets`() {
        assertEquals(emptyList<String>(), formatAsBullets("   "))
    }

    @Test
    fun `formatAsMeetingDetailLines indents bullets to match nested meeting detail style`() {
        val lines = formatAsMeetingDetailLines("First point. Second point.")

        assertEquals(listOf("\t- First point.", "\t- Second point."), lines)
    }

    @Test
    fun `formatAsNoteLines produces plain non-indented bullets`() {
        val lines = formatAsNoteLines("First point. Second point.")

        assertEquals(listOf("- First point.", "- Second point."), lines)
    }
}
