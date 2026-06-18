package com.boxmemo.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.boxmemo.app.settings.SettingsScreen
import com.boxmemo.app.settings.VaultPermission
import com.boxmemo.app.settings.VaultSettingsStore
import com.boxmemo.app.settings.launchAllFilesAccessSettings

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val store = VaultSettingsStore(applicationContext)

        setContent {
            MaterialTheme {
                Surface {
                    SettingsScreen(
                        store = store,
                        onRequestAllFilesAccess = { launchAllFilesAccessSettings(this) },
                        hasAllFilesAccess = { VaultPermission.hasAllFilesAccess() },
                    )
                }
            }
        }
    }
}
