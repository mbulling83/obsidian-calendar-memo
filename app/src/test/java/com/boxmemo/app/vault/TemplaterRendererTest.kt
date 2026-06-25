package com.boxmemo.app.vault

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * The Templater renderer evaluates the subset of `<% tp... %>` expressions we
 * can resolve on-device (dates + the note title) and strips everything it
 * can't, so a user's real Templater daily-note template produces a sensible
 * note even though Obsidian's JS runtime isn't in the loop (see CLAUDE.md —
 * we replicate the template, we don't execute Templater).
 *
 * The note's own date is the reference for `tp.date.*`, so creating a note for
 * a past/future day still fills the right date in (unlike Templater's literal
 * "now", which would be the real wall-clock day).
 */
class TemplaterRendererTest {

    // 2026-06-25 is a Thursday.
    private val date = LocalDate.of(2026, 6, 25)

    private fun render(template: String, title: String = "2026-06-25") =
        TemplaterRenderer.render(template, date, title)

    @Test
    fun `tp_date_now with no args uses ISO date`() {
        assertEquals("2026-06-25", render("<% tp.date.now() %>"))
    }

    @Test
    fun `tp_date_now honours an explicit moment format`() {
        assertEquals("2026-06-25", render("""<% tp.date.now("YYYY-MM-DD") %>"""))
    }

    @Test
    fun `tp_date_now renders day name, full month and ordinal day`() {
        assertEquals(
            "Thursday, June 25th 2026",
            render("""<% tp.date.now("dddd, MMMM Do YYYY") %>"""),
        )
    }

    @Test
    fun `tp_date_now applies a day offset`() {
        assertEquals("2026-06-24", render("""<% tp.date.now("YYYY-MM-DD", -1) %>"""))
        assertEquals("2026-06-26", render("""<% tp.date.now("YYYY-MM-DD", 1) %>"""))
    }

    @Test
    fun `tomorrow and yesterday are relative to the note date`() {
        assertEquals("2026-06-26", render("""<% tp.date.tomorrow("YYYY-MM-DD") %>"""))
        assertEquals("2026-06-24", render("""<% tp.date.yesterday("YYYY-MM-DD") %>"""))
    }

    @Test
    fun `tp_file_title resolves to the note title`() {
        assertEquals("# My Note", render("# <% tp.file.title %>", title = "My Note"))
    }

    @Test
    fun `whitespace and trim markers inside the tag are tolerated`() {
        assertEquals("2026-06-25", render("<%   tp.date.now()   %>"))
        assertEquals("2026-06-25", render("<%- tp.date.now() -%>"))
    }

    @Test
    fun `execution blocks produce no output`() {
        assertEquals(
            "before after",
            render("before <%* const x = tp.file.title; %>after"),
        )
    }

    @Test
    fun `unknown expressions are stripped, not left as raw tags`() {
        assertEquals("X Y", render("X <% tp.system.prompt(\"Name?\") %>Y"))
    }

    @Test
    fun `surrounding markdown and multiple tags are preserved`() {
        val template = """
            ---
            date: <% tp.date.now("YYYY-MM-DD") %>
            ---
            # <% tp.file.title %>

            # 👥 Meetings

            # 📝 Notes
            - created <% tp.date.now("MMM D") %>
        """.trimIndent()
        val expected = """
            ---
            date: 2026-06-25
            ---
            # 2026-06-25

            # 👥 Meetings

            # 📝 Notes
            - created Jun 25
        """.trimIndent()
        assertEquals(expected, render(template))
    }

    @Test
    fun `moment literal sections in brackets are emitted verbatim`() {
        assertEquals("Week 26 of 2026", render("""<% tp.date.now("[Week] WW [of] YYYY") %>"""))
    }
}
