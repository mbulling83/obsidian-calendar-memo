package com.boxmemo.app.vaultnotes

/**
 * Pure insert-marker index rules for the Vault Notes editor, split out so the
 * logic is JVM-testable without Compose.
 */

/**
 * Default insert point for a freshly-loaded file: the end of its content.
 *
 * The file's text is split on "\n", so a newline-terminated file yields a
 * trailing "" element. Inserting *past* that element would splice new lines
 * after the final newline — appending a spurious blank line and leaving the
 * file without its trailing newline — so the default sits just before it.
 */
internal fun defaultInsertIndex(lines: List<String>): Int =
    if (lines.lastOrNull() == "") lines.size - 1 else lines.size

/**
 * Where the insert marker should sit after [insertedCount] lines were
 * inserted at [insertIndex] and the file reloaded: just after the inserted
 * block (so consecutive converts append in order rather than jumping back to
 * end-of-file), clamped to the reloaded file's [newLineCount].
 */
internal fun insertIndexAfterInsert(insertIndex: Int, insertedCount: Int, newLineCount: Int): Int =
    (insertIndex + insertedCount).coerceIn(0, newLineCount)
