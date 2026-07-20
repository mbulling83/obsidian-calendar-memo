package com.boxmemo.app.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VaultFileIndexTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun seedVault() {
        tempFolder.newFile("Inbox.md")
        tempFolder.newFile("not-a-note.txt")
        val projects = tempFolder.newFolder("Projects")
        projects.resolve("Apollo.md").writeText("# Apollo")
        projects.resolve("Gemini.md").writeText("# Gemini")
        // Hidden config folder should be ignored entirely.
        val hidden = tempFolder.newFolder(".obsidian")
        hidden.resolve("workspace.md").writeText("internal")
    }

    @Test
    fun `childrenOf lists folders then markdown files, skipping non-md and hidden`() {
        seedVault()
        val index = VaultFileIndex(tempFolder.root.path)

        val children = index.childrenOf(tempFolder.root)

        // Folder first (Projects), then the markdown file (Inbox.md); .txt and
        // the hidden .obsidian folder are excluded.
        assertEquals(2, children.size)
        assertTrue(children[0] is VaultEntry.Folder)
        assertEquals("Projects", children[0].name)
        assertTrue(children[1] is VaultEntry.MarkdownFile)
        assertEquals("Inbox.md", children[1].name)
    }

    @Test
    fun `search matches markdown filenames case-insensitively across folders`() {
        seedVault()
        val index = VaultFileIndex(tempFolder.root.path)

        val results = index.search("gemini").map { it.name }

        assertEquals(listOf("Gemini.md"), results)
    }

    @Test
    fun `search matches a shared substring across nested files`() {
        seedVault()
        val index = VaultFileIndex(tempFolder.root.path)

        val results = index.search(".md").map { it.name }.sorted()

        assertEquals(listOf("Apollo.md", "Gemini.md", "Inbox.md"), results)
    }

    @Test
    fun `blank query returns nothing`() {
        seedVault()
        val index = VaultFileIndex(tempFolder.root.path)
        assertTrue(index.search("   ").isEmpty())
    }

    @Test
    fun `search does not walk deeper than the depth cap`() {
        var dir = tempFolder.root
        // Depth 8 is beyond the walk's 7-level cap; depth 2 is within it.
        repeat(8) { level ->
            dir = dir.resolve("level${level + 1}").apply { mkdirs() }
            if (level == 1) dir.resolve("Shallow.md").writeText("found")
        }
        dir.resolve("Deep.md").writeText("hidden")
        val index = VaultFileIndex(tempFolder.root.path)

        val results = index.search("md").map { it.name }

        assertEquals(listOf("Shallow.md"), results)
    }

    @Test
    fun `null or non-directory root yields no entries`() {
        assertNull(VaultFileIndex(null).rootEntry())
        assertTrue(VaultFileIndex(null).search("x").isEmpty())
        assertTrue(VaultFileIndex("/no/such/path/here").childrenOf(tempFolder.root.resolve("ghost")).isEmpty())
    }
}
