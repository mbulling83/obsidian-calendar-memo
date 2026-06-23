package com.boxmemo.app.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SectionHeadingTest {

    @Test
    fun `matches ignores the number of leading hashes`() {
        assertTrue(SectionHeading.matches("# Meetings", "Meetings"))
        assertTrue(SectionHeading.matches("## Meetings", "Meetings"))
        assertTrue(SectionHeading.matches("### Meetings", "# Meetings"))
    }

    @Test
    fun `matches ignores surrounding whitespace and case`() {
        assertTrue(SectionHeading.matches("   #   MEETINGS   ", "meetings"))
        assertTrue(SectionHeading.matches("# meetings", "Meetings"))
    }

    @Test
    fun `matches when the configured value itself carries hashes or emoji`() {
        assertTrue(SectionHeading.matches("# 👥 Meetings", "## 👥 meetings"))
        assertTrue(SectionHeading.matches("#  👥 Meetings", "# 👥 Meetings"))
    }

    @Test
    fun `does not match different heading text`() {
        assertFalse(SectionHeading.matches("# Notes", "Meetings"))
        assertFalse(SectionHeading.matches("# Meetings Today", "Meetings"))
    }

    @Test
    fun `non-heading lines never match`() {
        assertFalse(SectionHeading.matches("Meetings", "Meetings")) // no hash → not an ATX heading
        assertFalse(SectionHeading.matches("- Meetings", "Meetings"))
        assertFalse(SectionHeading.matches("#Meetings", "Meetings")) // no space after hash → a tag, not a heading
    }

    @Test
    fun `blank configured heading never matches`() {
        assertFalse(SectionHeading.matches("# Meetings", ""))
        assertFalse(SectionHeading.matches("# Meetings", "   "))
    }

    @Test
    fun `headingText strips markers or returns null for non-headings`() {
        assertEquals("Meetings", SectionHeading.headingText("##  Meetings  "))
        assertNull(SectionHeading.headingText("#tag"))
        assertNull(SectionHeading.headingText("plain text"))
        assertNull(SectionHeading.headingText("#"))
    }

    @Test
    fun `isSectionBoundary recognises any heading or a horizontal rule`() {
        assertTrue(SectionHeading.isSectionBoundary("# Anything"))
        assertTrue(SectionHeading.isSectionBoundary("### Sub heading"))
        assertTrue(SectionHeading.isSectionBoundary("---"))
        assertFalse(SectionHeading.isSectionBoundary("- a bullet"))
        assertFalse(SectionHeading.isSectionBoundary(""))
    }
}
