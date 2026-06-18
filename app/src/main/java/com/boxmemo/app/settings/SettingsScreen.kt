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
import com.boxmemo.app.hwr.RecognitionMethod
import com.boxmemo.app.hwr.RecognitionMethodPreference
import com.boxmemo.app.memo.PenSettingsStore
import com.boxmemo.app.memo.PenType
import kotlinx.coroutines.launch

/**
 * Full settings page (not a modal — settings here are infrequent but
 * deserve room to breathe): vault root path, pen type/thickness, and
 * which recognition method conversion uses by default.
 */
@Composable
fun SettingsScreen(
    store: VaultSettingsStore,
    penSettingsStore: PenSettingsStore,
    recognitionMethodPreference: RecognitionMethodPreference,
    onBack: () -> Unit,
    onRequestAllFilesAccess: () -> Unit,
    hasAllFilesAccess: () -> Boolean,
) {
    val scope = rememberCoroutineScope()

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
            RecognitionMethodSection(recognitionMethodPreference, scope)
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

@Composable
private fun RecognitionMethodSection(preference: RecognitionMethodPreference, scope: kotlinx.coroutines.CoroutineScope) {
    val lastUsedMethod by preference.lastUsedMethod.collectAsState(initial = RecognitionMethod.ONYX_BUILT_IN)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Handwriting conversion method", style = MaterialTheme.typography.titleMedium)
        Column {
            RecognitionMethod.entries.forEach { method ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.RadioButton(
                        selected = lastUsedMethod == method,
                        onClick = { scope.launch { preference.setLastUsedMethod(method) } },
                    )
                    Text(methodLabel(method))
                }
            }
        }
    }
}

private fun methodLabel(method: RecognitionMethod): String = when (method) {
    RecognitionMethod.ONYX_BUILT_IN -> "Onyx built-in"
    RecognitionMethod.AI_VISION -> "AI vision (OpenRouter)"
    RecognitionMethod.ONYX_THEN_AI_ENHANCE -> "Onyx + AI enhance"
}

fun launchAllFilesAccessSettings(context: Context) {
    context.startActivity(VaultPermission.buildManageAllFilesIntent(context))
}
