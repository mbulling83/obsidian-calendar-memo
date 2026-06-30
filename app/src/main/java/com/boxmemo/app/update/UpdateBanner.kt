package com.boxmemo.app.update

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * First meaningful line of a GitHub release body, with light Markdown stripped
 * (`**bold**`, `*`, backticks, leading `#`/`-`/`>`) so the banner shows clean
 * prose rather than raw syntax. Null if the body has no prose line.
 */
private fun releaseTeaser(notes: String): String? =
    notes.lineSequence()
        .map { it.trim() }
        .firstOrNull { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("---") }
        ?.replace("**", "")
        ?.replace("`", "")
        ?.trimStart('#', '-', '>', '*', ' ')
        ?.takeIf { it.isNotBlank() }

/**
 * Black-bordered Calendar banner offering a one-tap update when a newer release
 * is found on GitHub. Matches VaultHealthBanner's flat, border-not-shadow e-ink
 * styling (shadows ghost). No animation; states swap instantly.
 */
@Composable
fun UpdateBanner(
    state: UpdateUiState,
    onUpdate: (LatestRelease) -> Unit,
    onDismiss: () -> Unit,
) {
    val release = when (state) {
        is UpdateUiState.Available -> state.release
        is UpdateUiState.Downloading -> state.release
        is UpdateUiState.Failed -> state.release
        UpdateUiState.Hidden -> return
    }

    Surface(
        tonalElevation = 0.dp,
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Update available — v${release.version}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
            )
            val teaser = releaseTeaser(release.notes)
            if (teaser != null) {
                Text(
                    teaser,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (state is UpdateUiState.Failed) {
                Text(
                    state.message,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when (state) {
                    is UpdateUiState.Downloading ->
                        Text("Downloading…", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    is UpdateUiState.Failed -> {
                        Button(onClick = { onUpdate(release) }) { Text("Retry") }
                        TextButton(onClick = onDismiss) { Text("Later") }
                    }
                    else -> {
                        Button(onClick = { onUpdate(release) }) { Text("Download & install") }
                        TextButton(onClick = onDismiss) { Text("Later") }
                    }
                }
            }
        }
    }
}
