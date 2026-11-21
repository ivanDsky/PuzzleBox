package com.ivandsky.puzzlebox.train.practice

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The one-time bubble that points at the "How it works" chip once the first intro has been left ([introLeft]): "You can
 * watch this intro again any time from here." It is gone for good once the player taps OK or uses the chip
 * ([dismiss]). One per process (a Koin single), so it outlives the per-technique view models; it is in memory only,
 * so a restart shows it once more.
 */
internal class IntroHint {
    private val shown = MutableStateFlow(false)
    private var seen = false

    /** Whether the bubble is up. */
    val visible: StateFlow<Boolean> = shown.asStateFlow()

    /** An intro was left for the board: the bubble comes up unless the player has already been shown it. */
    fun introLeft() {
        if (!seen) shown.value = true
    }

    /** The bubble is answered, by OK or by the chip: never again. */
    fun dismiss() {
        seen = true
        shown.value = false
    }
}
