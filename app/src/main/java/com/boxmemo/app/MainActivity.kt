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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import com.boxmemo.app.ui.BoxMemoColorScheme
import com.boxmemo.app.ui.BoxMemoTypography
import com.boxmemo.app.update.ManualCheckResult
import com.boxmemo.app.update.UpdateBanner
import com.boxmemo.app.update.UpdateManager
import com.boxmemo.app.update.UpdateSettingsStore
import com.boxmemo.app.memo.PenSettings
import com.boxmemo.app.vault.DailyNoteRepository
import com.boxmemo.app.vault.DiagramRepository
import com.boxmemo.app.vault.VaultDiagnosis
import com.boxmemo.app.vault.VaultFileIndex
import com.boxmemo.app.vault.VaultFileRepository
import com.boxmemo.app.vault.VaultScanner
import com.boxmemo.app.vault.VaultSettings
import com.boxmemo.app.vaultcheck.VaultCheckScreen
import com.boxmemo.app.vaultcheck.VaultHealthBanner
import com.boxmemo.app.vaultnotes.VaultNotesScreen
import com.boxmemo.app.widget.AgendaWidgetProvider

private enum class Screen { CALENDAR, SETTINGS, VAULT_NOTES, MONTH_SCRIBBLE, VAULT_CHECK }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val store = VaultSettingsStore(applicationContext)
        val penSettingsStore = PenSettingsStore(applicationContext)
        val hwrSettingsStore = HwrSettingsStore(applicationContext)
        val onboardingStore = OnboardingSettingsStore(applicationContext)
        val updateManager = UpdateManager(
            appContext = applicationContext,
            currentVersion = BuildConfig.VERSION_NAME,
            store = UpdateSettingsStore(applicationContext),
        )

        setContent {
            MaterialTheme(colorScheme = BoxMemoColorScheme, typography = BoxMemoTypography) {
                Surface {
                    val vaultRoot by store.vaultRoot.collectAsState(initial = null)
                    val dailyNoteTemplate by store.dailyNoteTemplate
                        .collectAsState(initial = VaultSettings.DEFAULT_TEMPLATE)
                    val dailyNoteTemplatePath by store.dailyNoteTemplatePath
                        .collectAsState(initial = null)
                    val autoCreateMissingNotes by store.autoCreateMissingNotes
                        .collectAsState(initial = false)
                    val meetingsHeading by store.meetingsHeading
                        .collectAsState(initial = VaultSettings.DEFAULT_MEETINGS_HEADING)
                    val notesHeading by store.notesHeading
                        .collectAsState(initial = VaultSettings.DEFAULT_NOTES_HEADING)
                    val vaultSettings = remember(
                        vaultRoot, dailyNoteTemplate, meetingsHeading, notesHeading,
                        dailyNoteTemplatePath, autoCreateMissingNotes,
                    ) {
                        VaultSettings(
                            vaultRoot = vaultRoot,
                            dailyNoteSubpathTemplate = dailyNoteTemplate,
                            meetingsHeading = meetingsHeading,
                            notesHeading = notesHeading,
                            dailyNoteTemplatePath = dailyNoteTemplatePath,
                            autoCreateMissingNotes = autoCreateMissingNotes,
                        )
                    }
                    // U3 (Google Calendar) is deferred; the no-op repository
                    // keeps the merged day view working with Obsidian-only
                    // data until OAuth is wired in.
                    val dailyNoteRepository = remember(vaultSettings) {
                        DailyNoteRepository(vaultSettings)
                    }

                    // Vault health: scan recent notes off the main thread whenever
                    // the vault config changes, so the Calendar can warn when no
                    // meetings can be read (wrong folder / heading) instead of
                    // just showing empty days.
                    val scanScope = rememberCoroutineScope()
                    var vaultDiagnosis by remember { mutableStateOf<VaultDiagnosis?>(null) }
                    var bannerDismissed by remember(vaultSettings) { mutableStateOf(false) }
                    val rescan: () -> Unit = {
                        scanScope.launch {
                            val result = withContext(Dispatchers.IO) { VaultScanner(vaultSettings).scan() }
                            vaultDiagnosis = result
                        }
                    }
                    LaunchedEffect(vaultSettings) {
                        vaultDiagnosis = withContext(Dispatchers.IO) { VaultScanner(vaultSettings).scan() }
                    }
                    val viewModel = remember(dailyNoteRepository) {
                        DayViewModel(dailyNoteRepository, NoOpGoogleCalendarRepository)
                    }
                    // Rebuilding on settings change means lifecycle clearing never
                    // runs — cancel the replaced instance's scope explicitly so
                    // each vault-config change can't leak a live coroutine scope.
                    DisposableEffect(viewModel) {
                        onDispose { viewModel.dispose() }
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

                    // In-app updater: throttled GitHub check once per launch.
                    val updateState by updateManager.state.collectAsState()
                    val updateScope = rememberCoroutineScope()
                    LaunchedEffect(Unit) { updateManager.checkOnLaunch() }
                    val onCheckForUpdates: () -> Unit = {
                        updateScope.launch {
                            val result = updateManager.checkNow()
                            val text = when (result) {
                                ManualCheckResult.UPDATE_AVAILABLE -> "Update available — see the Calendar."
                                ManualCheckResult.UP_TO_DATE -> "You're on the latest version."
                                ManualCheckResult.CHECK_FAILED -> "Couldn't check — check your connection."
                            }
                            Toast.makeText(context, text, Toast.LENGTH_LONG).show()
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
                                if (!bannerDismissed) {
                                    VaultHealthBanner(
                                        diagnosis = vaultDiagnosis,
                                        onDiagnose = { screen = Screen.VAULT_CHECK },
                                        onDismiss = { bannerDismissed = true },
                                    )
                                }
                                UpdateBanner(
                                    state = updateState,
                                    onUpdate = { release ->
                                        updateScope.launch { updateManager.downloadAndInstall(release) }
                                    },
                                    onDismiss = { updateManager.dismiss() },
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
                                onCheckVault = { screen = Screen.VAULT_CHECK },
                                onCheckForUpdates = onCheckForUpdates,
                            )
                        }
                        Screen.VAULT_CHECK -> {
                            VaultCheckScreen(
                                diagnosis = vaultDiagnosis,
                                onApplyMeetingsHeading = { scanScope.launch { store.setMeetingsHeading(it) } },
                                onApplyNotesHeading = { scanScope.launch { store.setNotesHeading(it) } },
                                onApplyTemplate = { scanScope.launch { store.setDailyNoteTemplate(it) } },
                                onApplyVaultRoot = { scanScope.launch { store.setVaultRoot(it) } },
                                onRescan = rescan,
                                onBack = { screen = Screen.CALENDAR },
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
                                hwrSettingsStore = hwrSettingsStore,
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
