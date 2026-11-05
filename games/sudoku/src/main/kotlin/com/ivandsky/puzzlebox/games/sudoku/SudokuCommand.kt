package com.ivandsky.puzzlebox.games.sudoku

import com.ivandsky.puzzlebox.game.Command

/**
 * Reversible board edits. Each command stores only the delta its own revert needs, never a
 * board copy, so history costs one small value per action. Build them with [SudokuMoves].
 */
sealed interface SudokuCommand : Command<SudokuBoard> {

    /**
     * Enters [digit] at [idx] over [before], keeping [before]'s notes hidden under the entry,
     * and clears the digit's note from [clearedPeers] in the same undo unit.
     * [clearedPeers] lists, ascending, exactly the peers whose notes held the digit, which is
     * what makes [revert] restore them bit for bit.
     */
    data class Place(
        val idx: Int,
        val digit: Int,
        val before: SudokuCell,
        val clearedPeers: List<Int>,
    ) : SudokuCommand {

        init {
            require(before !is SudokuCell.Given) { "a given never changes" }
            require(digit >= 1) { "place needs a digit, got $digit" }
        }

        override fun apply(state: SudokuBoard): SudokuBoard {
            val bit = noteBit(digit)
            val cells = state.cells.toMutableList()
            cells[idx] = SudokuCell.Entry(digit, before.notesMask)
            for (peer in clearedPeers) cells[peer] = cells[peer].withoutNote(bit)
            return state.copy(cells = cells)
        }

        override fun revert(state: SudokuBoard): SudokuBoard {
            val bit = noteBit(digit)
            val cells = state.cells.toMutableList()
            cells[idx] = before
            for (peer in clearedPeers) cells[peer] = cells[peer].withNote(bit)
            return state.copy(cells = cells)
        }
    }
}

private fun noteBit(digit: Int): Int = 1 shl (digit - 1)

private fun SudokuCell.withoutNote(bit: Int): SudokuCell = when (this) {
    is SudokuCell.Notes -> (mask and bit.inv()).let { if (it == 0) SudokuCell.Empty else SudokuCell.Notes(it) }
    is SudokuCell.Entry -> copy(notes = notes and bit.inv())
    is SudokuCell.Given, SudokuCell.Empty -> this
}

private fun SudokuCell.withNote(bit: Int): SudokuCell = when (this) {
    SudokuCell.Empty -> SudokuCell.Notes(bit)
    is SudokuCell.Notes -> SudokuCell.Notes(mask or bit)
    is SudokuCell.Entry -> copy(notes = notes or bit)
    is SudokuCell.Given -> this
}
