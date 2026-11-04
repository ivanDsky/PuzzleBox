package com.ivandsky.puzzlebox.play

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.gameui.ResultScreen
import org.koin.compose.koinInject

/**
 * The result screen's DI-bound entry point (PlayRoute pattern). The frozen facts ride
 * the nav key (process-death stable); the record badge and the write-failure affordance
 * read the live [PlayFlow] state when it exists — the record write is never awaited
 * here, it completes async as today (F2 §7).
 *
 * "Play again" resets the session to a fresh play of the SAME puzzle via
 * [ActivePlay.replay] before handing navigation back to the app callback, which pops
 * the result and pushes a fresh play entry over the flow's live state.
 */
@Composable
fun ResultRoute(
    timeMs: Long,
    hintsUsed: Int,
    onPlayAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val flow = koinInject<PlayFlow>()
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
        label = state?.resultLabel,
    )
}
