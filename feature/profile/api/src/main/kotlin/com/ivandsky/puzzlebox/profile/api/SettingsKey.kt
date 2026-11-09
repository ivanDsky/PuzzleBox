package com.ivandsky.puzzlebox.profile.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Settings, a shell screen opened from the shell chrome. Serial name pinned for saved state. */
@Serializable
@SerialName("com.ivandsky.puzzlebox.app.nav.Settings")
data object SettingsKey : NavKey
