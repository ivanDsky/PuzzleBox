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
 * (digits, notes, revealed cells, conflicts), drawn by the live grid. No selection, peer
 * highlight, hint or input. Conflicts show only while [ReplayBoardOptions.showErrors].
 */
internal object SudokuReplayBoard : GameReplayBoard {
    override val pieces: String get() = "digits"

    @Composable
    override fun Board(frame: ReplayFrame, options: ReplayBoardOptions, modifier: Modifier) {
        // The frame is game-blind; this board is only reached through Sudoku's own plugin, so a miss draws nothing.
        val session = frame.session as? SudokuSession ?: return
        val showErrors = options.showErrors
        val model = remember(session, showErrors) {
            sudokuBoardModel(session, selected = null).let { board ->
                if (showErrors) board else board.copy(cells = board.cells.map { if (it.conflict) it.copy(conflict = false) else it })
            }
        }
        SudokuGrid(model, onSelect = null, modifier = modifier)
    }
}
