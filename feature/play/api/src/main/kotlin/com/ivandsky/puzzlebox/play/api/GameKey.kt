package com.ivandsky.puzzlebox.play.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One game's board on any stack (A9, N21): the key names the play's slot, the store holds
 * the live play. [day] is ISO-8601; null means that game's today, pinned by the store the
 * first time the key opens, so a board left open across midnight keeps its day.
 */
@Serializable
@SerialName("com.ivandsky.puzzlebox.app.nav.Game")
data class GameKey(val gameId: String, val day: String? = null) : NavKey
