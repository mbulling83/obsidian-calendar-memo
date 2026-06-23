package com.boxmemo.app.vaultcheck

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.boxmemo.app.vault.VaultDiagnosis

/**
 * A black-bordered banner shown on the Calendar when a scan concludes meetings
 * can't be read — pointing the user (or a friend) at the diagnosis instead of
 * silently showing empty days. Only renders for problem diagnoses.
 */
@Composable
fun VaultHealthBanner(
    diagnosis: VaultDiagnosis?,
    onDiagnose: () -> Unit,
    onDismiss: () -> Unit,
) {
    val message = when (diagnosis) {
        is VaultDiagnosis.HeadingMismatch ->
            "No meetings found in your recent notes. The meetings-section heading may not match your vault."
        is VaultDiagnosis.NoNotesFound ->
            "No daily notes found. Your vault folder or the Periodic Notes structure may be set incorrectly."
        else -> return // Healthy / NotConfigured / loading → no banner
    }

    Surface(
        tonalElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(message, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(onClick = onDiagnose) { Text("Diagnose & fix") }
                TextButton(onClick = onDismiss) { Text("Dismiss") }
            }
        }
    }
}
