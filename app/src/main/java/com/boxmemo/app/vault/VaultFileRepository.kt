package com.boxmemo.app.vault

import java.io.File

/**
 * Splices [newLines] into [content] so they appear starting at display-line
 * [atLine] (0 = top of file, [lineCount] = end), pushing any existing line at
 * that position down. [atLine] is coerced into the valid range so an out-of-date
 * line index can never corrupt the file. Pure and JVM-testable; the file I/O
 * lives in [VaultFileRepository].
 */
fun insertLines(content: String, atLine: Int, newLines: List<String>): String {
    if (newLines.isEmpty()) return content
    // Splitting on "\n" keeps a trailing empty element for a file ending in a
    // newline, so rejoining round-trips the original content exactly.
    val lines = content.split("\n")
    val index = atLine.coerceIn(0, lines.size)
    val merged = lines.subList(0, index) + newLines + lines.subList(index, lines.size)
    return merged.joinToString("\n")
}

/**
 * Reads and writes arbitrary `.md` files anywhere in the vault, applying the
 * same write-then-replace discipline as [DailyNoteRepository] so a concurrently
 * running LiveSync watcher never observes a half-written file. This is the
 * single owner of file I/O for the Vault Notes screen.
 */
class VaultFileRepository {

    fun readFile(file: File): String? =
        if (file.exists() && file.isFile) file.readText() else null

    /**
     * Inserts [lines] into [file] at display-line [atLine] (see [insertLines]),
     * then atomically replaces the original. Returns false if the file can't be
     * read or the rename fails.
     */
    fun insertLinesAt(file: File, atLine: Int, lines: List<String>): Boolean {
        val content = readFile(file) ?: return false
        val updated = insertLines(content, atLine, lines)
        val tempFile = File(file.parentFile, "${file.name}.tmp")
        tempFile.writeText(updated)
        return tempFile.renameTo(file)
    }
}
