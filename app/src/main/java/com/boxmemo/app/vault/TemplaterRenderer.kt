package com.boxmemo.app.vault

import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.IsoFields
import java.util.Locale

/**
 * Renders a user's Templater daily-note template *without* Obsidian's JS
 * runtime, by evaluating the handful of `<% tp... %>` expressions we can
 * resolve natively (dates and the note title) and stripping everything else.
 *
 * This is deliberately a best-effort replica, not an interpreter: dynamic
 * Templater features (prompts, user scripts, queries, frontmatter lookups)
 * can't run on-device, so those tags are removed rather than guessed. What it
 * does cover is the common shape of a daily-note template — date stamps in
 * various moment formats, the title, and relative day references — which is
 * what actually needs filling in when the app creates a missing note.
 *
 * The note's own [LocalDate] is the reference for every `tp.date.*` call, so a
 * note created for a past or future day still carries that day's date (unlike
 * Templater's literal `now`, which is always the real wall-clock day).
 */
object TemplaterRenderer {

    // Matches a Templater tag: <% ... %>, with optional execution (*) / output
    // (~) / whitespace-trim (-) markers, capturing the modifier and the inner
    // expression. DOT_MATCHES_ALL so multi-line execution blocks are captured.
    private val TAG = Regex("""<%([*~-]?)\s*(.*?)\s*-?%>""", RegexOption.DOT_MATCHES_ALL)

    private val DATE_CALL = Regex("""tp\.date\.(now|tomorrow|yesterday)\s*\((.*)\)""", RegexOption.DOT_MATCHES_ALL)
    private val QUOTED = Regex(""""([^"]*)"|'([^']*)'""")
    private val INT = Regex("""-?\d+""")

    fun render(template: String, noteDate: LocalDate, title: String): String =
        TAG.replace(template) { match ->
            val modifier = match.groupValues[1]
            // `<%* ... %>` is an execution block: it runs code but emits nothing.
            // The lambda form of replace() uses this return value literally, so
            // no $-escaping is needed (and would corrupt titles containing $).
            if (modifier == "*") "" else evaluate(match.groupValues[2].trim(), noteDate, title)
        }

    private fun evaluate(expr: String, noteDate: LocalDate, title: String): String {
        if (expr == "tp.file.title") return title

        val dateCall = DATE_CALL.matchEntire(expr)
        if (dateCall != null) {
            val func = dateCall.groupValues[1]
            val args = dateCall.groupValues[2]
            val format = QUOTED.find(args)?.let { it.groupValues[1].ifEmpty { it.groupValues[2] } } ?: "YYYY-MM-DD"
            val base = when (func) {
                "tomorrow" -> noteDate.plusDays(1)
                "yesterday" -> noteDate.minusDays(1)
                else -> noteDate.plusDays(nowOffset(args))
            }
            return MomentFormat.format(base, format)
        }

        // Anything we can't evaluate (prompts, user scripts, frontmatter, …) is
        // stripped — leaving a raw `<% … %>` tag in the note would be worse.
        return ""
    }

    /** The optional integer day-offset second argument of `tp.date.now(...)`. */
    private fun nowOffset(args: String): Long {
        val afterFormat = if (',' in args) args.substringAfter(',') else ""
        return INT.find(afterFormat)?.value?.toLongOrNull() ?: 0L
    }
}

/**
 * Formats a date with the subset of moment.js tokens that appear in real
 * daily-note templates. Moment's tokens differ from `java.time`'s (e.g. moment
 * `DD` is day-of-month, but `java.time` `DD` is day-of-year), so we translate
 * explicitly rather than handing the pattern to a [java.time.format.DateTimeFormatter].
 *
 * `[literal]` sections are emitted verbatim (moment's escaping); unrecognised
 * characters pass through as-is. Times are always midnight — the app only ever
 * creates whole-day notes — so hour/minute tokens render as zero.
 */
object MomentFormat {

    // Longest tokens first so e.g. "MMMM" is matched before "MM" before "M".
    private val TOKENS = listOf(
        "YYYY", "YY",
        "MMMM", "MMM", "MM", "Mo", "M",
        "DDDD", "DDD", "Do", "DD", "D",
        "dddd", "ddd", "dd", "do", "d",
        "WW", "Wo", "W", "ww", "wo", "w",
        "Qo", "Q",
        "E", "e",
        "HH", "H", "hh", "h",
        "mm", "m", "ss", "s",
        "A", "a",
    )

    fun format(date: LocalDate, pattern: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < pattern.length) {
            val c = pattern[i]
            if (c == '[') {
                val end = pattern.indexOf(']', i + 1)
                if (end == -1) {
                    sb.append(pattern.substring(i + 1)); break
                }
                sb.append(pattern, i + 1, end)
                i = end + 1
                continue
            }
            val token = TOKENS.firstOrNull { pattern.startsWith(it, i) }
            if (token != null) {
                sb.append(renderToken(token, date))
                i += token.length
            } else {
                sb.append(c); i++
            }
        }
        return sb.toString()
    }

    // Locale.ROOT on numeric formats so non-Latin-digit device locales can't
    // corrupt the rendered dates.
    private fun renderToken(token: String, date: LocalDate): String = when (token) {
        "YYYY" -> "%04d".format(Locale.ROOT, date.year)
        "YY" -> "%02d".format(Locale.ROOT, date.year % 100)
        "MMMM" -> date.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        "MMM" -> date.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
        "MM" -> "%02d".format(Locale.ROOT, date.monthValue)
        "Mo" -> ordinal(date.monthValue)
        "M" -> date.monthValue.toString()
        "DDDD" -> "%03d".format(Locale.ROOT, date.dayOfYear)
        "DDD" -> date.dayOfYear.toString()
        "Do" -> ordinal(date.dayOfMonth)
        "DD" -> "%02d".format(Locale.ROOT, date.dayOfMonth)
        "D" -> date.dayOfMonth.toString()
        "dddd" -> date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        "ddd" -> date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
        "dd" -> date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).take(2)
        // moment `d`/`e`: 0 = Sunday … 6 = Saturday; `do` its ordinal.
        "d", "e" -> (date.dayOfWeek.value % 7).toString()
        "do" -> ordinal(date.dayOfWeek.value % 7)
        // ISO week of year for `W`/`w` (Periodic Notes' convention).
        "WW", "ww" -> "%02d".format(Locale.ROOT, date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR))
        "W", "w" -> date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR).toString()
        "Wo", "wo" -> ordinal(date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR))
        "Q" -> date.get(IsoFields.QUARTER_OF_YEAR).toString()
        "Qo" -> ordinal(date.get(IsoFields.QUARTER_OF_YEAR))
        "E" -> date.dayOfWeek.value.toString() // ISO day of week, 1 = Monday
        // Whole-day notes have no time component.
        "HH", "hh" -> "00"
        "H" -> "0"
        "h" -> "12"
        "mm", "ss" -> "00"
        "m", "s" -> "0"
        "A" -> "AM"
        "a" -> "am"
        else -> token
    }

    @Suppress("MagicNumber")
    private fun ordinal(n: Int): String {
        val suffix = if (n % 100 in 11..13) "th" else when (n % 10) {
            1 -> "st"
            2 -> "nd"
            3 -> "rd"
            else -> "th"
        }
        return "$n$suffix"
    }
}
