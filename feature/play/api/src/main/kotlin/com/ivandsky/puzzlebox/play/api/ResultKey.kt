package com.ivandsky.puzzlebox.play.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Serial name pinned to the pre-split class name so an older saved back stack still restores.
/**
 * Solved-session result (ticket 23, spec UIUX-01 A3/A9): a proper route, not an overlay.
 * The frozen A3 facts ride the key so the surface renders even after process death; the
 * record badge is live flow state — the record write is fire-and-observe, never awaited.
 * Solving replaces everything above Home with this entry, so the system back (a plain
 * pop) lands on Home — back never surprises (senior-UX wayfinding).
 */
@Serializable
@SerialName("com.ivandsky.puzzlebox.app.nav.Result")
data class ResultKey(val timeMs: Long, val hintsUsed: Int) : NavKey
