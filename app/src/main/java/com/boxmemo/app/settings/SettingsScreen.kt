package com.boxmemo.app.settings

import android.content.Context
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.boxmemo.app.hwr.HwrEngineType
import com.boxmemo.app.hwr.MlKitHWREngine
import com.boxmemo.app.memo.PenSettingsStore
import com.boxmemo.app.memo.PenType
import com.boxmemo.app.vault.VaultSettings
import kotlinx.coroutines.launch

/**
 * Full settings page (not a modal — settings here are infrequent but
 * deserve room to breathe): vault root path and pen type/thickness.
 */
@Composable
fun SettingsScreen(
    store: VaultSettingsStore,
    penSettingsStore: PenSettingsStore,
    hwrSettingsStore: HwrSettingsStore,
    onBack: () -> Unit,
    onRequestAllFilesAccess: () -> Unit,
    hasAllFilesAccess: () -> Boolean,
    onShowOnboarding: () -> Unit = {},
    onCheckVault: () -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Settings", style = MaterialTheme.typography.titleLarge)
        }
        HorizontalDivider()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            VaultSection(store, onRequestAllFilesAccess, hasAllFilesAccess, onCheckVault)
            HorizontalDivider()
            PenSection(penSettingsStore)
            HorizontalDivider()
            HwrSection(hwrSettingsStore)
            HorizontalDivider()
            HelpSection(onShowOnboarding)
        }
    }
}

@Composable
private fun HelpSection(onShowOnboarding: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Help", style = MaterialTheme.typography.titleMedium)
        Text(
            "Replay the welcome tour — a refresher on setup and the app's features.",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedButton(onClick = onShowOnboarding) { Text("Show welcome tour") }
    }
}

@Composable
private fun HwrSection(hwrSettingsStore: HwrSettingsStore) {
    val engine by hwrSettingsStore.engine.collectAsState(initial = HwrEngineType.ONYX)
    val scope = rememberCoroutineScope()
    var modelStatus by remember { mutableStateOf<String?>(null) }
    var checkingModel by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Handwriting recognition", style = MaterialTheme.typography.titleMedium)
        Text(
            "Engine used by Convert. Switch to compare quality on the same handwriting.",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HwrEngineType.entries.forEach { type ->
                FilterChip(
                    selected = engine == type,
                    onClick = { scope.launch { hwrSettingsStore.setEngine(type) } },
                    label = { Text(type.label) },
                )
            }
        }
        if (engine == HwrEngineType.ML_KIT) {
            Text(
                "ML Kit downloads a ~20 MB English model once (needs network), then works offline.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(
                enabled = !checkingModel,
                onClick = {
                    scope.launch {
                        checkingModel = true
                        modelStatus = "Downloading model…"
                        modelStatus = if (MlKitHWREngine.ensureReady()) {
                            "Model ready — ML Kit will work offline."
                        } else {
                            "Couldn't prepare the model — check your network connection and try again."
                        }
                        checkingModel = false
                    }
                },
            ) { Text("Download / verify ML Kit model") }
            modelStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun VaultSection(
    store: VaultSettingsStore,
    onRequestAllFilesAccess: () -> Unit,
    hasAllFilesAccess: () -> Boolean,
    onCheckVault: () -> Unit,
) {
    val savedVaultRoot by store.vaultRoot.collectAsState(initial = null)
    var vaultRootInput by remember(savedVaultRoot) { mutableStateOf(savedVaultRoot.orEmpty()) }
    val scope = rememberCoroutineScope()

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { treeUri ->
        if (treeUri != null) {
            resolveAbsolutePathFromTreeUri(treeUri)?.let { resolvedPath -> vaultRootInput = resolvedPath }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Vault root path", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = vaultRootInput,
            onValueChange = { vaultRootInput = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("/storage/emulated/0/Documents/MyVault") },
            singleLine = true,
        )
        OutlinedButton(onClick = { folderPickerLauncher.launch(null) }) { Text("Browse for folder") }
        Button(onClick = { scope.launch { store.setVaultRoot(vaultRootInput) } }) { Text("Save vault path") }

        Text(if (hasAllFilesAccess()) "All-files access granted" else "All-files access not granted")
        Button(onClick = onRequestAllFilesAccess) { Text("Grant all-files access") }

        OutlinedButton(onClick = onCheckVault) { Text("Check vault setup") }
    }

    Spacer(Modifier.height(8.dp))
    DailyNoteStructureSection(store)
    Spacer(Modifier.height(8.dp))
    SectionHeadingsSection(store)
}

/**
 * Lets the user set the daily-note folder template. The tokens {year},
 * {monthFolder} and {isoDate} are substituted per date — e.g.
 * "Periodic Notes/Daily Notes/2026/06 - June/2026-06-23.md".
 */
@Composable
private fun DailyNoteStructureSection(store: VaultSettingsStore) {
    val savedTemplate by store.dailyNoteTemplate.collectAsState(initial = VaultSettings.DEFAULT_TEMPLATE)
    var templateInput by remember(savedTemplate) { mutableStateOf(savedTemplate) }
    var savedMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Daily note folder structure", style = MaterialTheme.typography.titleMedium)
        Text(
            "Where your daily notes live, relative to the vault root. Use {year}, " +
                "{monthFolder} (e.g. \"06 - June\") and {isoDate} (e.g. \"2026-06-23\") as tokens. " +
                "Not sure? Use \"Check vault setup\" above to detect it.",
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
 * Lets the user name the daily-note sections that hold their meetings and
 * notes. Matching is forgiving — heading level (`#` vs `##`), surrounding
 * whitespace, and case are all ignored — so the exact text here just needs to
 * carry the same words (and any emoji) as the heading in their note.
 */
@Composable
private fun SectionHeadingsSection(store: VaultSettingsStore) {
    val savedMeetings by store.meetingsHeading.collectAsState(initial = VaultSettings.DEFAULT_MEETINGS_HEADING)
    val savedNotes by store.notesHeading.collectAsState(initial = VaultSettings.DEFAULT_NOTES_HEADING)
    var meetingsInput by remember(savedMeetings) { mutableStateOf(savedMeetings) }
    var notesInput by remember(savedNotes) { mutableStateOf(savedNotes) }
    var savedMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Daily-note sections", style = MaterialTheme.typography.titleMedium)
        Text(
            "Which heading in your daily note holds meetings, and which holds notes. " +
                "The number of #'s, spacing, and capitalisation don't matter — just match the words " +
                "(and any emoji) you use.",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
            value = meetingsInput,
            onValueChange = { meetingsInput = it; savedMessage = null },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Meetings heading") },
            placeholder = { Text(VaultSettings.DEFAULT_MEETINGS_HEADING) },
            singleLine = true,
        )
        OutlinedTextField(
            value = notesInput,
            onValueChange = { notesInput = it; savedMessage = null },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Notes heading") },
            placeholder = { Text(VaultSettings.DEFAULT_NOTES_HEADING) },
            singleLine = true,
        )
        Button(
            onClick = {
                val meetings = meetingsInput.trim()
                val notes = notesInput.trim()
                savedMessage = if (meetings.isBlank() || notes.isBlank()) {
                    "Both headings need some text."
                } else {
                    scope.launch {
                        store.setMeetingsHeading(meetings)
                        store.setNotesHeading(notes)
                    }
                    "✓ Saved."
                }
            },
        ) { Text("Save section headings") }
        savedMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun PenSection(penSettingsStore: PenSettingsStore) {
    val penSettings by penSettingsStore.settings.collectAsState(initial = com.boxmemo.app.memo.PenSettings())
    val scope = rememberCoroutineScope()
    var sliderValue by remember(penSettings.strokeWidth) { mutableFloatStateOf(penSettings.strokeWidth) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Pen", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PenType.entries.forEach { type ->
                FilterChip(
                    selected = penSettings.penType == type,
                    onClick = { scope.launch { penSettingsStore.setPenType(type) } },
                    label = { Text(type.name.lowercase().replaceFirstChar { it.uppercase() }) },
                )
            }
        }
        Text("Thickness: ${sliderValue.toInt()}")
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = { scope.launch { penSettingsStore.setStrokeWidth(sliderValue) } },
            valueRange = 1f..16f,
        )
    }
}

fun launchAllFilesAccessSettings(context: Context) {
    context.startActivity(VaultPermission.buildManageAllFilesIntent(context))
}
