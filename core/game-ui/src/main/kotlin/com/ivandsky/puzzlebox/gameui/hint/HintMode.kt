package com.ivandsky.puzzlebox.gameui.hint

import kotlinx.coroutines.CoroutineDispatcher

/**
 * How a game controller hints (spec A20), chosen where the game is registered: controllers never read a flag.
 * [Legacy] keeps today's hint path byte-for-byte; [Stepwise] searches one deduction per press on
 * [Stepwise.compute], off the main thread (A19).
 */
sealed interface HintMode {
    data object Legacy : HintMode

    data class Stepwise(val compute: CoroutineDispatcher) : HintMode
}
