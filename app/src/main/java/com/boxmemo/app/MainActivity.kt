package com.boxmemo.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.boxmemo.app.calendar.CalendarScreen
import com.boxmemo.app.calendar.DayViewModel
import com.boxmemo.app.gcal.NoOpGoogleCalendarRepository
import com.boxmemo.app.hwr.RecognitionMethodPreference
import com.boxmemo.app.memo.PenSettingsStore
import com.boxmemo.app.memo.StrokeStore
import com.boxmemo.app.quickadd.QuickAddForm
import com.boxmemo.app.settings.SettingsScreen
import com.boxmemo.app.settings.VaultPermission
import com.boxmemo.app.settings.VaultSettingsStore
import com.boxmemo.app.settings.launchAllFilesAccessSettings
import com.boxmemo.app.ui.AppTopBar
import com.boxmemo.app.ui.BoxMemoTypography
import com.boxmemo.app.vault.DailyNoteRepository
import com.boxmemo.app.vault.VaultSettings

private enum class Screen { CALENDAR, SETTINGS }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val store = VaultSettingsStore(applicationContext)
        val penSettingsStore = PenSettingsStore(applicationContext)
        val recognitionMethodPreference = RecognitionMethodPreference(applicationContext)

        setContent {
            MaterialTheme(typography = BoxMemoTypography) {
                Surface {
                    val vaultRoot by store.vaultRoot.collectAsState(initial = null)
                    // U3 (Google Calendar) is deferred; the no-op repository
                    // keeps the merged day view working with Obsidian-only
                    // data until OAuth is wired in.
                    val dailyNoteRepository = remember(vaultRoot) {
                        DailyNoteRepository(VaultSettings(vaultRoot))
                    }
                    val viewModel = remember(dailyNoteRepository) {
                        DayViewModel(dailyNoteRepository, NoOpGoogleCalendarRepository)
                    }
                    val strokeStore = remember { StrokeStore() }

                    var screen by remember { mutableStateOf(Screen.CALENDAR) }
                    var showAdd by remember { mutableStateOf(false) }

                    when (screen) {
                        Screen.CALENDAR -> {
                            Column(modifier = Modifier.fillMaxSize()) {
                                AppTopBar(
                                    onSettingsClick = { screen = Screen.SETTINGS },
                                    onAddClick = { showAdd = true },
                                )
                                CalendarScreen(
                                    viewModel = viewModel,
                                    dailyNoteRepository = dailyNoteRepository,
                                    strokeStore = strokeStore,
                                    penSettingsStore = penSettingsStore,
                                    recognitionMethodPreference = recognitionMethodPreference,
                                )
                            }
                        }
                        Screen.SETTINGS -> {
                            SettingsScreen(
                                store = store,
                                penSettingsStore = penSettingsStore,
                                recognitionMethodPreference = recognitionMethodPreference,
                                onBack = { screen = Screen.CALENDAR },
                                onRequestAllFilesAccess = { launchAllFilesAccessSettings(this@MainActivity) },
                                hasAllFilesAccess = { VaultPermission.hasAllFilesAccess() },
                            )
                        }
                    }

                    if (showAdd) {
                        AlertDialog(
                            onDismissRequest = { showAdd = false },
                            confirmButton = {},
                            text = {
                                QuickAddForm(
                                    onAddMeeting = { startTime, endTime, title ->
                                        viewModel.addMeeting(startTime, endTime, title)
                                    },
                                    onAddNote = { text -> viewModel.addNote(text) },
                                    onDone = { showAdd = false },
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}
