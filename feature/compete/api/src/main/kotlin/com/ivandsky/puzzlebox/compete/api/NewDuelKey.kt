package com.ivandsky.puzzlebox.compete.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The New duel screen. [friendUid], [friendName] and [game] carry a challenge, all empty for none. */
@Serializable
@SerialName("com.ivandsky.puzzlebox.app.nav.NewDuel")
data class NewDuelKey(val friendUid: String = "", val friendName: String = "", val game: String = "") : NavKey
