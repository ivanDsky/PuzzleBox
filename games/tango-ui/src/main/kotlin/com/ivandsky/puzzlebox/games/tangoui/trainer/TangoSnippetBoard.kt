package com.ivandsky.puzzlebox.games.tangoui.trainer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.game.trainer.TrainerVerdict
import com.ivandsky.puzzlebox.games.tango.engine.TangoState
import com.ivandsky.puzzlebox.games.tango.engine.trainer.TangoMoveCheck
import com.ivandsky.puzzlebox.games.tango.render.TangoRendererModel
import com.ivandsky.puzzlebox.games.tangoui.TangoBoardRenderer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Test tag of the snippet board's grid. */
internal const val TANGO_SNIPPET_BOARD_TAG = "tango.trainer.snippet"

/**
 * The board of one Tango [snippet]: the live board's renderer over the cut's grid and a tap that takes the player's one
 * move ([TangoSnippetBoardState.tap]). The move check judges it on [compute], off the main thread,
 * then [onVerdict] gets the verdict once; later taps do nothing. The move stays drawn after the verdict, and a move
 * that breaks a rule wears the renderer's own violation tint. The board shows no Spotlight: the round's reveal is
 * [TangoTrainer.spotlight]'s.
 */
@Composable
internal fun TangoSnippetBoard(
    snippet: TangoSnippet,
    compute: CoroutineDispatcher,
    onVerdict: (TrainerVerdict) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = remember(snippet) { TangoSnippetBoardState(snippet) }
    val report by rememberUpdatedState(onVerdict)
    val scope = rememberCoroutineScope()
    // The renderer keeps the first tap lambda it is given, so a new snippet gets a new renderer.
    val renderer = remember(state) {
        TangoBoardRenderer(
            onTap = { idx ->
                state.tap(idx)?.let { move ->
                    scope.launch {
                        val judged = withContext(compute) {
                            TangoMoveCheck.judge(
                                snippet.puzzle, snippet.solution, snippet.cut.gridBefore, snippet.technique, move,
                            )
                        }
                        // Before the verdict: the round asks for the reveal as soon as it has it.
                        snippet.proving.step = judged.proof?.takeUnless { snippet.cutProves(move) }
                        report(judged.verdict)
                    }
                }
            },
        )
    }
    val model = TangoRendererModel.of(TangoState(snippet.puzzle, state.grid))
    renderer.Content(model, modifier.testTag(TANGO_SNIPPET_BOARD_TAG))
}
