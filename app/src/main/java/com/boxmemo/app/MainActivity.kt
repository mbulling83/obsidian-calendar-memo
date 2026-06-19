package com.boxmemo.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.boxmemo.app.calendar.CalendarScreen
import com.boxmemo.app.calendar.DayViewModel
import com.boxmemo.app.gcal.NoOpGoogleCalendarRepository
import com.boxmemo.app.memo.PenSettingsStore
import com.boxmemo.app.memo.StrokeStore
import com.boxmemo.app.quickadd.QuickAddForm
import com.boxmemo.app.settings.HwrSettingsStore
import com.boxmemo.app.settings.SettingsScreen
import com.boxmemo.app.settings.VaultPermission
import com.boxmemo.app.settings.VaultSettingsStore
import com.boxmemo.app.settings.launchAllFilesAccessSettings
import com.boxmemo.app.ui.AppTopBar
import com.boxmemo.app.ui.BoxMemoTypography
import com.boxmemo.app.memo.PenSettings
import com.boxmemo.app.vault.DailyNoteRepository
import com.boxmemo.app.vault.VaultFileIndex
import com.boxmemo.app.vault.VaultFileRepository
import com.boxmemo.app.vault.VaultSettings
import com.boxmemo.app.vaultnotes.VaultNotesScreen
import com.boxmemo.app.widget.AgendaWidgetProvider

private enum class Screen { CALENDAR, SETTINGS, VAULT_NOTES }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val store = VaultSettingsStore(applicationContext)
        val penSettingsStore = PenSettingsStore(applicationContext)
        val hwrSettingsStore = HwrSettingsStore(applicationContext)

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
                    val vaultFileRepository = remember { VaultFileRepository() }
                    val vaultFileIndex = remember(vaultRoot) { VaultFileIndex(vaultRoot) }
                    val strokeStore = remember { StrokeStore() }
                    val penSettings by penSettingsStore.settings.collectAsState(initial = PenSettings())

                    var screen by remember { mutableStateOf(Screen.CALENDAR) }
                    var showAdd by remember { mutableStateOf(false) }

                    // Surface quick-add warnings (e.g. the day's note doesn't
                    // exist yet) rather than letting the add fail silently.
                    val context = LocalContext.current
                    val message by viewModel.message.collectAsState()
                    LaunchedEffect(message) {
                        message?.let {
                            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
                            viewModel.messageShown()
                        }
                    }

                    when (screen) {
                        Screen.CALENDAR -> {
                            Column(modifier = Modifier.fillMaxSize()) {
                                AppTopBar(
                                    onSettingsClick = { screen = Screen.SETTINGS },
                                    onAddClick = { showAdd = true },
                                    onTodayClick = { viewModel.selectDate(java.time.LocalDate.now()) },
                                    onVaultNotesClick = { screen = Screen.VAULT_NOTES },
                                )
                                CalendarScreen(
                                    viewModel = viewModel,
                                    dailyNoteRepository = dailyNoteRepository,
                                    strokeStore = strokeStore,
                                    penSettingsStore = penSettingsStore,
                                )
                            }
                        }
                        Screen.SETTINGS -> {
                            SettingsScreen(
                                store = store,
                                penSettingsStore = penSettingsStore,
                                hwrSettingsStore = hwrSettingsStore,
                                onBack = { screen = Screen.CALENDAR },
                                onRequestAllFilesAccess = { launchAllFilesAccessSettings(this@MainActivity) },
                                hasAllFilesAccess = { VaultPermission.hasAllFilesAccess() },
                            )
                        }
                        Screen.VAULT_NOTES -> {
                            VaultNotesScreen(
                                fileIndex = vaultFileIndex,
                                fileRepository = vaultFileRepository,
                                strokeStore = strokeStore,
                                penSettings = penSettings,
                                onBack = { screen = Screen.CALENDAR },
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

    override fun onPause() {
        super.onPause()
        // Reflect any edits made in-app on the home-screen agenda widget.
        AgendaWidgetProvider.refresh(applicationContext)
    }
}
