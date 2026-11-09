package com.ivandsky.puzzlebox.play.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One leg ([legIndex]) of the duel [code] (the canonical six symbols). gameId rides in the key
 * so a killed leg reopens its saved board without the match document.
 */
@Serializable
@SerialName("com.ivandsky.puzzlebox.app.nav.MatchLeg")
data class MatchLegKey(val code: String, val legIndex: Int, val gameId: String) : NavKey
