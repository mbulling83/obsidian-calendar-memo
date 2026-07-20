package com.boxmemo.app.update

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/** What the Calendar's update banner should show, if anything. */
sealed interface UpdateUiState {
    data object Hidden : UpdateUiState
    data class Available(val release: LatestRelease) : UpdateUiState
    data class Downloading(val release: LatestRelease) : UpdateUiState
    data class Failed(val release: LatestRelease, val message: String) : UpdateUiState
}

/** Result of a manual "check for updates" tap, for one-shot feedback. */
enum class ManualCheckResult { UPDATE_AVAILABLE, UP_TO_DATE, CHECK_FAILED }

/**
 * Orchestrates the in-app updater: checks GitHub for a newer release, exposes
 * a [state] for the banner, and downloads + launches the installer on demand.
 * Holds no Android UI — MainActivity collects [state] and drives it from
 * coroutines. Fail-silent throughout: a failed check just leaves the banner
 * hidden, so the app is fully usable offline.
 */
class UpdateManager(
    private val appContext: Context,
    private val currentVersion: String,
    private val store: UpdateSettingsStore,
    private val client: GitHubUpdateClient = GitHubUpdateClient(),
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Hidden)
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    /** Throttled check run once per app launch; no-op if checked recently. */
    suspend fun checkOnLaunch(minIntervalMs: Long = 24 * 60 * 60 * 1000L) {
        restorePersistedUpdate()
        if (!store.autoCheckEnabled.first()) return
        if (now() - store.lastCheckMs() < minIntervalMs) return
        runCheck()
    }

    /**
     * Re-shows the banner for an update a previous launch already found — the
     * throttle would otherwise hide it for up to a day after process death.
     * Clears the stored release once it's no longer newer (i.e. installed).
     */
    private suspend fun restorePersistedUpdate() {
        val saved = store.availableRelease() ?: return
        if (UpdateVersions.isNewer(saved.version, currentVersion)) {
            if (_state.value is UpdateUiState.Hidden) {
                _state.value = UpdateUiState.Available(saved)
            }
        } else {
            store.setAvailableRelease(null)
        }
    }

    /** Force an immediate check (e.g. a Settings button); returns the outcome. */
    suspend fun checkNow(): ManualCheckResult {
        val latest = runCheck() ?: return ManualCheckResult.CHECK_FAILED
        return if (UpdateVersions.isNewer(latest.version, currentVersion)) {
            ManualCheckResult.UPDATE_AVAILABLE
        } else {
            ManualCheckResult.UP_TO_DATE
        }
    }

    /** Fetch + apply to [state]; returns the release fetched (or null on error). */
    private suspend fun runCheck(): LatestRelease? {
        // Only mark the check done on success — a failed fetch (offline,
        // rate-limited) shouldn't start the daily throttle, so connectivity
        // returning gets a fresh check on the next launch.
        val latest = client.fetchLatest() ?: return null
        store.setLastCheckMs(now())
        if (UpdateVersions.isNewer(latest.version, currentVersion)) {
            store.setAvailableRelease(latest)
            // Don't clobber an in-flight download.
            if (_state.value !is UpdateUiState.Downloading) {
                _state.value = UpdateUiState.Available(latest)
            }
        } else {
            store.setAvailableRelease(null)
            if (_state.value !is UpdateUiState.Downloading) {
                _state.value = UpdateUiState.Hidden
            }
        }
        return latest
    }

    fun dismiss() {
        if (_state.value !is UpdateUiState.Downloading) _state.value = UpdateUiState.Hidden
    }

    /** Download the release APK and hand it to the system installer. */
    suspend fun downloadAndInstall(release: LatestRelease) {
        _state.value = UpdateUiState.Downloading(release)
        val apk = ApkInstaller.download(appContext, release.apkUrl, release.version)
        if (apk == null) {
            _state.value = UpdateUiState.Failed(release, "Download failed — check your connection and try again.")
            return
        }
        // Back to Available so the banner offers a retry if the user cancels
        // the system installer; a successful install replaces the app anyway.
        _state.value = UpdateUiState.Available(release)
        ApkInstaller.launchInstaller(appContext, apk)
    }
}
