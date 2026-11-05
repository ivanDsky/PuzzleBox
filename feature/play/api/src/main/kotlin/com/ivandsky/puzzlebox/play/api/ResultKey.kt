package com.ivandsky.puzzlebox.play.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Serial name pinned to the pre-split class name so an older saved back stack still restores.
/**
 * A solved play's result (A5, A7, N4–N6): [gameId] and [day] name the play's slot (an
 * ISO date, always resolved), and the frozen facts ride the key so the surface renders
 * after process death (N20). Solving replaces the game with this entry, so back lands
 * on the game's origin (SC4).
 */
@Serializable
@SerialName("com.ivandsky.puzzlebox.app.nav.Result")
data class ResultKey(val gameId: String, val day: String, val timeMs: Long, val hintsUsed: Int) : NavKey
