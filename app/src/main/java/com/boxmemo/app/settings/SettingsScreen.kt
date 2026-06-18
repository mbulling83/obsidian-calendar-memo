package com.boxmemo.app.settings

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Lets the user configure the vault root path and grant all-files access.
 * See plan U1: vault file access is direct File I/O under
 * MANAGE_EXTERNAL_STORAGE, not Storage Access Framework.
 */
@Composable
fun SettingsScreen(
    store: VaultSettingsStore,
    onRequestAllFilesAccess: () -> Unit,
    hasAllFilesAccess: () -> Boolean,
) {
    val savedVaultRoot by store.vaultRoot.collectAsState(initial = null)
    var vaultRootInput by remember(savedVaultRoot) {
        mutableStateOf(savedVaultRoot.orEmpty())
    }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Vault root path")
        OutlinedTextField(
            value = vaultRootInput,
            onValueChange = { vaultRootInput = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("/storage/emulated/0/lepus albus") },
        )
        Button(onClick = { scope.launch { store.setVaultRoot(vaultRootInput) } }) {
            Text("Save vault path")
        }

        Text(if (hasAllFilesAccess()) "All-files access granted" else "All-files access not granted")
        Button(onClick = onRequestAllFilesAccess) {
            Text("Grant all-files access")
        }
    }
}

fun launchAllFilesAccessSettings(context: Context) {
    context.startActivity(VaultPermission.buildManageAllFilesIntent(context))
}
