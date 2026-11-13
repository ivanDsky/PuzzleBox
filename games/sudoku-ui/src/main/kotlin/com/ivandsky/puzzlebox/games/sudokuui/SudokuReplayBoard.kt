package com.ivandsky.puzzlebox.games.sudokuui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.game.replay.ReplayFrame
import com.ivandsky.puzzlebox.gameui.replay.GameReplayBoard
import com.ivandsky.puzzlebox.gameui.replay.ReplayBoardOptions
import com.ivandsky.puzzlebox.games.sudoku.SudokuSession

/**
 * Sudoku's read-only replay board: the committed board and the marks live play derives from it
 * (digits, notes, revealed cells, conflicts), drawn by the live grid, with a hint badge on each
 * revealed cell still holding its digit. No selection, peer highlight or input. Conflicts show only while [ReplayBoardOptions.showErrors].
 * While the replay plays ([ReplayBoardOptions.animate]) a forward step plays live's pop, erase fade and unit waves;
 * paused, scrubbing, stepping back or under Reduce motion the board snaps to the frame.
 */
internal object SudokuReplayBoard : GameReplayBoard {
    override val pieces: String get() = "digits"

    @Composable
    override fun Board(frame: ReplayFrame, options: ReplayBoardOptions, modifier: Modifier) {
        // The frame is game-blind; this board is only reached through Sudoku's own plugin, so a miss draws nothing.
        val session = frame.session as? SudokuSession ?: return
        val showErrors = options.showErrors
        val hinted = frame.hinted
        val model = remember(session, showErrors, hinted) {
            val board = sudokuBoardModel(session, selected = null)
            val solution = session.puzzle.solution
            board.copy(
                cells = board.cells.map { cell ->
                    cell.copy(
                        conflict = cell.conflict && showErrors,
                        // A revealed cell is an entry holding the solution digit; edited since, it loses the badge.
                        hinted = cell.idx in hinted && !cell.given && cell.digit != 0 && cell.digit == solution[cell.idx],
                    )
                },
            )
        }
        val motion = rememberReplayMotion(session, options.animate)
        CompositionLocalProvider(LocalSudokuMotion provides motion) {
            SudokuGrid(model, onSelect = null, modifier = modifier)
        }
    }
}

/** Plays the live effect of each forward step from the frame shown before it; the first frame is the baseline and plays nothing. */
@Composable
private fun rememberReplayMotion(session: SudokuSession, animate: Boolean): SudokuMotionState {
    val motion = remember(session.board.geometry.cellCount) { SudokuMotionState(session.board.geometry.cellCount) }
    val scope = rememberCoroutineScope()
    val animating by rememberUpdatedState(animate)
    var previous by remember { mutableStateOf(session) }
    LaunchedEffect(session) {
        val event = sudokuPlayerEvent(previous, session)
        previous = session
        if (animating) scope.playSudokuEvent(motion, event, session.board.geometry)
    }
    return motion
}
