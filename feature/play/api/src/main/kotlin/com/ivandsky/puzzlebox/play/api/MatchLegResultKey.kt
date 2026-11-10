package com.ivandsky.puzzlebox.play.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A finished duel puzzle: leg [legIndex] of the duel [code], solved in [timeMs] with [hintsUsed]
 * hints, or given up ([gaveUp]; time and hints are 0). The finish rides the key so the page renders
 * after process death. Finishing replaces the board with this entry, and a taken puzzle reopened
 * lands here instead of on a fresh board.
 */
@Serializable
@SerialName("com.ivandsky.puzzlebox.app.nav.MatchLegResult")
data class MatchLegResultKey(val code: String, val legIndex: Int, val timeMs: Long, val hintsUsed: Int, val gaveUp: Boolean) : NavKey
