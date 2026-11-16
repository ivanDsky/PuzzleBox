package com.ivandsky.puzzlebox.gameui.hint

import kotlinx.coroutines.CoroutineDispatcher

/**
 * How a game controller hints (spec A20), chosen where the game is registered: controllers never read a flag.
 * [Legacy] keeps today's hint path byte-for-byte; [Stepwise] searches one deduction per press on
 * [Stepwise.compute], off the main thread (A19). [FirstMove] is the first-move rule (nav-rethink B4) for a game
 * whose engine picker is not stepwise (Zip, Strands); [Stepwise.firstMove] is the same rule for a stepwise game.
 */
sealed interface HintMode {
    data object Legacy : HintMode

    /** @property firstMove the picker skips a step the player already knows (B4); off by default. */
    data class Stepwise(val compute: CoroutineDispatcher, val firstMove: Boolean = false) : HintMode

    /** A hint is the first move that is forced or most constrained and not yet obvious (B4), searched on [compute]. */
    data class FirstMove(val compute: CoroutineDispatcher) : HintMode
}
