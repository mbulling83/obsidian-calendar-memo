package com.boxmemo.app.scribble

import java.time.LocalDate
import java.time.YearMonth

/**
 * The searchable text recognised from one month's scribble, stored in a sidecar
 * `.idx` file next to the month's `.ink`. The ink itself is never altered — this
 * is purely a search index.
 *
 * [hash] is a fingerprint of the `.ink` text the index was built from, so an
 * unchanged month can be skipped on re-index. [dayText] maps each day cell to
 * the text written in it; [monthText] holds text written outside the day grid
 * (page title / notes area), giving a month-level fallback match.
 */
data class ScribbleIndex(
    val month: YearMonth,
    val hash: String,
    val dayText: Map<LocalDate, String>,
    val monthText: String,
)

private const val FORMAT_VERSION = "v1"

/** A point that a search matched: a specific [day] (or null for a month-level hit) plus the matched text. */
data class ScribbleHit(val month: YearMonth, val day: LocalDate?, val snippet: String)

/** Stable fingerprint of the raw `.ink` text, used to skip re-indexing unchanged months. */
fun inkHash(inkText: String): String = inkText.hashCode().toUInt().toString(16)

private fun flatten(text: String): String = text.replace('\n', ' ').replace('\t', ' ').trim()

/**
 * Serializes a [ScribbleIndex] to the same compact, pure-text, version-prefixed
 * style as the `.ink` format (so the round-trip is JVM-testable):
 *
 * ```
 * v1
 * month=2026-07
 * hash=1a2b3c
 * day=2026-07-03	dentist appointment 2pm
 * free=planning notes top of page
 * ```
 *
 * Day entries are tab-delimited and emitted in date order for deterministic
 * output; blank text is dropped. `free=` carries the month-level text.
 */
fun serializeIndex(index: ScribbleIndex): String {
    val sb = StringBuilder()
    sb.append(FORMAT_VERSION).append('\n')
    sb.append("month=").append(index.month).append('\n')
    sb.append("hash=").append(index.hash).append('\n')
    for ((date, text) in index.dayText.toSortedMap()) {
        val clean = flatten(text)
        if (clean.isEmpty()) continue
        sb.append("day=").append(date).append('\t').append(clean).append('\n')
    }
    val month = flatten(index.monthText)
    if (month.isNotEmpty()) sb.append("free=").append(month).append('\n')
    return sb.toString()
}

/** Parses what [serializeIndex] wrote. Returns null on a malformed or unversioned blob. */
fun deserializeIndex(text: String): ScribbleIndex? {
    val lines = text.split("\n")
    if (lines.isEmpty() || lines[0].trim() != FORMAT_VERSION) return null

    var month: YearMonth? = null
    var hash = ""
    val dayText = mutableMapOf<LocalDate, String>()
    var monthText = ""

    for (i in 1 until lines.size) {
        val line = lines[i]
        if (line.isBlank()) continue
        when {
            line.startsWith("month=") ->
                month = runCatching { YearMonth.parse(line.removePrefix("month=").trim()) }.getOrNull()
            line.startsWith("hash=") -> hash = line.removePrefix("hash=").trim()
            line.startsWith("day=") -> {
                val body = line.removePrefix("day=")
                val tab = body.indexOf('\t')
                if (tab > 0) {
                    val date = runCatching { LocalDate.parse(body.substring(0, tab)) }.getOrNull()
                    val value = body.substring(tab + 1)
                    if (date != null && value.isNotBlank()) dayText[date] = value
                }
            }
            line.startsWith("free=") -> monthText = line.removePrefix("free=")
        }
    }

    val resolvedMonth = month ?: return null
    return ScribbleIndex(resolvedMonth, hash, dayText, monthText)
}

/**
 * Finds every day- and month-level entry across [indexes] whose text contains
 * [query] (case-insensitive). Results are ordered newest-first: later months
 * first, later days within a month first, and a month-level hit after that
 * month's day hits.
 */
fun searchIndex(indexes: List<ScribbleIndex>, query: String): List<ScribbleHit> {
    val needle = query.trim().lowercase()
    if (needle.isEmpty()) return emptyList()

    val hits = mutableListOf<ScribbleHit>()
    for (index in indexes) {
        for ((date, value) in index.dayText) {
            if (value.lowercase().contains(needle)) {
                hits.add(ScribbleHit(index.month, date, value))
            }
        }
        if (index.monthText.lowercase().contains(needle)) {
            hits.add(ScribbleHit(index.month, null, index.monthText))
        }
    }
    return hits.sortedWith(
        compareByDescending<ScribbleHit> { it.month }
            .thenByDescending { it.day ?: LocalDate.MIN },
    )
}
