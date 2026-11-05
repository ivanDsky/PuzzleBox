package com.ivandsky.puzzlebox.play

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.gameui.ResultScreen
import org.koin.androidx.compose.koinViewModel

/**
 * The result screen's DI-bound entry point (HomeRoute pattern). The frozen facts ride
 * the nav key (process-death stable); the record badge and the write-failure affordance
 * read the live play through [ResultViewModel] when one exists — the record write is
 * never awaited here, it completes async as today (F2 §7).
 *
 * "Play again" resets the session to a fresh play of the SAME puzzle via
 * [ResultViewModel.replay] before handing navigation back to the app callback, which
 * pops the result and pushes a fresh play entry over the store's live play.
 */
@Composable
fun ResultRoute(
    timeMs: Long,
    hintsUsed: Int,
    onPlayAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ResultViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    ResultScreen(
        timeMs = timeMs,
        hintsUsed = hintsUsed,
        onPlayAgain = {
            // Fresh session, same puzzle (A9) — before the entry swap, so the play
            // route finds the live play already reset.
            viewModel.replay()
            onPlayAgain()
        },
        modifier = modifier.testTag("route.result"),
        isRecord = state.isRecord,
        writeFailed = state.writeFailed,
        onRetryWrite = viewModel::retryRecordWrite,
        label = state.label,
    )
}
