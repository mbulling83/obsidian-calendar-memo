package com.boxmemo.app.vaultcheck

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.boxmemo.app.settings.resolveAbsolutePathFromTreeUri
import com.boxmemo.app.vault.VaultDiagnosis

/**
 * Diagnoses why no meetings are showing and offers one-tap fixes. Reachable
 * from the Calendar banner and from Settings. E-ink friendly: plain text,
 * large buttons, instant state changes, no animation.
 */
@Composable
fun VaultCheckScreen(
    diagnosis: VaultDiagnosis?,
    onApplyMeetingsHeading: (String) -> Unit,
    onApplyNotesHeading: (String) -> Unit,
    onApplyTemplate: (String) -> Unit,
    onApplyVaultRoot: (String) -> Unit,
    onRescan: () -> Unit,
    onBack: () -> Unit,
) {
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { treeUri ->
        if (treeUri != null) {
            resolveAbsolutePathFromTreeUri(treeUri)?.let { onApplyVaultRoot(it) }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
            Text("Check vault setup", style = MaterialTheme.typography.titleLarge)
        }
        HorizontalDivider(thickness = 2.dp)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (diagnosis) {
                null -> Heading("Checking your recent notes…")

                VaultDiagnosis.NotConfigured -> {
                    Heading("No vault configured")
                    Body("Set your vault folder first, then run this check again.")
                    Button(onClick = { folderPickerLauncher.launch(null) }) { Text("Pick vault folder") }
                }

                is VaultDiagnosis.Healthy -> {
                    Heading("✓ Looks healthy")
                    Body(
                        "Found the meetings section in ${diagnosis.notesWithMeetings} of " +
                            "${diagnosis.notesSampled} recent daily notes. If a specific day looks " +
                            "empty, it just has no meetings logged.",
                    )
                }

                is VaultDiagnosis.HeadingMismatch -> {
                    Heading("Meetings section not recognised")
                    Body(
                        "Checked ${diagnosis.notesSampled} recent notes, but none use the meetings " +
                            "heading you've configured — so no meetings can show.",
                    )
                    diagnosis.recommendedMeetingsHeading?.let { rec ->
                        Recommendation(
                            "Your meeting times appear under \"$rec\". Use that as the meetings heading?",
                            "Use \"$rec\" for meetings",
                        ) { onApplyMeetingsHeading(rec) }
                    }
                    diagnosis.recommendedNotesHeading?.let { rec ->
                        Recommendation(
                            "\"$rec\" looks like your notes section. Use it for notes?",
                            "Use \"$rec\" for notes",
                        ) { onApplyNotesHeading(rec) }
                    }
                    if (diagnosis.recommendedMeetingsHeading == null) {
                        Body(
                            "Couldn't spot any HH:MM – HH:MM meeting lines automatically. " +
                                "Headings seen in your notes: " +
                                diagnosis.headingsSeen.joinToString(", ").ifEmpty { "(none)" } +
                                ". You can set the heading manually in Settings.",
                        )
                    }
                }

                is VaultDiagnosis.NoNotesFound -> {
                    Heading("No daily notes found")
                    Body(
                        "Looked back ${diagnosis.daysChecked} days but found no note files with the " +
                            "current folder pattern. The vault folder or the Periodic Notes structure " +
                            "is probably off.",
                    )
                    diagnosis.recommendedTemplate?.let { template ->
                        Recommendation(
                            "Found a daily note at \"${diagnosis.foundExampleRelPath}\". " +
                                "Use this folder pattern?\n$template",
                            "Use detected folder pattern",
                        ) { onApplyTemplate(template) }
                    }
                    Body("Or pick the correct vault folder:")
                    OutlinedButton(onClick = { folderPickerLauncher.launch(null) }) {
                        Text("Re-pick vault folder")
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            OutlinedButton(onClick = onRescan) { Text("Re-scan") }
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(text, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
}

@Composable
private fun Body(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun Recommendation(explanation: String, action: String, onApply: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider()
        Text(explanation, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        Button(onClick = onApply) { Text(action) }
    }
}
