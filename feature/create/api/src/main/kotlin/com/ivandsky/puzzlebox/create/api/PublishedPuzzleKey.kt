package com.ivandsky.puzzlebox.create.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The page of a puzzle this user published under the share code [code] (its canonical value). */
@Serializable
@SerialName("com.ivandsky.puzzlebox.app.nav.PublishedPuzzle")
data class PublishedPuzzleKey(val code: String) : NavKey
