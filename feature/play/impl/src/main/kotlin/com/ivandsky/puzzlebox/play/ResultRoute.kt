package com.ivandsky.puzzlebox.play

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.designsystem.CompactTopBar
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.gameui.ResultPrimaryAction
import com.ivandsky.puzzlebox.gameui.ResultScreen
import com.ivandsky.puzzlebox.gameui.resultShareText
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.play.api.ResultKey
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The result screen's DI-bound entry point for [key]'s slot. The frozen facts ride the
 * key (process-death stable); the pill, the read facts and the write-failure retry come
 * from [ResultViewModel] — the record write is never awaited here (F2 §7).
 *
 * Up and Done both hand back [onDone] (a pop to the game's origin, SC4). "Play again"
 * hands back [onPlayAgain]: the app replaces this entry with the game, whose open resets
 * the solved live play to a fresh attempt. "Next: <game>" hands back [onNext] with the
 * next unsolved daily (A7, SC10). Share (A17) opens the system share sheet with the
 * plain [resultShareText] — no screen of our own.
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
    val context = LocalContext.current
    Column(modifier = modifier.testTag("route.result")) {
        CompactTopBar(
            // The result card already names the puzzle; the bar only carries Up.
            title = "",
            navigationIcon = {
                IconButton(onClick = onDone, modifier = Modifier.testTag("result.up")) {
                    Icon(
                        imageVector = PuzzleIcons.Back,
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
            onShare = {
                context.shareText(resultShareText(state.game, state.puzzleNumber, key.timeMs, key.hintsUsed))
            },
            onPlayAgain = onPlayAgain,
            brand = viewModel.brand,
            game = state.game,
            puzzleNumber = state.puzzleNumber,
            practice = state.practice,
            verdict = state.verdict,
            previousBest = state.previousBest,
            rankOrder = state.rankOrder,
            streak = state.streak,
            doneToday = state.doneToday,
            total = state.total,
            rolloverAt = state.rolloverAt,
            writeFailed = state.writeFailed,
            onRetryWrite = viewModel::retryRecordWrite,
        )
    }
}

/** Hands [text] to the system share sheet as plain text (ACTION_SEND). */
internal fun Context.shareText(text: String) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, text)
    startActivity(Intent.createChooser(send, null))
}
