package com.ivandsky.puzzlebox.compete.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The duel screen (invite, puzzles list). [code] is the canonical ShareCode value. Serial name pinned for saved state. */
@Serializable
@SerialName("com.ivandsky.puzzlebox.app.nav.Duel")
data class DuelKey(val code: String) : NavKey
