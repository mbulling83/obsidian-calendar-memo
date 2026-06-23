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
import com.boxmemo.app.onboarding.OnboardingScreen
import com.boxmemo.app.quickadd.QuickAddForm
import com.boxmemo.app.scribble.MonthScribbleScreen
import com.boxmemo.app.scribble.MonthScribbleStore
import com.boxmemo.app.settings.HwrSettingsStore
import com.boxmemo.app.settings.OnboardingSettingsStore
import com.boxmemo.app.settings.SettingsScreen
import com.boxmemo.app.settings.VaultPermission
import com.boxmemo.app.settings.VaultSettingsStore
import com.boxmemo.app.settings.launchAllFilesAccessSettings
import com.boxmemo.app.ui.AppTopBar
import com.boxmemo.app.ui.BoxMemoTypography
import com.boxmemo.app.memo.PenSettings
import com.boxmemo.app.vault.DailyNoteRepository
import com.boxmemo.app.vault.DiagramRepository
import com.boxmemo.app.vault.VaultFileIndex
import com.boxmemo.app.vault.VaultFileRepository
import com.boxmemo.app.vault.VaultSettings
import com.boxmemo.app.vaultnotes.VaultNotesScreen
import com.boxmemo.app.widget.AgendaWidgetProvider

private enum class Screen { CALENDAR, SETTINGS, VAULT_NOTES, MONTH_SCRIBBLE }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val store = VaultSettingsStore(applicationContext)
        val penSettingsStore = PenSettingsStore(applicationContext)
        val hwrSettingsStore = HwrSettingsStore(applicationContext)
        val onboardingStore = OnboardingSettingsStore(applicationContext)

        setContent {
            MaterialTheme(typography = BoxMemoTypography) {
                Surface {
                    val vaultRoot by store.vaultRoot.collectAsState(initial = null)
                    val meetingsHeading by store.meetingsHeading
                        .collectAsState(initial = VaultSettings.DEFAULT_MEETINGS_HEADING)
                    val notesHeading by store.notesHeading
                        .collectAsState(initial = VaultSettings.DEFAULT_NOTES_HEADING)
                    // U3 (Google Calendar) is deferred; the no-op repository
                    // keeps the merged day view working with Obsidian-only
                    // data until OAuth is wired in.
                    val dailyNoteRepository = remember(vaultRoot, meetingsHeading, notesHeading) {
                        DailyNoteRepository(VaultSettings(vaultRoot, meetingsHeading = meetingsHeading, notesHeading = notesHeading))
                    }
                    val viewModel = remember(dailyNoteRepository) {
                        DayViewModel(dailyNoteRepository, NoOpGoogleCalendarRepository)
                    }
                    val vaultFileRepository = remember { VaultFileRepository() }
                    val diagramRepository = remember(vaultRoot) { DiagramRepository(vaultRoot) }
                    val vaultFileIndex = remember(vaultRoot) { VaultFileIndex(vaultRoot) }
                    val strokeStore = remember { StrokeStore() }
                    val monthScribbleStore = remember {
                        MonthScribbleStore(java.io.File(applicationContext.filesDir, "month-scribbles"))
                    }
                    val penSettings by penSettingsStore.settings.collectAsState(initial = PenSettings())

                    var screen by remember { mutableStateOf(Screen.CALENDAR) }
                    var showAdd by remember { mutableStateOf(false) }

                    // First-run welcome tour: shown once for a new user (e.g. a
                    // friend who just installed the app), and re-openable from
                    // Settings. null = still loading the flag; don't flash the
                    // tour before we know whether it's been completed.
                    val onboardingComplete by onboardingStore.onboardingComplete
                        .collectAsState(initial = null)
                    var forceOnboarding by remember { mutableStateOf(false) }
                    val showOnboarding = forceOnboarding || onboardingComplete == false

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

                    if (onboardingComplete == null) {
                        // Still loading the onboarding flag — render nothing
                        // this frame to avoid flashing the tour, then the real
                        // value resolves and routes correctly.
                    } else if (showOnboarding) {
                        OnboardingScreen(
                            onboardingStore = onboardingStore,
                            vaultSettingsStore = store,
                            onRequestAllFilesAccess = { launchAllFilesAccessSettings(this@MainActivity) },
                            hasAllFilesAccess = { VaultPermission.hasAllFilesAccess() },
                            onFinish = {
                                forceOnboarding = false
                                screen = Screen.CALENDAR
                            },
                        )
                    } else {
                    when (screen) {
                        Screen.CALENDAR -> {
                            Column(modifier = Modifier.fillMaxSize()) {
                                AppTopBar(
                                    onSettingsClick = { screen = Screen.SETTINGS },
                                    onAddClick = { showAdd = true },
                                    onTodayClick = { viewModel.selectDate(java.time.LocalDate.now()) },
                                    onVaultNotesClick = { screen = Screen.VAULT_NOTES },
                                    onMonthScribbleClick = { screen = Screen.MONTH_SCRIBBLE },
                                )
                                CalendarScreen(
                                    viewModel = viewModel,
                                    dailyNoteRepository = dailyNoteRepository,
                                    diagramRepository = diagramRepository,
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
                                onShowOnboarding = { forceOnboarding = true },
                            )
                        }
                        Screen.VAULT_NOTES -> {
                            VaultNotesScreen(
                                fileIndex = vaultFileIndex,
                                fileRepository = vaultFileRepository,
                                diagramRepository = diagramRepository,
                                strokeStore = strokeStore,
                                penSettings = penSettings,
                                onBack = { screen = Screen.CALENDAR },
                            )
                        }
                        Screen.MONTH_SCRIBBLE -> {
                            MonthScribbleScreen(
                                store = monthScribbleStore,
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

    override fun onPause() {
        super.onPause()
        // Reflect any edits made in-app on the home-screen agenda widget.
        AgendaWidgetProvider.refresh(applicationContext)
    }
}
