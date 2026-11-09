package com.ivandsky.puzzlebox.gameui.hint

import kotlinx.coroutines.CoroutineDispatcher

/**
 * The HintParity flag as a game controller receives it (spec A20): controllers never read a flag.
 * [Legacy] keeps today's hint path byte-for-byte; [Stepwise] searches one deduction per press on
 * [Stepwise.compute], off the main thread (A19).
 */
sealed interface HintMode {
    data object Legacy : HintMode

    data class Stepwise(val compute: CoroutineDispatcher) : HintMode
}
