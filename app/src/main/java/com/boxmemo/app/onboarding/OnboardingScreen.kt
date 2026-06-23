package com.boxmemo.app.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.boxmemo.app.settings.OnboardingSettingsStore
import com.boxmemo.app.settings.VaultSettingsStore
import com.boxmemo.app.settings.resolveAbsolutePathFromTreeUri
import kotlinx.coroutines.launch
import java.io.File

/**
 * First-run welcome tour. Designed for a friend who just sideloaded the app
 * and has never configured a vault. It walks through the two things the app
 * can't work without — all-files access and the vault folder path — then
 * highlights what the app does, before handing off to the calendar.
 *
 * E-ink friendly: paged (no scroll-driven animation), one decision per page,
 * large black-on-white targets, instant page changes.
 */
@Composable
fun OnboardingScreen(
    onboardingStore: OnboardingSettingsStore,
    vaultSettingsStore: VaultSettingsStore,
    onRequestAllFilesAccess: () -> Unit,
    hasAllFilesAccess: () -> Boolean,
    onFinish: () -> Unit,
) {
    val steps = OnboardingStep.entries
    var stepIndex by remember { mutableStateOf(0) }
    val step = steps[stepIndex]
    val scope = rememberCoroutineScope()

    // Vault input is hoisted here (not held inside the step) so the path is
    // persisted the moment it's valid — on folder pick and when advancing —
    // rather than only on an explicit Save the user can skip past.
    val savedVaultRoot by vaultSettingsStore.vaultRoot.collectAsState(initial = null)
    var vaultInput by remember(savedVaultRoot) { mutableStateOf(savedVaultRoot.orEmpty()) }

    /** Persists [path] if it's a real directory; returns whether it was saved. */
    fun saveVaultIfValid(path: String): Boolean {
        val trimmed = path.trim()
        val valid = trimmed.isNotEmpty() && File(trimmed).isDirectory
        if (valid) scope.launch { vaultSettingsStore.setVaultRoot(trimmed) }
        return valid
    }

    val finish: () -> Unit = {
        scope.launch { onboardingStore.setOnboardingComplete(true) }
        onFinish()
    }

    Column(modifier = Modifier.fillMaxSize().padding(32.dp)) {
        Text(
            "Step ${stepIndex + 1} of ${steps.size}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(thickness = 2.dp)
        Spacer(Modifier.height(24.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            when (step) {
                OnboardingStep.WELCOME -> WelcomeStep()
                OnboardingStep.PERMISSION -> PermissionStep(
                    onRequestAllFilesAccess = onRequestAllFilesAccess,
                    hasAllFilesAccess = hasAllFilesAccess,
                )
                OnboardingStep.VAULT -> VaultStep(
                    vaultInput = vaultInput,
                    onVaultInputChange = { vaultInput = it },
                    saveVaultIfValid = { saveVaultIfValid(it) },
                )
                OnboardingStep.FEATURES -> FeaturesStep()
                OnboardingStep.DONE -> DoneStep()
            }
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider(thickness = 2.dp)
        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (stepIndex > 0) {
                OutlinedButton(onClick = { stepIndex-- }) { Text("Back") }
            } else {
                TextButton(onClick = finish) { Text("Skip") }
            }

            if (step == OnboardingStep.DONE) {
                Button(onClick = finish) { Text("Get started") }
            } else {
                Button(
                    onClick = {
                        // Don't lose a browsed/typed vault path just because the
                        // user advanced without tapping Save.
                        if (step == OnboardingStep.VAULT) saveVaultIfValid(vaultInput)
                        stepIndex++
                    },
                ) { Text("Next") }
            }
        }
    }
}

private enum class OnboardingStep { WELCOME, PERMISSION, VAULT, FEATURES, DONE }

@Composable
private fun StepHeading(title: String) {
    Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun StepBody(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge)
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun WelcomeStep() {
    StepHeading("Welcome to Calendar Memo")
    StepBody(
        "A handwriting-first companion for your Obsidian daily notes — built for " +
            "this e-ink tablet and its pen.",
    )
    StepBody(
        "It reads the meetings and notes from your Obsidian daily note, lets you " +
            "scribble with the pen, and writes your handwriting straight back into " +
            "the note as Markdown.",
    )
    StepBody("The next two steps connect the app to your vault. It takes about a minute.")
}

@Composable
private fun PermissionStep(
    onRequestAllFilesAccess: () -> Unit,
    hasAllFilesAccess: () -> Boolean,
) {
    // hasAllFilesAccess() reads live each recomposition; tapping the button
    // leaves the app, so the status refreshes when the user returns.
    val granted = hasAllFilesAccess()

    StepHeading("Allow access to your files")
    StepBody(
        "Your vault is a folder of Markdown files on this device. To read your " +
            "daily notes and save your handwriting back, the app needs permission " +
            "to access files.",
    )
    StepBody(
        "Tap the button below — it opens an Android settings page. Turn on " +
            "\"Allow access to manage all files\", then come back here.",
    )
    Spacer(Modifier.height(8.dp))
    Text(
        if (granted) "✓ File access granted" else "File access not granted yet",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(12.dp))
    Button(onClick = onRequestAllFilesAccess) {
        Text(if (granted) "Open file-access settings again" else "Grant file access")
    }
}

@Composable
private fun VaultStep(
    vaultInput: String,
    onVaultInputChange: (String) -> Unit,
    saveVaultIfValid: (String) -> Boolean,
) {
    // Status shown after an explicit action (pick/Save) — not while typing.
    var statusMessage by remember { mutableStateOf<String?>(null) }

    fun saveAndReport(path: String) {
        statusMessage = if (saveVaultIfValid(path)) {
            "✓ Saved. The calendar will read from this vault."
        } else if (path.isBlank()) {
            "Enter or browse to a folder first."
        } else {
            "Couldn't find that folder. Check file access is granted and the path is correct."
        }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { treeUri ->
        if (treeUri != null) {
            resolveAbsolutePathFromTreeUri(treeUri)?.let { resolved ->
                onVaultInputChange(resolved)
                // Auto-save on pick so the path can't be lost by skipping Save.
                saveAndReport(resolved)
            }
        }
    }

    StepHeading("Point to your vault")
    StepBody(
        "Choose the top folder of your Obsidian vault. \"Browse\" is easiest — " +
            "navigate to the vault folder and select it. Or type the full path if " +
            "you know it.",
    )
    OutlinedTextField(
        value = vaultInput,
        onValueChange = {
            onVaultInputChange(it)
            statusMessage = null
        },
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("/storage/emulated/0/Documents/MyVault") },
        singleLine = true,
        label = { Text("Vault folder path") },
    )
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = { folderPickerLauncher.launch(null) }) { Text("Browse") }
        Button(onClick = { saveAndReport(vaultInput) }) { Text("Save") }
    }
    statusMessage?.let {
        Spacer(Modifier.height(12.dp))
        Text(it, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
    Spacer(Modifier.height(16.dp))
    Text(
        "The app reads meetings and notes from sections in your daily note. If your " +
            "headings differ from the defaults (👥 Meetings and 📝 Notes), set the names " +
            "in Settings → Daily-note sections — it's forgiving about #'s and case.",
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun FeaturesStep() {
    StepHeading("What you can do")
    FeatureRow(
        "Calendar",
        "See your day's meetings from your Obsidian daily note. Tap a day to open " +
            "it; tap the calendar header to jump back to today.",
    )
    FeatureRow(
        "Handwrite into notes",
        "Open a meeting or the Notes section and write with the pen. \"Convert\" " +
            "turns your handwriting into Markdown bullets, saved straight into the note.",
    )
    FeatureRow(
        "Vault Notes",
        "Pick any Markdown file in your vault and handwrite bullets — or save your " +
            "scribble as a diagram image — into it at the line you tap.",
    )
    FeatureRow(
        "Scribble calendar",
        "A month grid you write over freely, like a paper wall planner. Saved on " +
            "this device, page through any month.",
    )
    FeatureRow(
        "Quick add",
        "Use the + button to add a meeting or note by typing, without the pen.",
    )
}

@Composable
private fun FeatureRow(title: String, body: String) {
    Column(modifier = Modifier.padding(bottom = 20.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DoneStep() {
    StepHeading("You're all set")
    StepBody("That's everything. You can change the vault path or pen any time in Settings.")
    StepBody("Tip: re-open this tour from Settings whenever you want a refresher.")
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(0.dp))
        Text(
            "Tap \"Get started\" to open your calendar.",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}
