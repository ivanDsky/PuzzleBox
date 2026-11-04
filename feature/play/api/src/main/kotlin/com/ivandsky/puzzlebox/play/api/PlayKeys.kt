package com.ivandsky.puzzlebox.play.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.PolymorphicModuleBuilder

// Serial names are pinned to the pre-split class names (app.nav.Play/Result/ReplayZip) and
// field names are unchanged, so a back stack saved by an older build still restores.

/** Play tab root (top level); also pushed by Home's "Play today". */
@Serializable
@SerialName("com.ivandsky.puzzlebox.app.nav.Play")
data object PlayKey : NavKey

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

/**
 * Replay of a captured day (ticket 14): the history-built session blob rides base64
 * in the key; the play entry restores it via its existing restore path. The blob is
 * self-describing (A19) and carries the day's ref, so records stay attributed to
 * their day (A4).
 */
@Serializable
@SerialName("com.ivandsky.puzzlebox.app.nav.ReplayZip")
data class ReplayKey(val session: String) : NavKey

/** Registers Play's keys (play, result, replay) for back-stack saved state. */
fun PolymorphicModuleBuilder<NavKey>.playNavKeys() {
    subclass(PlayKey::class, PlayKey.serializer())
    subclass(ResultKey::class, ResultKey.serializer())
    subclass(ReplayKey::class, ReplayKey.serializer())
}
