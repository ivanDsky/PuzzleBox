package com.ivandsky.puzzlebox.gameui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.ivandsky.puzzlebox.designsystem.CelebrationMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Whether a solve rests on the board for [CelebrationMotion.SolveMomentMs] before the hand-off (M10-6), with no stamp.
 * Provided by the play route from the Result moments flag, for every game; off (no provider) leaves each game's
 * hand-off as it was.
 */
val LocalSolveRestOnBoard = staticCompositionLocalOf { false }

/**
 * The solve rest of one board: [begin] it the moment the solve lands and await it where the hand-off goes. It lasts
 * [CelebrationMotion.SolveMomentMs] from [begin], and [skip] (a tap on [SolveRestTapLayer]) ends it at once. Without
 * [enabled] it is over already. A pause, so reduced motion keeps it. The success buzz is Compose's `Confirm`; below API 30
 * androidx maps it to VIRTUAL_KEY, a lighter tap than the old LONG_PRESS.
 */
@Stable
class SolveRestState internal constructor(val enabled: Boolean, playFeel: Boolean, private val haptics: HapticFeedback) {
    /** Whether [begin] plays the success buzz: with the rest, and with PlayFeel even when the rest is off. */
    val confirms: Boolean = enabled || playFeel

    /** True while the board rests: the tap layer is up. */
    var resting by mutableStateOf(false)
        private set

    private val skips = Channel<Unit>(Channel.CONFLATED)

    /** Starts the rest in [scope] now, with the success buzz (L2) once, as the solve moment starts, through [HapticFeedback] so the Vibration switch silences it; await the result to wait out what is left of it. */
    fun begin(scope: CoroutineScope): Deferred<Unit> = scope.async {
        if (confirms) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        if (!enabled) return@async
        skips.tryReceive()
        resting = true
        try {
            withTimeoutOrNull(CelebrationMotion.SolveMomentMs) { skips.receive() }
        } finally {
            resting = false
        }
    }

    /** Ends the rest at once. */
    fun skip() {
        skips.trySend(Unit)
    }
}
