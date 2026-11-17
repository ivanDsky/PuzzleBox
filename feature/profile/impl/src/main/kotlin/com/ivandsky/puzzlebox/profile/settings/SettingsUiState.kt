package com.ivandsky.puzzlebox.profile.settings

import com.ivandsky.puzzlebox.data.visibility.OwnTier
import com.ivandsky.puzzlebox.model.ThemeMode

/**
 * What Settings shows: the stored theme choice, the Games switches, the player's own tier ([visibility], null: no Visibility
 * section, and no Ranks section either), the Ranks default ([ranksFriendsDefault]: Friends, else Global), the app-wide [vibration] switch, the Play section ([duelPlay]: shown only while the duel flag is on, with its [showSelfDuelTimeLeft] switch) the [signInMessage] of a failed sign-in from the Visibility section, and the app's version name.
 */
internal data class SettingsUiState(
    val themeMode: ThemeMode,
    val versionName: String,
    val games: GamesSectionState = GamesSectionState(),
    val visibility: OwnTier? = null,
    val ranksFriendsDefault: Boolean = true,
    val vibration: Boolean = true,
    val duelPlay: Boolean = false,
    val showSelfDuelTimeLeft: Boolean = true,
    val signInMessage: String? = null,
)
