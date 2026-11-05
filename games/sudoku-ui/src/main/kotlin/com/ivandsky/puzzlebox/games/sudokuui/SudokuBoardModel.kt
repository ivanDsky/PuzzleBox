package com.ivandsky.puzzlebox.games.sudokuui

import com.ivandsky.puzzlebox.games.sudoku.SudokuCell
import com.ivandsky.puzzlebox.games.sudoku.SudokuSession
import com.ivandsky.puzzlebox.games.sudoku.digit
import com.ivandsky.puzzlebox.games.sudoku.engine.rules.SudokuConflicts

/** The grid's display state: one [SudokuCellModel] per cell, row-major. */
data class SudokuBoardModel(val size: Int, val boxRows: Int, val boxCols: Int, val cells: List<SudokuCellModel>)

/**
 * Flattens [session] into paint decisions. Peers and same-digit cells exclude the selection
 * itself, whose own tint wins. [hintTarget], [hintCells] and [hintStruck] come pre-mapped
 * from the hint.
 */
fun sudokuBoardModel(
    session: SudokuSession,
    selected: Int?,
    hintTarget: Int? = null,
    hintCells: Set<Int> = emptySet(),
    hintStruck: Set<Int> = emptySet(),
): SudokuBoardModel {
    val board = session.board
    val geometry = board.geometry
    val peers = BooleanArray(geometry.cellCount)
    selected?.let { for (peer in geometry.peersOf(it)) peers[peer] = true }
    val selectedDigit = selected?.let(board::digitAt) ?: 0
    val conflicts = SudokuConflicts.cellsIn(SudokuConflicts.find(board))
    val cells = board.cells.mapIndexed { idx, cell ->
        SudokuCellModel(
            idx = idx,
            digit = cell.digit,
            notes = (cell as? SudokuCell.Notes)?.mask ?: 0,
            given = cell is SudokuCell.Given,
            selected = idx == selected,
            peer = peers[idx],
            sameDigit = selectedDigit != 0 && idx != selected && cell.digit == selectedDigit,
            conflict = idx in conflicts,
            hintTarget = idx == hintTarget,
            hintUnit = idx in hintCells,
            hintStruck = idx in hintStruck,
        )
    }
    return SudokuBoardModel(geometry.size, geometry.boxRows, geometry.boxCols, cells)
}
