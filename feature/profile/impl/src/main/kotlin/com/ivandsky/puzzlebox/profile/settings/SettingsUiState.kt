package com.ivandsky.puzzlebox.profile.settings

import com.ivandsky.puzzlebox.model.ThemeMode

/**
 * What Settings shows: the stored theme choice, the Games switches, the app's version name and the LinkedIn block.
 * [linkedIn] defaults to not connected; the real value always comes from the link.
 */
internal data class SettingsUiState(
    val themeMode: ThemeMode,
    val versionName: String,
    val games: GamesSectionState = GamesSectionState(),
    val linkedIn: LinkedInSectionState = LinkedInSectionState.NotConnected,
)
