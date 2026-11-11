package com.ivandsky.puzzlebox.profile.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.data.repository.UserPreferencesRepository
import com.ivandsky.puzzlebox.domain.AppBuild
import com.ivandsky.puzzlebox.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Settings state: the stored theme mode, the Games switches (live from [games]) and [AppBuild.versionName]; a pick is
 * saved through [preferences] and a switch through [games].
 */
internal class SettingsViewModel(
    private val preferences: UserPreferencesRepository,
    private val games: GameOptions,
    appBuild: AppBuild,
) : ViewModel() {

    private val gamesSection: Flow<GamesSectionState> =
        combine(games.queensAutoCross, games.sudokuPeerHighlight, games.sudokuSameDigitHighlight, ::GamesSectionState)

    val uiState: StateFlow<SettingsUiState> =
        combine(preferences.themeMode, gamesSection) { mode, gamesState ->
            SettingsUiState(themeMode = mode, versionName = appBuild.versionName, games = gamesState)
        }.stateIn(
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
}
