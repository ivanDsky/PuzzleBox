package com.ivandsky.puzzlebox.games.strands.engine

import com.ivandsky.puzzlebox.games.strands.StrandsState

/** The hint economy: what is earned, the meter, and the one hint decision. */
object StrandsHints {

    fun available(state: StrandsState): Int = 0

    /** Thirds of the meter filled toward the next hint. */
    fun meterThirds(state: StrandsState): Int = state.nonTheme.size % 3

    fun hintFor(state: StrandsState): StrandsHint = StrandsHint.NoneAvailable
}
