package com.boxmemo.app.vault

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields

/**
 * Pure naming/path helpers for saved handwriting diagrams (PNG attachments).
 * Kept Android-free so the filename, folder, and collision rules are
 * JVM-testable; the bitmap render and file I/O live in [DiagramRepository] and
 * `memo/StrokeRenderer`.
 *
 * Diagrams are organised under `attachments/Diagrams/<week-based-year>/W<week>`
 * and named with date + time + meeting/note name so the attachments folder is
 * easy to scan and sort.
 */

private val ILLEGAL_FILENAME = Regex("""[\\/:*?"<>|\[\]]""")
private val WHITESPACE = Regex("""\s+""")
private val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH.mm")

/**
 * Strips characters that are illegal in filenames (and Obsidian `[[ ]]` wiki
 * brackets) from [raw], collapsing any resulting whitespace runs so a title
 * like `1:1 with [[Bob]]` becomes a clean `1 1 with Bob`.
 */
fun sanitizeDiagramName(raw: String): String =
    raw.replace(ILLEGAL_FILENAME, " ").replace(WHITESPACE, " ").trim()

/** Base name (no extension) for a diagram saved under a meeting. */
fun meetingDiagramBaseName(date: LocalDate, startTime: String, title: String): String {
    val time = startTime.replace(":", ".").trim()
    val parts = listOf(date.toString(), time, sanitizeDiagramName(title))
    return parts.filter { it.isNotEmpty() }.joinToString(" ")
}

/** Base name (no extension) for a diagram saved into the page-level Notes section. */
fun notesDiagramBaseName(date: LocalDate, time: LocalTime): String =
    "$date ${time.format(TIME_FMT)} Notes"

/** Base name (no extension) for a diagram saved into an arbitrary vault file. */
fun fileDiagramBaseName(date: LocalDate, time: LocalTime, fileName: String): String {
    val base = sanitizeDiagramName(fileName.removeSuffix(".md"))
    return "$date ${time.format(TIME_FMT)} $base"
}

/** Vault-relative folder a diagram for [date] is stored in (ISO week-based year/week). */
fun diagramRelativeDir(date: LocalDate): String {
    val week = date.get(WeekFields.ISO.weekOfWeekBasedYear())
    val year = date.get(WeekFields.ISO.weekBasedYear())
    return "attachments/Diagrams/%d/W%02d".format(year, week)
}

/**
 * Resolves a non-colliding `<baseName>.png` against the [existing] filenames in
 * the target folder, appending ` (2)`, ` (3)`, … until free.
 */
fun uniqueDiagramFileName(baseName: String, existing: Set<String>): String {
    val candidate = "$baseName.png"
    if (candidate !in existing) return candidate
    var n = 2
    while ("$baseName ($n).png" in existing) n++
    return "$baseName ($n).png"
}
