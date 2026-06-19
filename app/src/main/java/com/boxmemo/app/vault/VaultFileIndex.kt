package com.boxmemo.app.vault

import java.io.File

/** A folder or a Markdown file in the vault tree. */
sealed interface VaultEntry {
    val file: File
    val name: String

    data class Folder(override val file: File) : VaultEntry {
        override val name: String get() = file.name
    }

    data class MarkdownFile(override val file: File) : VaultEntry {
        override val name: String get() = file.name
    }
}

/**
 * Enumerates the vault tree for the file picker, showing only `.md` files (plus
 * the folders to reach them). Direct [File] access against the configured vault
 * root, matching the rest of the app's storage approach.
 *
 * The explorer expands lazily one folder at a time via [childrenOf] so a large
 * vault is never walked in full; [search] does walk the tree, but only matches
 * filenames (cheap) and is expected to run off the main thread.
 */
class VaultFileIndex(private val vaultRoot: String?) {

    private fun root(): File? = vaultRoot?.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.isDirectory }

    /** The vault root as a folder entry, or null if no (valid) vault is configured. */
    fun rootEntry(): VaultEntry.Folder? = root()?.let(VaultEntry::Folder)

    /**
     * Immediate children of [dir]: subfolders first (alphabetical), then `.md`
     * files (alphabetical). Hidden entries (dot-prefixed, e.g. `.obsidian`) are
     * skipped. Non-markdown files are omitted.
     */
    fun childrenOf(dir: File): List<VaultEntry> {
        val entries = dir.listFiles()?.filterNot { it.name.startsWith(".") } ?: return emptyList()
        val folders = entries.filter { it.isDirectory }
            .sortedBy { it.name.lowercase() }
            .map(VaultEntry::Folder)
        val files = entries.filter { it.isFile && it.extension.equals("md", ignoreCase = true) }
            .sortedBy { it.name.lowercase() }
            .map(VaultEntry::MarkdownFile)
        return folders + files
    }

    /**
     * All `.md` files in the vault whose filename contains [query]
     * (case-insensitive), sorted by name. A blank query returns nothing. Hidden
     * folders are skipped.
     */
    fun search(query: String): List<VaultEntry.MarkdownFile> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return emptyList()
        val start = root() ?: return emptyList()
        return start.walkTopDown()
            .onEnter { !it.name.startsWith(".") }
            .filter { it.isFile && it.extension.equals("md", ignoreCase = true) }
            .filter { it.name.lowercase().contains(needle) }
            .sortedBy { it.name.lowercase() }
            .map(VaultEntry::MarkdownFile)
            .toList()
    }
}
