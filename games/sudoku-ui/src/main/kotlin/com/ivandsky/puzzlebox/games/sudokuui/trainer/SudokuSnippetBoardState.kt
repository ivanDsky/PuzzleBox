package com.ivandsky.puzzlebox.games.sudokuui.trainer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ivandsky.puzzlebox.games.sudoku.SudokuCell
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.SudokuCandidates
import com.ivandsky.puzzlebox.games.sudoku.engine.trainer.SudokuTrainerMove
import com.ivandsky.puzzlebox.games.sudokuui.SudokuBoardModel
import com.ivandsky.puzzlebox.games.sudokuui.SudokuCellModel

/**
 * The state of one snippet board: the cut's board as drawn, the cell the player picked, and the one move they make.
 * It is its own state, never the play screen's `SudokuBoardState`: no notes toggle, no undo, no peer tint, no win.
 * The board is only ever drawn; the verdict is the move check's, not the board's.
 *
 * One input flow for both families (ledger ruling): [select] an empty cell, then [input] a digit. A snippet for a
 * single draws no notes and any digit is a [SudokuTrainerMove.Place]; a snippet for an elimination draws the engine's
 * candidates for the cut ([SudokuCandidates.of], minus what earlier eliminations of the walk struck) as notes, the
 * very set the move check reads, and only one of the picked cell's shown notes is a
 * [SudokuTrainerMove.RemoveNote], so the digit a note does not show does nothing.
 */
internal class SudokuSnippetBoardState(private val snippet: SudokuSnippet) {

    private val size = snippet.puzzle.geometry.size
    private val cellCount = snippet.puzzle.geometry.cellCount
    private val before = snippet.cut.boardBefore

    /** The digit each cell shows as drawn: the cut's own, plus the player's placement once made. */
    var digits: List<Int> by mutableStateOf(List(cellCount) { before.digitAt(it) })
        private set

    /** The notes each cell shows as drawn (bit `d - 1` for digit `d`): none for a single, the shown candidates for an elimination. */
    var notes: List<Int> by mutableStateOf(
        if (snippet.placesDigit) List(cellCount) { 0 } else shownCandidates(snippet),
    )
        private set

    /** The cell the digit picker is open on, null while it is closed. */
    var selected: Int? by mutableStateOf(null)
        private set

    /** The player's move, null until one is made. */
    var played: SudokuTrainerMove? = null
        private set

    /** The digits the picker offers for the cell [idx]: every digit for a single, the cell's shown notes for an elimination. */
    fun keysAt(idx: Int): List<Int> =
        if (snippet.placesDigit) (1..size).toList() else (1..size).filter { notes[idx] and (1 shl (it - 1)) != 0 }

    /**
     * Opens the picker on the empty cell [idx]. Nothing happens once the move is made, for a cell off the board, a given
     * or an already filled one, or one with no note to strike.
     */
    fun select(idx: Int) {
        if (played != null || idx !in 0 until cellCount) return
        if (before.cells[idx] is SudokuCell.Given || digits[idx] != 0 || keysAt(idx).isEmpty()) return
        selected = idx
    }

    /** Closes the picker without a move. */
    fun dismiss() {
        selected = null
    }

    /**
     * The player's move for [digit] on the selected cell, now drawn, and the picker closed; null when no move is made:
     * no cell is selected, one was made already, or [digit] is not among [keysAt] the cell.
     */
    fun input(digit: Int): SudokuTrainerMove? {
        val idx = selected ?: return null
        if (played != null || digit !in keysAt(idx)) return null
        val move = if (snippet.placesDigit) SudokuTrainerMove.Place(idx, digit) else SudokuTrainerMove.RemoveNote(idx, digit)
        when (move) {
            is SudokuTrainerMove.Place -> digits = digits.toMutableList().also { it[idx] = digit }
            is SudokuTrainerMove.RemoveNote -> notes = notes.toMutableList().also { it[idx] = it[idx] and (1 shl (digit - 1)).inv() }
        }
        played = move
        selected = null
        return move
    }

    /** What the grid draws now: a filled cell hides its notes, the picked cell is the selected one. No other tint. */
    fun model(): SudokuBoardModel {
        val geometry = snippet.puzzle.geometry
        val cells = List(cellCount) { idx ->
            SudokuCellModel(
                idx = idx,
                digit = digits[idx],
                notes = if (digits[idx] == 0) notes[idx] else 0,
                given = before.cells[idx] is SudokuCell.Given,
                selected = idx == selected,
                peer = false,
                sameDigit = false,
                conflict = false,
                hintTarget = false,
                hintUnit = false,
                hintStruck = false,
            )
        }
        return SudokuBoardModel(geometry.size, geometry.boxRows, geometry.boxCols, cells)
    }
}

/** The engine's candidates for the cut's board, less the notes [com.ivandsky.puzzlebox.games.sudoku.engine.trainer.SudokuSnippetCut.struckBefore] took out. */
private fun shownCandidates(snippet: SudokuSnippet): List<Int> {
    val candidates = SudokuCandidates.of(snippet.puzzle, snippet.cut.boardBefore)
    for ((cell, mask) in snippet.cut.struckBefore) candidates[cell] = candidates[cell] and mask.inv()
    return candidates.toList()
}
