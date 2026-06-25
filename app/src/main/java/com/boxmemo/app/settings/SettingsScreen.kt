package com.boxmemo.app.settings

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.boxmemo.app.hwr.HwrEngineType
import com.boxmemo.app.hwr.MlKitHWREngine
import com.boxmemo.app.memo.PenSettingsStore
import com.boxmemo.app.memo.PenType
import com.boxmemo.app.vault.VaultSettings
import kotlinx.coroutines.launch

/**
 * Full settings page. Grouped into bordered cards rather than a flat scroll of
 * divider-separated blocks: on e-ink, crisp black borders read far better than
 * faint dividers and shadows (which ghost), and clear grouping tames what had
 * become a long, busy list. One concern per card, generous spacing, large
 * stylus targets, instant state changes — no animation.
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
    val vaultRoot by store.vaultRoot.collectAsState(initial = null)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        HorizontalDivider(thickness = 2.dp)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SettingsCard("Vault") {
                VaultControls(store, onRequestAllFilesAccess, hasAllFilesAccess, onCheckVault)
            }
            SettingsCard("Where your daily notes live") {
                DailyNoteStructureControls(store)
            }
            SettingsCard("Daily note template") {
                DailyNoteTemplateControls(store, vaultRoot)
            }
            SettingsCard("Daily-note sections") {
                SectionHeadingsControls(store)
            }
            SettingsCard("Pen") {
                PenControls(penSettingsStore)
            }
            SettingsCard("Handwriting recognition") {
                HwrControls(hwrSettingsStore)
            }
            SettingsCard("Help") {
                HelpControls(onShowOnboarding)
            }
        }
    }
}

/**
 * A titled, bordered group. Uses [OutlinedCard] (border, no elevation/shadow)
 * with a deliberately heavy 2dp black border — decorative affordances need
 * thickening and darkening to register on e-ink.
 */
@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun HelpControls(onShowOnboarding: () -> Unit) {
    Text(
        "Replay the welcome tour — a refresher on setup and the app's features.",
        style = MaterialTheme.typography.bodySmall,
    )
    OutlinedButton(onClick = onShowOnboarding, modifier = Modifier.heightIn(min = 52.dp)) {
        Text("Show welcome tour")
    }
}

@Composable
private fun HwrControls(hwrSettingsStore: HwrSettingsStore) {
    val engine by hwrSettingsStore.engine.collectAsState(initial = HwrEngineType.ONYX)
    val scope = rememberCoroutineScope()
    var modelStatus by remember { mutableStateOf<String?>(null) }
    var checkingModel by remember { mutableStateOf(false) }

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
            modifier = Modifier.heightIn(min = 52.dp),
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

@Composable
private fun VaultControls(
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

    Text(
        "The top folder of your Obsidian vault, where your Markdown notes live.",
        style = MaterialTheme.typography.bodySmall,
    )
    OutlinedTextField(
        value = vaultRootInput,
        onValueChange = { vaultRootInput = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Vault root path") },
        placeholder = { Text("/storage/emulated/0/Documents/MyVault") },
        singleLine = true,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(
            onClick = { folderPickerLauncher.launch(null) },
            modifier = Modifier.heightIn(min = 52.dp),
        ) { Text("Browse for folder") }
        Button(
            onClick = { scope.launch { store.setVaultRoot(vaultRootInput) } },
            modifier = Modifier.heightIn(min = 52.dp),
        ) { Text("Save vault path") }
    }

    HorizontalDivider(thickness = 2.dp, modifier = Modifier.padding(vertical = 4.dp))

    Text(
        if (hasAllFilesAccess()) "✓ All-files access granted" else "All-files access not granted",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Bold,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = onRequestAllFilesAccess,
            modifier = Modifier.heightIn(min = 52.dp),
        ) { Text("Grant all-files access") }
        OutlinedButton(
            onClick = onCheckVault,
            modifier = Modifier.heightIn(min = 52.dp),
        ) { Text("Check vault setup") }
    }
}

/**
 * Lets the user name the daily-note sections that hold their meetings and
 * notes. Matching is forgiving — heading level (`#` vs `##`), surrounding
 * whitespace, and case are all ignored — so the exact text here just needs to
 * carry the same words (and any emoji) as the heading in their note.
 */
@Composable
private fun SectionHeadingsControls(store: VaultSettingsStore) {
    val savedMeetings by store.meetingsHeading.collectAsState(initial = VaultSettings.DEFAULT_MEETINGS_HEADING)
    val savedNotes by store.notesHeading.collectAsState(initial = VaultSettings.DEFAULT_NOTES_HEADING)
    var meetingsInput by remember(savedMeetings) { mutableStateOf(savedMeetings) }
    var notesInput by remember(savedNotes) { mutableStateOf(savedNotes) }
    var savedMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

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
        modifier = Modifier.heightIn(min = 52.dp),
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

@Composable
private fun PenControls(penSettingsStore: PenSettingsStore) {
    val penSettings by penSettingsStore.settings.collectAsState(initial = com.boxmemo.app.memo.PenSettings())
    val scope = rememberCoroutineScope()
    var sliderValue by remember(penSettings.strokeWidth) { mutableFloatStateOf(penSettings.strokeWidth) }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PenType.entries.forEach { type ->
            FilterChip(
                selected = penSettings.penType == type,
                onClick = { scope.launch { penSettingsStore.setPenType(type) } },
                label = { Text(type.name.lowercase().replaceFirstChar { it.uppercase() }) },
            )
        }
    }
    Text("Thickness: ${sliderValue.toInt()}", style = MaterialTheme.typography.bodyMedium)
    Slider(
        value = sliderValue,
        onValueChange = { sliderValue = it },
        onValueChangeFinished = { scope.launch { penSettingsStore.setStrokeWidth(sliderValue) } },
        valueRange = 1f..16f,
    )
}

fun launchAllFilesAccessSettings(context: Context) {
    context.startActivity(VaultPermission.buildManageAllFilesIntent(context))
}
