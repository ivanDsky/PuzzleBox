package com.ivandsky.puzzlebox.profile.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.data.linkedin.LinkState
import com.ivandsky.puzzlebox.data.linkedin.LinkedInLink
import com.ivandsky.puzzlebox.data.repository.UserPreferencesRepository
import com.ivandsky.puzzlebox.domain.AppBuild
import com.ivandsky.puzzlebox.game.linkedin.LinkedInRead
import com.ivandsky.puzzlebox.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Settings state: the stored theme mode, [AppBuild.versionName] and, when [link] is given, the LinkedIn block
 * following the link live; a pick is saved through [preferences]. A null [link] (the feature is off) leaves
 * the LinkedIn block out of the state entirely and makes the LinkedIn actions no-ops.
 */
internal class SettingsViewModel(
    private val preferences: UserPreferencesRepository,
    appBuild: AppBuild,
    private val link: LinkedInLink?,
) : ViewModel() {

    private val states: Flow<SettingsUiState> =
        if (link == null) {
            preferences.themeMode.map { SettingsUiState(themeMode = it, versionName = appBuild.versionName) }
        } else {
            combine(preferences.themeMode, link.state) { mode, linkState ->
                SettingsUiState(themeMode = mode, versionName = appBuild.versionName, linkedIn = linkState.toSection())
            }
        }

    val uiState: StateFlow<SettingsUiState> =
        states.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SettingsUiState(themeMode = ThemeMode.SYSTEM, versionName = appBuild.versionName),
        )

    /** Appearance: persisted; the app root re-themes from the stored mode. */
    fun onSetThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferences.setThemeMode(mode) }
    }

    /** Lifts the pause only; the latch is the link's. */
    fun onResumeLinkedIn() {
        val link = link ?: return
        viewModelScope.launch { link.resume() }
    }

    /** The cookie wipe and database clear are the link's; this only asks. */
    fun onDisconnectLinkedIn() {
        val link = link ?: return
        viewModelScope.launch { link.disconnect() }
    }
}

private fun LinkState.toSection(): LinkedInSectionState = when (this) {
    LinkState.NotConnected -> LinkedInSectionState.NotConnected
    is LinkState.Connected -> LinkedInSectionState.Connected
    is LinkState.Paused -> LinkedInSectionState.Paused(
        reason = kind.reason(),
        needsReconnect = kind == LinkedInRead.Rejected.Kind.SessionExpired,
    )
}

private fun LinkedInRead.Rejected.Kind.reason(): String = when (this) {
    LinkedInRead.Rejected.Kind.SessionExpired -> "Your LinkedIn login expired. Reconnect to keep reading."
    LinkedInRead.Rejected.Kind.Challenge -> "LinkedIn asked for a check, so reading is paused."
    LinkedInRead.Rejected.Kind.RateLimited -> "LinkedIn is asking us to slow down, so reading is paused."
    LinkedInRead.Rejected.Kind.Unexpected -> "LinkedIn answered in a way we didn't expect, so reading is paused."
}
