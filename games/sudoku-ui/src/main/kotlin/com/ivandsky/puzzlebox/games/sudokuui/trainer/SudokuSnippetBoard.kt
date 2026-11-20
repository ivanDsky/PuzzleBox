package com.ivandsky.puzzlebox.games.sudokuui.trainer

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.min
import com.ivandsky.puzzlebox.designsystem.AppPopup
import com.ivandsky.puzzlebox.designsystem.LocalBoardGeometry
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.game.trainer.TrainerVerdict
import com.ivandsky.puzzlebox.games.sudoku.engine.trainer.SudokuMoveCheck
import com.ivandsky.puzzlebox.games.sudokuui.SudokuGrid
import com.ivandsky.puzzlebox.games.sudokuui.rememberGridDigits
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Test tag of the snippet board's square. */
internal const val SUDOKU_SNIPPET_BOARD_TAG = "sudoku.trainer.snippet"

/**
 * The board of one Mini Sudoku [snippet]: the live grid over the cut's board, filling the square it is given. The
 * player taps an empty cell and a [SudokuDigitPicker] opens in a popup under the square (above it when the window has
 * no room below); a digit is the one move ([SudokuSnippetBoardState.input]), the picker closes, and the move check
 * judges it on [compute], off the main thread, then [onVerdict] gets the verdict once; later taps do nothing. The move
 * stays drawn after the verdict. The board shows no Spotlight: the round's reveal is [SudokuTrainer.spotlight]'s, drawn
 * over this square by the round, so the grid is laid out to give the cells the pitch of that square.
 *
 * It also publishes its digit look to [SudokuSnippet.digits], which the reveal reads to draw its glyphs in the same style.
 */
@Composable
internal fun SudokuSnippetBoard(
    snippet: SudokuSnippet,
    compute: CoroutineDispatcher,
    onVerdict: (TrainerVerdict) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = remember(snippet) { SudokuSnippetBoardState(snippet) }
    val report by rememberUpdatedState(onVerdict)
    val scope = rememberCoroutineScope()
    val geometry = snippet.puzzle.geometry
    val frame = LocalBoardGeometry.current.wallWeight
    BoxWithConstraints(modifier.aspectRatio(1f).testTag(SUDOKU_SNIPPET_BOARD_TAG)) {
        val digits = rememberGridDigits(min(maxWidth, maxHeight), geometry.size)
        SideEffect { snippet.digits.digits = digits }
        SudokuGrid(state.model(), onSelect = state::select, Modifier.framed(frame))
        state.selected?.let { idx ->
            val gap = with(LocalDensity.current) { Spacing.sm.roundToPx() }
            AppPopup(
                popupPositionProvider = remember(idx, geometry.size, gap) { SudokuPickerPosition(idx, geometry.size, gap) },
                onDismissRequest = state::dismiss,
            ) {
                SudokuDigitPicker(
                    digits = state.keysAt(idx),
                    size = geometry.size,
                    onPick = { digit ->
                        state.input(digit)?.let { move ->
                            scope.launch {
                                val judged = withContext(compute) {
                                    SudokuMoveCheck.judge(snippet.puzzle, snippet.cut.boardBefore, snippet.cut.struckBefore, snippet.technique, move)
                                }
                                // Before the verdict: the round asks for the reveal as soon as it has it.
                                snippet.proving.step = judged.proof?.takeUnless { snippet.cutProves(move) }
                                report(judged.verdict)
                            }
                        }
                    },
                )
            }
        }
    }
}

/**
 * Lays the grid out [frame] larger on every side and centres it, so the cells (inside the grid's own frame of that
 * width) fill exactly the square this modifier is given, at the pitch of one sixth of it. The round draws the reveal
 * over that square at that pitch; a grid that took the frame out of the square would drift from it by up to
 * three quarters of the frame at the far cells. The frame itself draws over the edge of the square.
 */
private fun Modifier.framed(frame: Dp): Modifier = fillMaxSize().layout { measurable, constraints ->
    val side = minOf(constraints.maxWidth, constraints.maxHeight)
    val bleed = frame.roundToPx()
    val placeable = measurable.measure(Constraints.fixed(side + 2 * bleed, side + 2 * bleed))
    layout(side, side) { placeable.place(-bleed, -bleed) }
}
