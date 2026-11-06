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

        override fun apply(state: SudokuBoard): SudokuBoard = placeDigit(state, idx, digit, before, clearedPeers)

        override fun revert(state: SudokuBoard): SudokuBoard = unplaceDigit(state, idx, digit, before, clearedPeers)
    }

    /** Removes what [before] shows at [idx]: an entry falls back to its hidden notes, notes go away. */
    data class Erase(val idx: Int, val before: SudokuCell) : SudokuCommand {

        init {
            require(before is SudokuCell.Entry || before is SudokuCell.Notes) { "nothing to erase in $before" }
        }

        override fun apply(state: SudokuBoard): SudokuBoard = state.with(idx, erased(before))

        override fun revert(state: SudokuBoard): SudokuBoard = state.with(idx, before)
    }

    /** Flips the note for [digit] at an open cell; flipping twice is the identity, so it needs no snapshot. */
    data class ToggleNote(val idx: Int, val digit: Int) : SudokuCommand {

        init {
            require(digit >= 1) { "a note needs a digit, got $digit" }
        }

        override fun apply(state: SudokuBoard): SudokuBoard = state.with(idx, state.cells[idx].toggledNote(noteBit(digit)))

        override fun revert(state: SudokuBoard): SudokuBoard = apply(state)
    }

    /** Empties every cell in [before] at once, so one undo brings the whole board back. */
    data class Clear(val before: Map<Int, SudokuCell>) : SudokuCommand {

        init {
            require(before.values.none { it is SudokuCell.Given || it == SudokuCell.Empty }) {
                "clear only records cells it empties"
            }
        }

        override fun apply(state: SudokuBoard): SudokuBoard {
            val cells = state.cells.toMutableList()
            for (idx in before.keys) cells[idx] = SudokuCell.Empty
            return state.copy(cells = cells)
        }

        override fun revert(state: SudokuBoard): SudokuBoard {
            val cells = state.cells.toMutableList()
            for ((idx, cell) in before) cells[idx] = cell
            return state.copy(cells = cells)
        }
    }

    /**
     * A hint's solution digit entered at [idx]; the board edit is exactly [Place]'s. It is a
     * separate kind so the session can tell a revealed digit from a played one.
     */
    data class HintReveal(
        val idx: Int,
        val digit: Int,
        val before: SudokuCell,
        val clearedPeers: List<Int>,
    ) : SudokuCommand {

        init {
            require(before !is SudokuCell.Given) { "a given never changes" }
            require(digit >= 1) { "reveal needs a digit, got $digit" }
        }

        override fun apply(state: SudokuBoard): SudokuBoard = placeDigit(state, idx, digit, before, clearedPeers)

        override fun revert(state: SudokuBoard): SudokuBoard = unplaceDigit(state, idx, digit, before, clearedPeers)
    }
}

private fun noteBit(digit: Int): Int = 1 shl (digit - 1)

private fun placeDigit(state: SudokuBoard, idx: Int, digit: Int, before: SudokuCell, clearedPeers: List<Int>): SudokuBoard {
    val bit = noteBit(digit)
    val cells = state.cells.toMutableList()
    cells[idx] = SudokuCell.Entry(digit, before.notesMask)
    for (peer in clearedPeers) cells[peer] = cells[peer].withoutNote(bit)
    return state.copy(cells = cells)
}

private fun unplaceDigit(state: SudokuBoard, idx: Int, digit: Int, before: SudokuCell, clearedPeers: List<Int>): SudokuBoard {
    val bit = noteBit(digit)
    val cells = state.cells.toMutableList()
    cells[idx] = before
    for (peer in clearedPeers) cells[peer] = cells[peer].withNote(bit)
    return state.copy(cells = cells)
}

private fun erased(cell: SudokuCell): SudokuCell =
    if (cell is SudokuCell.Entry && cell.notes != 0) SudokuCell.Notes(cell.notes) else SudokuCell.Empty

private fun notesOrEmpty(mask: Int): SudokuCell = if (mask == 0) SudokuCell.Empty else SudokuCell.Notes(mask)

private fun SudokuCell.toggledNote(bit: Int): SudokuCell = when (this) {
    SudokuCell.Empty -> SudokuCell.Notes(bit)
    is SudokuCell.Notes -> notesOrEmpty(mask xor bit)
    is SudokuCell.Given, is SudokuCell.Entry -> error("notes toggle only on open cells, got $this")
}

private fun SudokuCell.withoutNote(bit: Int): SudokuCell = when (this) {
    is SudokuCell.Notes -> notesOrEmpty(mask and bit.inv())
    is SudokuCell.Entry -> copy(notes = notes and bit.inv())
    is SudokuCell.Given, SudokuCell.Empty -> this
}

private fun SudokuCell.withNote(bit: Int): SudokuCell = when (this) {
    SudokuCell.Empty -> SudokuCell.Notes(bit)
    is SudokuCell.Notes -> SudokuCell.Notes(mask or bit)
    is SudokuCell.Entry -> copy(notes = notes or bit)
    is SudokuCell.Given -> this
}
