package com.ivandsky.puzzlebox.compete.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The reveal screen of a settled duel. [code] is the canonical ShareCode value. Serial name pinned for saved state. */
@Serializable
@SerialName("com.ivandsky.puzzlebox.app.nav.DuelReveal")
data class DuelRevealKey(val code: String) : NavKey
