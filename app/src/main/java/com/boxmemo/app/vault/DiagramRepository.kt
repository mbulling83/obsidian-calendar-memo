package com.boxmemo.app.vault

import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate

/**
 * Outcome of saving a diagram PNG, mirroring [NoteWriteOutcome] so the UI can
 * tell the user why a save didn't happen rather than failing silently.
 */
sealed interface DiagramSaveOutcome {
    /** Saved successfully; [fileName] is the bare PNG filename for the `![[...]]` embed. */
    data class Saved(val fileName: String) : DiagramSaveOutcome
    object VaultNotConfigured : DiagramSaveOutcome
    object WriteFailed : DiagramSaveOutcome
}

/**
 * Single owner of diagram image I/O. Writes PNGs under
 * `attachments/Diagrams/<year>/W<week>` (see [diagramRelativeDir]) using the
 * same write-then-replace discipline as the note repositories so a
 * concurrently-running LiveSync watcher never observes a half-written image.
 */
class DiagramRepository(private val vaultRoot: String?) {

    /**
     * Renders [bitmap] to a uniquely-named PNG in [date]'s diagram folder,
     * creating the folder if needed. [baseName] is the date/time/name stem from
     * [meetingDiagramBaseName] / [notesDiagramBaseName] / [fileDiagramBaseName];
     * a collision suffix is added if a file of that name already exists.
     */
    fun saveDiagram(bitmap: Bitmap, date: LocalDate, baseName: String): DiagramSaveOutcome {
        val root = vaultRoot?.takeIf { it.isNotBlank() } ?: return DiagramSaveOutcome.VaultNotConfigured
        val dir = File(root, diagramRelativeDir(date))
        if (!dir.exists() && !dir.mkdirs()) return DiagramSaveOutcome.WriteFailed

        val existing = dir.list()?.toSet() ?: emptySet()
        val fileName = uniqueDiagramFileName(baseName, existing)
        val target = File(dir, fileName)
        val tempFile = File(dir, "$fileName.tmp")
        return try {
            FileOutputStream(tempFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            if (tempFile.renameTo(target)) {
                DiagramSaveOutcome.Saved(fileName)
            } else {
                tempFile.delete()
                DiagramSaveOutcome.WriteFailed
            }
        } catch (e: Exception) {
            tempFile.delete()
            DiagramSaveOutcome.WriteFailed
        }
    }
}
