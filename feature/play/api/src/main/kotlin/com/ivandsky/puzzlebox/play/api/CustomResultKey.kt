package com.ivandsky.puzzlebox.play.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A solved friend's puzzle: [gameId] and [puzzleId] (its share code) name the play's custom
 * slot, and the solve's [timeMs] and [hintsUsed] ride the key so the page renders after
 * process death. Solving replaces the board with this entry, so back lands on the card.
 */
@Serializable
@SerialName("com.ivandsky.puzzlebox.app.nav.CustomResult")
data class CustomResultKey(val gameId: String, val puzzleId: String, val timeMs: Long, val hintsUsed: Int) : NavKey
