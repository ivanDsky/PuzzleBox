package com.ivandsky.puzzlebox.domain.nextthing

import com.ivandsky.puzzlebox.domain.moments.Moment

/**
 * What Result shows for one solve at one moment of [ObserveResultNextThing]'s flow.
 *
 * @property moments what the solve has earned so far, strongest first (L1); a friend-board moment joins when the
 *   board lands. Empty for a play again or a practice puzzle, which earn none.
 * @property nextThing what the main button offers (L3); null until the chain has answered, and for good for a
 *   play again or a practice puzzle, which get none.
 */
data class ResultNextThing(
    val moments: List<Moment> = emptyList(),
    val nextThing: NextThing? = null,
)
