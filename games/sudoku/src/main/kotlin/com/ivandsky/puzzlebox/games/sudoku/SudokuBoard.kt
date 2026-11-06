package com.ivandsky.puzzlebox.games.sudoku

/** The player-edited board: one [SudokuCell] per index, immutable. */
data class SudokuBoard(val geometry: SudokuGeometry, val cells: List<SudokuCell>) {

    init {
        require(cells.size == geometry.cellCount) { "board needs ${geometry.cellCount} cells, got ${cells.size}" }
    }

    fun digitAt(idx: Int): Int = cells[idx].digit

    val isFull: Boolean get() = cells.all { it.digit != 0 }

    /** Given and Entry cells holding [digit]. */
    fun placedCount(digit: Int): Int = cells.count { it.digit == digit }

    fun with(idx: Int, cell: SudokuCell): SudokuBoard =
        copy(cells = cells.toMutableList().also { it[idx] = cell })

    companion object {
        fun of(puzzle: SudokuPuzzle): SudokuBoard = SudokuBoard(
            geometry = puzzle.geometry,
            cells = puzzle.givens.map { if (it == 0) SudokuCell.Empty else SudokuCell.Given(it) },
        )
    }
}
