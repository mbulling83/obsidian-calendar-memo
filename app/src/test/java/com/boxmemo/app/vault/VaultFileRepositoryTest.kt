package com.boxmemo.app.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VaultFileRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    // --- insertLines (pure) ---

    @Test
    fun `inserts at end of file`() {
        val content = "# Title\nfirst line"
        val result = insertLines(content, atLine = 2, newLines = listOf("- a", "- b"))
        assertEquals("# Title\nfirst line\n- a\n- b", result)
    }

    @Test
    fun `inserts at top of file`() {
        val content = "first\nsecond"
        val result = insertLines(content, atLine = 0, newLines = listOf("- new"))
        assertEquals("- new\nfirst\nsecond", result)
    }

    @Test
    fun `inserts in the middle pushing later lines down`() {
        val content = "one\ntwo\nthree"
        val result = insertLines(content, atLine = 1, newLines = listOf("- x"))
        assertEquals("one\n- x\ntwo\nthree", result)
    }

    @Test
    fun `preserves a trailing newline when appending before it`() {
        val content = "a\nb\n" // splits to [a, b, ""]
        // atLine 2 inserts before the trailing empty element, keeping the final newline.
        val result = insertLines(content, atLine = 2, newLines = listOf("- c"))
        assertEquals("a\nb\n- c\n", result)
    }

    @Test
    fun `coerces an out-of-range line index to the end`() {
        val content = "a\nb"
        val result = insertLines(content, atLine = 99, newLines = listOf("- c"))
        assertEquals("a\nb\n- c", result)
    }

    @Test
    fun `inserting into an empty file yields the new line above the empty line`() {
        // "" splits to a single empty line, so the insert lands above it.
        val result = insertLines("", atLine = 0, newLines = listOf("- only"))
        assertEquals("- only\n", result)
    }

    @Test
    fun `no new lines leaves content untouched`() {
        val content = "a\nb"
        assertEquals(content, insertLines(content, atLine = 1, newLines = emptyList()))
    }

    // --- VaultFileRepository (I/O) ---

    @Test
    fun `readFile returns content for an existing file and null otherwise`() {
        val repo = VaultFileRepository()
        val file = tempFolder.newFile("note.md")
        file.writeText("hello")

        assertEquals("hello", repo.readFile(file))
        assertNull(repo.readFile(tempFolder.root.resolve("missing.md")))
    }

    @Test
    fun `insertLinesAt writes the spliced content via rename`() {
        val repo = VaultFileRepository()
        val file = tempFolder.newFile("note.md")
        file.writeText("# Notes\n- existing")

        val ok = repo.insertLinesAt(file, atLine = 2, lines = listOf("- added"))

        assertTrue(ok)
        assertEquals("# Notes\n- existing\n- added", file.readText())
        // The temp sibling must not linger after the atomic rename.
        assertFalse(tempFolder.root.resolve("note.md.tmp").exists())
    }

    @Test
    fun `insertLinesAt returns false for a missing file`() {
        val repo = VaultFileRepository()
        val missing = tempFolder.root.resolve("nope.md")
        assertFalse(repo.insertLinesAt(missing, atLine = 0, lines = listOf("- x")))
    }
}
