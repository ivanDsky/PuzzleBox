package com.ivandsky.puzzlebox.profile.settings

import com.ivandsky.puzzlebox.model.ThemeMode

/** What Settings shows: the stored theme choice, the Games switches and the app's version name. */
internal data class SettingsUiState(
    val themeMode: ThemeMode,
    val versionName: String,
    val games: GamesSectionState = GamesSectionState(),
)
