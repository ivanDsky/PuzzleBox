package com.ivandsky.puzzlebox.play.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Serial name pinned to the pre-split class name so an older saved back stack still restores.
/**
 * Replay of a captured day (ticket 14): the history-built session blob rides base64
 * in the key; the play entry restores it via its existing restore path. The blob is
 * self-describing (A19) and carries the day's ref, so records stay attributed to
 * their day (A4).
 */
@Serializable
@SerialName("com.ivandsky.puzzlebox.app.nav.ReplayZip")
data class ReplayKey(val session: String) : NavKey
