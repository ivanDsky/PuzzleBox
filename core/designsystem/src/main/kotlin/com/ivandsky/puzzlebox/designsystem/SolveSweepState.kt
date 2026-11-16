package com.ivandsky.puzzlebox.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * Drives one board's solve sweep (identity §2.6, spec A6): [play] runs it once, again on
 * every call. Hold it with [rememberSolveSweepState] and draw it with [solveSweep]. A [played]
 * one starts at the sweep's end, as if it had already run.
 */
@Stable
class SolveSweepState internal constructor(played: Boolean = false) {
    /** How many times [play] ran; 0 = never, so nothing is drawn yet. */
    internal var plays by mutableIntStateOf(if (played) 1 else 0)
        private set

    /** Linear time through the sweep, 0..1; each layer applies its own easing to it. */
    internal val clock = Animatable(if (played) 1f else 0f)

    fun play() {
        plays++
    }
}
