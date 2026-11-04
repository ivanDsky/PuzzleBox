package com.ivandsky.puzzlebox.play.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Serial name pinned to the pre-split class name so an older saved back stack still restores.
/** Play tab root (top level); also pushed by Home's "Play today". */
@Serializable
@SerialName("com.ivandsky.puzzlebox.app.nav.Play")
data object PlayKey : NavKey
