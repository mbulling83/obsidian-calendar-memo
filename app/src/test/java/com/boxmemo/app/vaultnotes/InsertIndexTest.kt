package com.boxmemo.app.vaultnotes

import org.junit.Assert.assertEquals
import org.junit.Test

class InsertIndexTest {

    @Test
    fun `newline-terminated file defaults to before the trailing empty element`() {
        // "a\nb\n".split("\n") == ["a", "b", ""] — inserting at 3 would land
        // after the final newline; the default sits at 2.
        assertEquals(2, defaultInsertIndex(listOf("a", "b", "")))
    }

    @Test
    fun `file without trailing newline defaults to end of file`() {
        assertEquals(2, defaultInsertIndex(listOf("a", "b")))
    }

    @Test
    fun `empty file defaults to zero`() {
        // "".split("\n") == [""] — the sole element is the trailing "".
        assertEquals(0, defaultInsertIndex(listOf("")))
    }

    @Test
    fun `marker advances past the inserted block after a convert`() {
        // 2 lines inserted at index 3 of a file that reloads with 10 lines.
        assertEquals(5, insertIndexAfterInsert(insertIndex = 3, insertedCount = 2, newLineCount = 10))
    }

    @Test
    fun `marker is clamped to the reloaded file length`() {
        assertEquals(4, insertIndexAfterInsert(insertIndex = 8, insertedCount = 3, newLineCount = 4))
    }
}
