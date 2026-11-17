package com.ivandsky.puzzlebox.create.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Legacy only: the key of the four-tab app's Create tab. It stays registered, under its pinned serial name, so an old
 * saved navigation state still decodes (the app drops its stack on restore). No code uses it as a tab root any more.
 */
@Serializable
@SerialName("com.ivandsky.puzzlebox.app.nav.Create")
data object CreateKey : NavKey
