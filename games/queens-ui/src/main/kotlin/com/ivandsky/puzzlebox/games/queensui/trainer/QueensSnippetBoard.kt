package com.ivandsky.puzzlebox.games.queensui.trainer

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.game.trainer.TrainerVerdict
import com.ivandsky.puzzlebox.games.queens.engine.trainer.QueensMoveCheck
import com.ivandsky.puzzlebox.games.queensui.LocalQueensReducedMotion
import com.ivandsky.puzzlebox.games.queensui.QueensBoardFace
import com.ivandsky.puzzlebox.games.queensui.QueensBoardModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Test tag of the snippet board's touch surface. */
internal const val QUEENS_SNIPPET_BOARD_TAG = "queens.trainer.snippet"

/**
 * The board of one Queens [snippet]: the live board's face over the cut's board, crosses a queen forces drawn
 * (A11), and a touch surface that takes the player's one move ([QueensSnippetBoardState.tap]). The move check
 * judges it on [compute] (a long look-ahead takes up to 90 ms), then [onVerdict] gets the verdict once; later
 * taps do nothing. The move stays drawn after the verdict.
 */
@Composable
internal fun QueensSnippetBoard(
    snippet: QueensSnippet,
    compute: CoroutineDispatcher,
    onVerdict: (TrainerVerdict) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = remember(snippet) { QueensSnippetBoardState(snippet) }
    val report by rememberUpdatedState(onVerdict)
    val scope = rememberCoroutineScope()
    val n = snippet.puzzle.size
    CompositionLocalProvider(LocalQueensReducedMotion provides rememberReducedMotion()) {
        Box(
            modifier
                .aspectRatio(1f)
                .testTag(QUEENS_SNIPPET_BOARD_TAG)
                .pointerInput(state, n) {
                    detectTapGestures { offset ->
                        // Edge-clamped, as the play board resolves a touch to a square.
                        val col = (offset.x / size.width * n).toInt().coerceIn(0, n - 1)
                        val row = (offset.y / size.height * n).toInt().coerceIn(0, n - 1)
                        val move = state.tap(row * n + col) ?: return@detectTapGestures
                        scope.launch {
                            val verdict = withContext(compute) {
                                QueensMoveCheck.classify(snippet.puzzle, snippet.cut.boardBefore, snippet.technique, move)
                            }
                            report(verdict)
                        }
                    }
                },
        ) {
            QueensBoardFace(QueensBoardModel.of(state.session), Modifier.matchParentSize())
        }
    }
}
