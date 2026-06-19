package com.boxmemo.app.settings

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
            VaultSection(store, onRequestAllFilesAccess, hasAllFilesAccess)
            HorizontalDivider()
            PenSection(penSettingsStore)
            HorizontalDivider()
            HwrSection(hwrSettingsStore)
        }
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
            placeholder = { Text("/storage/emulated/0/lepus albus") },
            singleLine = true,
        )
        OutlinedButton(onClick = { folderPickerLauncher.launch(null) }) { Text("Browse for folder") }
        Button(onClick = { scope.launch { store.setVaultRoot(vaultRootInput) } }) { Text("Save vault path") }

        Text(if (hasAllFilesAccess()) "All-files access granted" else "All-files access not granted")
        Button(onClick = onRequestAllFilesAccess) { Text("Grant all-files access") }
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
