package com.boxmemo.app.settings

import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract

/**
 * Converts a tree URI returned by ACTION_OPEN_DOCUMENT_TREE into an absolute
 * filesystem path, so the picked folder can be used directly as the vault
 * root for our direct-File-access approach (see plan U1 — we deliberately
 * use MANAGE_EXTERNAL_STORAGE + File I/O, not SAF, for ongoing vault access;
 * the picker is only a convenience for *selecting* that path).
 *
 * Reliable for the primary storage volume ("primary:..."); falls back to a
 * best-effort `/storage/<volumeId>/...` guess for other volumes (e.g. an SD
 * card), which may not resolve on every device.
 */
fun resolveAbsolutePathFromTreeUri(treeUri: Uri): String? {
    val documentId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return null
    val parts = documentId.split(":", limit = 2)
    if (parts.size != 2) return null
    val (volumeId, relativePath) = parts

    val volumeRoot = if (volumeId == "primary") {
        Environment.getExternalStorageDirectory().path
    } else {
        "/storage/$volumeId"
    }

    return if (relativePath.isBlank()) volumeRoot else "$volumeRoot/$relativePath"
}

/**
 * Converts a document URI returned by ACTION_OPEN_DOCUMENT (a single picked
 * file) into an absolute filesystem path, so a chosen Templater template file
 * can be read directly via File I/O. Same caveats as
 * [resolveAbsolutePathFromTreeUri]: reliable on the primary volume, best-effort
 * elsewhere.
 */
fun resolveAbsolutePathFromDocumentUri(documentUri: Uri): String? {
    val documentId = runCatching { DocumentsContract.getDocumentId(documentUri) }.getOrNull() ?: return null
    // The Downloads provider hands back "raw:<absolute path>" for files it
    // stores directly on disk — the embedded path is already what we want.
    if (documentId.startsWith("raw:")) {
        return documentId.removePrefix("raw:").takeIf { it.startsWith("/") }
    }
    val parts = documentId.split(":", limit = 2)
    if (parts.size != 2) return null
    val (volumeId, relativePath) = parts

    val volumeRoot = if (volumeId == "primary") {
        Environment.getExternalStorageDirectory().path
    } else {
        "/storage/$volumeId"
    }

    return if (relativePath.isBlank()) volumeRoot else "$volumeRoot/$relativePath"
}
