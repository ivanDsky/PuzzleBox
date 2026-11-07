package com.ivandsky.puzzlebox.play.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The board of one friend's puzzle: [puzzleId] is its share code, and the key names the
 * play's custom slot in [gameId], so the store holds the live play as it does a daily's.
 */
@Serializable
@SerialName("com.ivandsky.puzzlebox.app.nav.CustomGame")
data class CustomGameKey(val gameId: String, val puzzleId: String) : NavKey
