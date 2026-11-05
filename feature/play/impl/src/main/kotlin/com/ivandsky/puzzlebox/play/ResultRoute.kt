package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.designsystem.CompactTopBar
import com.ivandsky.puzzlebox.gameui.ResultPrimaryAction
import com.ivandsky.puzzlebox.gameui.ResultScreen
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.play.api.ResultKey
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The result screen's DI-bound entry point for [key]'s slot. The frozen facts ride the
 * key (process-death stable); the badge and the write-failure retry come from
 * [ResultViewModel] — the record write is never awaited here (F2 §7).
 *
 * Up and Done both hand back [onDone] (a pop to the game's origin, SC4). "Play again"
 * hands back [onPlayAgain]: the app replaces this entry with the game, whose open resets
 * the solved live play to a fresh attempt. "Next: <game>" hands back [onNext] with the
 * next unsolved daily (A7, SC10).
 */
@Composable
fun ResultRoute(
    key: ResultKey,
    onPlayAgain: () -> Unit,
    onNext: (GameId) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ResultViewModel = koinViewModel(key = key.toString(), parameters = { parametersOf(key) })
    val state by viewModel.state.collectAsStateWithLifecycle()
    val primary by viewModel.primary.collectAsStateWithLifecycle()
    Column(modifier = modifier.testTag("route.result")) {
        CompactTopBar(
            // The result body already names the puzzle; the bar only carries Up.
            title = "",
            navigationIcon = {
                IconButton(onClick = onDone, modifier = Modifier.testTag("result.up")) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
        ResultScreen(
            timeMs = key.timeMs,
            hintsUsed = key.hintsUsed,
            primary = primary,
            onPrimary = {
                when (val action = primary) {
                    is ResultPrimaryAction.Next -> onNext(action.gameId)
                    ResultPrimaryAction.AllDone, ResultPrimaryAction.Done -> onDone()
                    null -> Unit
                }
            },
            onPlayAgain = onPlayAgain,
            verdict = state.verdict,
            previousBestMs = state.previousBestMs,
            writeFailed = state.writeFailed,
            onRetryWrite = viewModel::retryRecordWrite,
            label = state.label,
        )
    }
}
