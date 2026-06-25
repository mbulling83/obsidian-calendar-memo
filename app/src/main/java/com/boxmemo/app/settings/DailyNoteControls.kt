package com.boxmemo.app.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.boxmemo.app.vault.VaultSettings
import kotlinx.coroutines.launch

/**
 * Reusable settings controls for the daily-note folder structure and the
 * Templater template, shared by the Settings screen and the first-run
 * onboarding tour so the two never drift apart. Each control owns its own
 * persistence (Save buttons / immediate toggle writes); the host supplies the
 * surrounding heading and framing (a card in Settings, a step in onboarding).
 */

/**
 * Lets the user set the daily-note folder template. The tokens {year},
 * {monthFolder} and {isoDate} are substituted per date — e.g.
 * "Periodic Notes/Daily Notes/2026/06 - June/2026-06-23.md".
 */
@Composable
fun DailyNoteStructureControls(store: VaultSettingsStore, modifier: Modifier = Modifier) {
    val savedTemplate by store.dailyNoteTemplate.collectAsState(initial = VaultSettings.DEFAULT_TEMPLATE)
    var templateInput by remember(savedTemplate) { mutableStateOf(savedTemplate) }
    var savedMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Where your daily notes live, relative to the vault root. Use {year}, " +
                "{monthFolder} (e.g. \"06 - June\") and {isoDate} (e.g. \"2026-06-23\") as tokens. " +
                "Not sure? Use \"Check vault setup\" to detect it.",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
            value = templateInput,
            onValueChange = { templateInput = it; savedMessage = null },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Folder template") },
            placeholder = { Text(VaultSettings.DEFAULT_TEMPLATE) },
            singleLine = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    val trimmed = templateInput.trim()
                    savedMessage = if (!trimmed.contains("{isoDate}")) {
                        "The template must include {isoDate} so each day maps to its own file."
                    } else {
                        scope.launch { store.setDailyNoteTemplate(trimmed) }
                        "✓ Saved."
                    }
                },
            ) { Text("Save folder template") }
            OutlinedButton(
                onClick = {
                    templateInput = VaultSettings.DEFAULT_TEMPLATE
                    scope.launch { store.setDailyNoteTemplate(VaultSettings.DEFAULT_TEMPLATE) }
                    savedMessage = "✓ Reset to default."
                },
            ) { Text("Reset") }
        }
        savedMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}

/**
 * Lets the user choose the Templater template used to fill a freshly-created
 * daily note, and whether to auto-create missing notes. We can't run Templater
 * itself (that's Obsidian's JS runtime), so the app reads this template and
 * natively renders the common `<% tp.date… %>` / `<% tp.file.title %>` tags,
 * stripping anything dynamic. The path is stored relative to the vault root
 * when the picked file lives inside the vault, otherwise absolute.
 */
@Composable
fun DailyNoteTemplateControls(store: VaultSettingsStore, vaultRoot: String?, modifier: Modifier = Modifier) {
    val savedPath by store.dailyNoteTemplatePath.collectAsState(initial = null)
    var pathInput by remember(savedPath) { mutableStateOf(savedPath.orEmpty()) }
    var savedMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun toStoredPath(absolute: String?): String? {
        val abs = absolute ?: return null
        val root = vaultRoot?.takeIf { it.isNotBlank() }
        return if (root != null && abs.startsWith("$root/")) abs.removePrefix("$root/") else abs
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            toStoredPath(resolveAbsolutePathFromDocumentUri(uri))?.let { picked ->
                pathInput = picked
                // Auto-save on pick so a browsed path can't be lost (matches the
                // vault step). Manual text edits still use the Save button.
                scope.launch { store.setDailyNoteTemplatePath(picked) }
                savedMessage = "✓ Saved."
            }
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "When you create a missing daily note, the app fills it from this Templater " +
                "template. Date tags (e.g. <% tp.date.now(\"YYYY-MM-DD\") %>) and <% tp.file.title %> " +
                "are rendered for the note's date; dynamic Templater features (prompts, user scripts) " +
                "can't run on-device and are removed. Leave blank to just create the section headings.",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
            value = pathInput,
            onValueChange = { pathInput = it; savedMessage = null },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Template file") },
            placeholder = { Text("Templates/Daily Note.md") },
            singleLine = true,
        )
        OutlinedButton(onClick = { filePickerLauncher.launch(arrayOf("text/*", "text/markdown", "*/*")) }) {
            Text("Browse for template file")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    scope.launch { store.setDailyNoteTemplatePath(pathInput) }
                    savedMessage =
                        if (pathInput.isBlank()) "✓ Cleared — new notes use the section headings." else "✓ Saved."
                },
            ) { Text("Save template") }
            OutlinedButton(
                onClick = {
                    pathInput = ""
                    scope.launch { store.setDailyNoteTemplatePath("") }
                    savedMessage = "✓ Cleared — new notes use the section headings."
                },
            ) { Text("Clear") }
        }
        savedMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

        val autoCreate by store.autoCreateMissingNotes.collectAsState(initial = false)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Auto-create missing notes", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "When you add a meeting, note or handwriting to a day with no note yet, " +
                        "create it from the template automatically. Off: you'll be told the note " +
                        "doesn't exist and can use \"Create note\" yourself.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(
                checked = autoCreate,
                onCheckedChange = { scope.launch { store.setAutoCreateMissingNotes(it) } },
            )
        }
    }
}
