package com.ivandsky.puzzlebox.games.strands.engine

import com.ivandsky.puzzlebox.game.Hint
import com.ivandsky.puzzlebox.games.strands.ActiveHint

/** The outcome of asking for a hint. */
sealed interface StrandsHint : Hint {
    /** Spend one hint: put [hint] on the board. */
    data class Reveal(val hint: ActiveHint) : StrandsHint

    /** A hint is already fully shown and its word not yet found. */
    data object OnBoard : StrandsHint

    /** No hint is earned. */
    data object NoneAvailable : StrandsHint
}
