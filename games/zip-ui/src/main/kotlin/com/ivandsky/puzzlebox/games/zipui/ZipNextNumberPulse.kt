package com.ivandsky.puzzlebox.games.zipui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember

/** How long one halo wave takes to spread and fade; a second starts [HALO_WAVE_MILLIS] / 2 behind it. */
private const val HALO_WAVE_MILLIS = 1800

/**
 * The halo of the next number to reach with ZipDotPulse (backlog #1151), for a board that shows it
 * live (play, tutorial, replay): where in the wave cycle it is, 0 up to 1 and round again every
 * 1.8 s. It never waits for a finger, so it keeps going while a trace is drawn. With [enabled] false
 * (reduced motion, or no number left to reach) it returns null and nothing runs.
 *
 * Returns a reader to call in the draw phase: the halo repaints the one cell that draws it and
 * recomposes nothing. This is the one endless animation in a game module (konsist S6 allowlist):
 * the board's idle cue, not a response to an input. Boards without ZipDotPulse and static
 * previews draw no next-number mark at all.
 */
@Composable
internal fun rememberNextNumberHalo(enabled: Boolean): (() -> Float)? {
    val phase = remember { Animatable(0f) }
    LaunchedEffect(phase, enabled) {
        if (!enabled) return@LaunchedEffect
        phase.snapTo(0f)
        phase.animateTo(1f, infiniteRepeatable(tween(HALO_WAVE_MILLIS, easing = LinearEasing), RepeatMode.Restart))
    }
    return if (enabled) remember(phase) { { phase.value } } else null
}
