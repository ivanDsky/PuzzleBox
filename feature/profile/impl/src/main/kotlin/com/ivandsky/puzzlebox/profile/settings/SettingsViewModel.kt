package com.ivandsky.puzzlebox.profile.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.data.repository.UserPreferencesRepository
import com.ivandsky.puzzlebox.domain.AppBuild
import com.ivandsky.puzzlebox.model.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Settings state: the stored theme mode plus [AppBuild.versionName]; a pick is saved through [preferences]. */
internal class SettingsViewModel(
    private val preferences: UserPreferencesRepository,
    appBuild: AppBuild,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> =
        preferences.themeMode
            .map { SettingsUiState(themeMode = it, versionName = appBuild.versionName) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = SettingsUiState(themeMode = ThemeMode.SYSTEM, versionName = appBuild.versionName),
            )

    /** Appearance: persisted; the app root re-themes from the stored mode. */
    fun onSetThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferences.setThemeMode(mode) }
    }
}
