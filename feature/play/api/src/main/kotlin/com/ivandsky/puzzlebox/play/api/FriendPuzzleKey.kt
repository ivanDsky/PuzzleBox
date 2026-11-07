package com.ivandsky.puzzlebox.play.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A friend's puzzle card, opened by its share [code] (the canonical six symbols). */
@Serializable
@SerialName("com.ivandsky.puzzlebox.app.nav.FriendPuzzle")
data class FriendPuzzleKey(val code: String) : NavKey
