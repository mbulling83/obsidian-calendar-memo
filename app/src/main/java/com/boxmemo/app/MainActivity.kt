package com.boxmemo.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.boxmemo.app.calendar.CalendarScreen
import com.boxmemo.app.gcal.NoOpGoogleCalendarRepository
import com.boxmemo.app.settings.SettingsScreen
import com.boxmemo.app.settings.VaultPermission
import com.boxmemo.app.settings.VaultSettingsStore
import com.boxmemo.app.settings.launchAllFilesAccessSettings
import com.boxmemo.app.vault.DailyNoteRepository
import com.boxmemo.app.vault.VaultSettings

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val store = VaultSettingsStore(applicationContext)

        setContent {
            MaterialTheme {
                Surface {
                    var selectedTab by remember { mutableIntStateOf(0) }
                    val vaultRoot by store.vaultRoot.collectAsState(initial = null)
                    // U3 (Google Calendar) is deferred; the no-op repository
                    // keeps the merged day view working with Obsidian-only
                    // data until OAuth is wired in.
                    val dailyNoteRepository = remember(vaultRoot) {
                        DailyNoteRepository(VaultSettings(vaultRoot))
                    }

                    Column(modifier = Modifier.fillMaxSize()) {
                        TabRow(selectedTabIndex = selectedTab) {
                            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { androidx.compose.material3.Text("Calendar") })
                            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { androidx.compose.material3.Text("Settings") })
                        }

                        when (selectedTab) {
                            0 -> CalendarScreen(
                                dailyNoteRepository = dailyNoteRepository,
                                googleCalendarRepository = NoOpGoogleCalendarRepository,
                            )
                            else -> SettingsScreen(
                                store = store,
                                onRequestAllFilesAccess = { launchAllFilesAccessSettings(this@MainActivity) },
                                hasAllFilesAccess = { VaultPermission.hasAllFilesAccess() },
                            )
                        }
                    }
                }
            }
        }
    }
}
