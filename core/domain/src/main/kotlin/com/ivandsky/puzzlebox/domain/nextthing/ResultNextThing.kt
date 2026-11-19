package com.ivandsky.puzzlebox.domain.nextthing

import com.ivandsky.puzzlebox.domain.moments.Moment

/**
 * What Result shows for one solve at one moment of Play's ObserveResultNextThing flow.
 *
 * @property moments what the solve has earned so far, strongest first (L1); a friend-board moment joins when the
 *   board lands. Empty for a play again or a practice puzzle, which earn none.
 * @property nextThing what the main button offers (L3); null until the chain has answered, and for good for a
 *   play again or a practice puzzle, which get none.
 * @property challenge the friend Result's Challenge row names (L4), whatever [nextThing] is: the same friend the
 *   chain's challenge rung would offer. Null until the Friends board and that friend's tier have been read, and for
 *   good when there is nobody to challenge.
 */
data class ResultNextThing(
    val moments: List<Moment> = emptyList(),
    val nextThing: NextThing? = null,
    val challenge: NextThing.ChallengeFriend? = null,
)
