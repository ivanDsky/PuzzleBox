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
 * Whether a game's own board rests on a solve for [CelebrationMotion.SolveMomentMs] before the hand-off (M10-6), with no
 * stamp. The play chrome provides it for every game's play screen and the Studio's test solve; a board drawn outside
 * it (the Library's lessons) does not rest on its own.
 */
val LocalSolveRestOnBoard = staticCompositionLocalOf { false }

/**
 * True under a host that plays the solve's success buzz itself (the tutorial host): a rest begun below
 * it stays silent, so a board that begins its own rest never buzzes a second time.
 */
internal val LocalSolveConfirmHosted = staticCompositionLocalOf { false }

/**
 * The solve rest of one board: [begin] it the moment the solve lands and await it where the hand-off goes. It lasts
 * [CelebrationMotion.SolveMomentMs] from [begin], and [skip] (a tap on [SolveRestTapLayer]) ends it at once. Without
 * [enabled] it is over already. A pause, so reduced motion keeps it. The success buzz is Compose's `Confirm`; below API 30
 * androidx maps it to VIRTUAL_KEY, a lighter tap than the old LONG_PRESS. With [hostConfirms] the host above plays it.
 */
@Stable
class SolveRestState internal constructor(
    val enabled: Boolean,
    private val haptics: HapticFeedback,
    private val hostConfirms: Boolean = false,
) {
    /** True while the board rests: the tap layer is up. */
    var resting by mutableStateOf(false)
        private set

    private val skips = Channel<Unit>(Channel.CONFLATED)

    /** Starts the rest in [scope] now, with the success buzz (L2) once, as the solve moment starts, through [HapticFeedback] so the Vibration switch silences it; await the result to wait out what is left of it. */
    fun begin(scope: CoroutineScope): Deferred<Unit> = scope.async {
        if (!hostConfirms) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
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
