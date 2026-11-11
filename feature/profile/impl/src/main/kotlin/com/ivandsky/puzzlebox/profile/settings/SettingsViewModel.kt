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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Settings state: the stored theme mode, the Games switches (live from [games]), [AppBuild.versionName] and, when
 * [link] is given, the LinkedIn block following the link live; a pick is saved through [preferences] and a switch
 * through [games]. A null [link] (the feature is off) leaves
 * the LinkedIn block out of the state entirely and makes the LinkedIn actions no-ops.
 */
internal class SettingsViewModel(
    private val preferences: UserPreferencesRepository,
    private val games: GameOptions,
    appBuild: AppBuild,
    private val link: LinkedInLink?,
) : ViewModel() {

    private val gamesSection: Flow<GamesSectionState> =
        combine(games.queensAutoCross, games.sudokuPeerHighlight, games.sudokuSameDigitHighlight, ::GamesSectionState)

    private val states: Flow<SettingsUiState> =
        if (link == null) {
            combine(preferences.themeMode, gamesSection) { mode, gamesState ->
                SettingsUiState(themeMode = mode, versionName = appBuild.versionName, games = gamesState)
            }
        } else {
            combine(preferences.themeMode, gamesSection, link.state) { mode, gamesState, linkState ->
                SettingsUiState(
                    themeMode = mode,
                    versionName = appBuild.versionName,
                    games = gamesState,
                    linkedIn = linkState.toSection(),
                )
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

    /** Games: written to the store the in-game overflow toggle reads, so the board shows it next time it opens. */
    fun onSetQueensAutoCross(enabled: Boolean) {
        viewModelScope.launch { games.setQueensAutoCross(enabled) }
    }

    fun onSetSudokuPeerHighlight(enabled: Boolean) {
        viewModelScope.launch { games.setSudokuPeerHighlight(enabled) }
    }

    fun onSetSudokuSameDigitHighlight(enabled: Boolean) {
        viewModelScope.launch { games.setSudokuSameDigitHighlight(enabled) }
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
