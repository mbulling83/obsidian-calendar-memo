package com.boxmemo.app.vault

import java.io.File
import java.time.LocalDate

/**
 * Inspects the user's vault on disk and produces a [VaultDiagnosis]. Thin
 * Android/File glue around the pure [VaultDiagnostics] analysis: it samples
 * recent daily notes via [VaultSettings], and — if none resolve — walks the
 * vault for a date-named note to recommend a corrected folder template.
 */
class VaultScanner(private val vaultSettings: VaultSettings) {

    /**
     * @param today anchor for the look-back window (injected for testing).
     * @param lookBackDays how far back to look for existing notes.
     * @param maxSamples how many resolved notes to analyse.
     */
    fun scan(
        today: LocalDate = LocalDate.now(),
        lookBackDays: Int = 120,
        maxSamples: Int = 10,
    ): VaultDiagnosis {
        val root = vaultSettings.vaultRoot?.takeIf { it.isNotBlank() }
            ?: return VaultDiagnosis.NotConfigured

        val samples = mutableListOf<DailyNoteSample>()
        var day = today
        var checked = 0
        while (checked < lookBackDays && samples.size < maxSamples) {
            val file = vaultSettings.resolveDailyNotePath(day)
            if (file != null && file.isFile) {
                runCatching { file.readText() }.getOrNull()?.let { samples.add(DailyNoteSample(day, it)) }
            }
            day = day.minusDays(1)
            checked++
        }

        if (samples.isNotEmpty()) {
            return VaultDiagnostics.analyzeSamples(
                samples,
                vaultSettings.meetingsHeading,
                vaultSettings.notesHeading,
            )
        }

        // Nothing resolved with the current template — hunt for a dated note
        // anywhere under the vault and recommend a folder template from it.
        val (relPath, date) = findDatedNote(File(root)) ?: return VaultDiagnosis.NoNotesFound(
            daysChecked = lookBackDays,
            recommendedTemplate = null,
            foundExampleRelPath = null,
        )
        return VaultDiagnosis.NoNotesFound(
            daysChecked = lookBackDays,
            recommendedTemplate = VaultDiagnostics.inferTemplate(relPath, date),
            foundExampleRelPath = relPath,
        )
    }

    /** Returns the first date-named `.md` file under [root] as (relPath, date), bounded. */
    private fun findDatedNote(root: File, maxDepth: Int = 7, maxVisited: Int = 20_000): Pair<String, LocalDate>? {
        var visited = 0
        val stack = ArrayDeque<Pair<File, Int>>()
        stack.addLast(root to 0)
        while (stack.isNotEmpty()) {
            val (dir, depth) = stack.removeLast()
            val children = dir.listFiles() ?: continue
            for (child in children) {
                if (++visited > maxVisited) return null
                if (child.isFile) {
                    val date = VaultDiagnostics.dateFromFileName(child.name)
                    if (date != null) {
                        val rel = child.absolutePath.removePrefix(root.absolutePath).trimStart('/')
                        return rel to date
                    }
                } else if (child.isDirectory && depth < maxDepth && !child.name.startsWith(".")) {
                    stack.addLast(child to depth + 1)
                }
            }
        }
        return null
    }
}
