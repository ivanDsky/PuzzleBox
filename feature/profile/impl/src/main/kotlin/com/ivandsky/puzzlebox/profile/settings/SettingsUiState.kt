package com.ivandsky.puzzlebox.profile.settings

import com.ivandsky.puzzlebox.data.visibility.OwnTier
import com.ivandsky.puzzlebox.model.ThemeMode

/**
 * What Settings shows: the stored theme choice, the Games switches, the player's own tier ([visibility], null: no Visibility
 * section, and no Ranks section either), the Ranks default ([ranksFriendsDefault]: Friends, else Global), the Ranks LinkedIn games default ([ranksLinkedInDefault]), the app-wide [vibration] switch, the Play section (its [showSelfDuelTimeLeft] switch), the sign-in prompt of the Visibility section ([signedIn]: the player has an account; [signingIn]: a sign-in is running; [signInMessage]: why the last one failed), [choiceRows] (Appearance as radio rows, not the segmented control) and the app's version name.
 */
internal data class SettingsUiState(
    val themeMode: ThemeMode,
    val versionName: String,
    val games: GamesSectionState = GamesSectionState(),
    val visibility: OwnTier? = null,
    val ranksFriendsDefault: Boolean = true,
    val ranksLinkedInDefault: Boolean = false,
    val vibration: Boolean = true,
    val showSelfDuelTimeLeft: Boolean = true,
    val signedIn: Boolean = false,
    val signingIn: Boolean = false,
    val signInMessage: String? = null,
    val choiceRows: Boolean = false,
)
