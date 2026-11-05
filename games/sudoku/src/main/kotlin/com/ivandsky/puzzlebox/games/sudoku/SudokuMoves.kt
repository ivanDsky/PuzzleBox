package com.ivandsky.puzzlebox.games.sudoku

/** Builds commands against the current board; null means the action would change nothing. */
object SudokuMoves {

    /** Null on a Given, on the digit the cell already holds, and on an out-of-range idx or digit. */
    fun place(board: SudokuBoard, idx: Int, digit: Int): SudokuCommand.Place? {
        val geometry = board.geometry
        if (idx !in 0 until geometry.cellCount || digit !in 1..geometry.size) return null
        val before = board.cells[idx]
        if (before is SudokuCell.Given || before.digit == digit) return null
        val bit = 1 shl (digit - 1)
        val clearedPeers = geometry.peersOf(idx).filter { board.cells[it].notesMask and bit != 0 }
        return SudokuCommand.Place(idx, digit, before, clearedPeers)
    }
}
