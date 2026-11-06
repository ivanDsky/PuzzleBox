package com.ivandsky.puzzlebox.games.sudoku.render

import com.ivandsky.puzzlebox.game.Hint
import com.ivandsky.puzzlebox.game.RendererModel
import com.ivandsky.puzzlebox.games.sudoku.SudokuCell
import com.ivandsky.puzzlebox.games.sudoku.SudokuGameRules
import com.ivandsky.puzzlebox.games.sudoku.SudokuState
import com.ivandsky.puzzlebox.games.sudoku.digit
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.SudokuHint

/**
 * Plain drawable facts for a board, row-major: [digits] (0 = none), the [givens] cells,
 * and the visible [notes] masks (0 under an entry, whose notes stay hidden).
 */
data class SudokuRendererModel(
    val size: Int,
    val boxRows: Int,
    val boxCols: Int,
    val digits: List<Int>,
    val givens: Set<Int>,
    val notes: List<Int>,
    val hintCell: Int?,
    val status: Status,
) : RendererModel {

    enum class Status { IDLE, PLAYING, COMPLETE }

    companion object {
        fun of(state: SudokuState, hint: Hint?): SudokuRendererModel {
            val board = state.board
            val geometry = board.geometry
            val status = when {
                SudokuGameRules.isComplete(state) -> Status.COMPLETE
                board.cells.any { it !is SudokuCell.Given && it != SudokuCell.Empty } -> Status.PLAYING
                else -> Status.IDLE
            }
            return SudokuRendererModel(
                size = geometry.size,
                boxRows = geometry.boxRows,
                boxCols = geometry.boxCols,
                digits = board.cells.map { it.digit },
                givens = board.cells.indices.filterTo(mutableSetOf()) { board.cells[it] is SudokuCell.Given },
                notes = board.cells.map { (it as? SudokuCell.Notes)?.mask ?: 0 },
                hintCell = when (hint) {
                    is SudokuHint.Place -> hint.cell
                    is SudokuHint.Reveal -> hint.cell
                    else -> null
                },
                status = status,
            )
        }
    }
}
