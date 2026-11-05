package com.ivandsky.puzzlebox.games.sudoku

/** Builds commands against the current board; null means the action would change nothing. */
object SudokuMoves {

    /** Null on a Given, on the digit the cell already holds, and on an out-of-range idx or digit. */
    fun place(board: SudokuBoard, idx: Int, digit: Int): SudokuCommand.Place? {
        if (!canEnter(board, idx, digit)) return null
        return SudokuCommand.Place(idx, digit, board.cells[idx], peersNoting(board, idx, digit))
    }

    /** Null on an Empty or Given cell and on an out-of-range idx. */
    fun erase(board: SudokuBoard, idx: Int): SudokuCommand.Erase? {
        if (idx !in 0 until board.geometry.cellCount) return null
        val before = board.cells[idx]
        if (before !is SudokuCell.Entry && before !is SudokuCell.Notes) return null
        return SudokuCommand.Erase(idx, before)
    }

    /** Null unless the cell is Empty or Notes and [digit] is in range. */
    fun toggleNote(board: SudokuBoard, idx: Int, digit: Int): SudokuCommand.ToggleNote? {
        if (idx !in 0 until board.geometry.cellCount || digit !in 1..board.geometry.size) return null
        val cell = board.cells[idx]
        if (cell != SudokuCell.Empty && cell !is SudokuCell.Notes) return null
        return SudokuCommand.ToggleNote(idx, digit)
    }

    /** Empties every Entry and Notes cell, hidden notes included; null when the board has none. */
    fun clear(board: SudokuBoard): SudokuCommand.Clear? {
        val before = LinkedHashMap<Int, SudokuCell>()
        board.cells.forEachIndexed { idx, cell ->
            if (cell is SudokuCell.Entry || cell is SudokuCell.Notes) before[idx] = cell
        }
        return if (before.isEmpty()) null else SudokuCommand.Clear(before)
    }

    /** Same rules as [place]; a wrong Entry is overwritten with [digit] like any other entry. */
    fun reveal(board: SudokuBoard, idx: Int, digit: Int): SudokuCommand.HintReveal? {
        if (!canEnter(board, idx, digit)) return null
        return SudokuCommand.HintReveal(idx, digit, board.cells[idx], peersNoting(board, idx, digit))
    }

    private fun canEnter(board: SudokuBoard, idx: Int, digit: Int): Boolean {
        val geometry = board.geometry
        if (idx !in 0 until geometry.cellCount || digit !in 1..geometry.size) return false
        val before = board.cells[idx]
        return before !is SudokuCell.Given && before.digit != digit
    }

    private fun peersNoting(board: SudokuBoard, idx: Int, digit: Int): List<Int> {
        val bit = 1 shl (digit - 1)
        return board.geometry.peersOf(idx).filter { board.cells[it].notesMask and bit != 0 }
    }
}
