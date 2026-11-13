package com.ivandsky.puzzlebox.games.sudokuui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.game.replay.ReplayFrame
import com.ivandsky.puzzlebox.gameui.replay.GameReplayBoard
import com.ivandsky.puzzlebox.gameui.replay.ReplayBoardOptions
import com.ivandsky.puzzlebox.games.sudoku.SudokuSession

/**
 * Sudoku's read-only replay board: the committed board and the marks live play derives from it
 * (digits, notes, revealed cells, conflicts), drawn by the live grid, with a hint badge on each
 * revealed cell still holding its digit. No selection, peer highlight or input. Conflicts show only while [ReplayBoardOptions.showErrors].
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
        SudokuGrid(model, onSelect = null, modifier = modifier)
    }
}
