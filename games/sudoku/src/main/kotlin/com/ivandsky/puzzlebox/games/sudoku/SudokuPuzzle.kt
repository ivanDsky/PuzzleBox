package com.ivandsky.puzzlebox.games.sudoku

/**
 * One board: [givens] holds a digit per cell (0 = open) and [solution] the full published
 * grid, both row-major. Only the shape is enforced here; whether the givens solve uniquely to
 * [solution] is the feed gate's call, so tests can still build untrusted boards.
 */
data class SudokuPuzzle(val variant: SudokuVariant, val givens: List<Int>, val solution: List<Int>) {

    val geometry: SudokuGeometry get() = variant.geometry

    init {
        val cells = variant.geometry.cellCount
        val size = variant.geometry.size
        require(givens.size == cells) { "givens must cover $cells cells, got ${givens.size}" }
        require(solution.size == cells) { "solution must cover $cells cells, got ${solution.size}" }
        require(givens.all { it in 0..size }) { "given digit outside 0..$size" }
        require(solution.all { it in 1..size }) { "solution digit outside 1..$size" }
    }
}
