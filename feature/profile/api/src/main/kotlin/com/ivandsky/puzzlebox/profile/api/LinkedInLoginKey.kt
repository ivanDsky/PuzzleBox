package com.ivandsky.puzzlebox.profile.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The LinkedIn login, pushed from the shell's Connect action. It carries no data: the session
 * lives in the web view and the store, never in the back stack or saved state.
 */
@Serializable
@SerialName("com.ivandsky.puzzlebox.profile.LinkedInLogin")
data object LinkedInLoginKey : NavKey
