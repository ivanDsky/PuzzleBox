package com.ivandsky.puzzlebox.play

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.koin.compose.koinInject

/**
 * The Queens result screen's DI-bound entry point ([ResultRoute] pattern, fix round 6
 * R-5: the win presentation is Zip's opaque result ROUTE — the modal overlay is
 * deleted). The frozen facts (elapsed time, hints used) ride the nav key
 * (process-death stable); the record badge and the write-failure affordance read the
 * live [QueensPlayFlow] state when it exists — the record write is never awaited here,
 * it completes async in the flow scope.
 *
 * "Play again" resets the session to a fresh play of the SAME puzzle via
 * [QueensPlayState.replay] before handing navigation back to the app callback, which
 * pops the result and pushes a fresh play entry over the flow's live state.
 */
@Composable
fun QueensResultRoute(
    timeMs: Long,
    hintsUsed: Int,
    onPlayAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val flow = koinInject<QueensPlayFlow>()
    val state = flow.state
    ResultScreen(
        timeMs = timeMs,
        hintsUsed = hintsUsed,
        onPlayAgain = {
            // Fresh session, same puzzle (A9) — before the entry swap, so the play
            // route finds the live flow state already reset.
            state?.replay()
            onPlayAgain()
        },
        modifier = modifier,
        isRecord = state?.recordBadge,
        writeFailed = state?.recordWriteFailed == true,
        onRetryWrite = { state?.retryRecordWrite() },
    )
}
