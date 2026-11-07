package com.ivandsky.puzzlebox.games.sudoku.editor

/**
 * An editable Sudoku board: [size] × [size] cells (6 or 9), [givens] row-major with
 * `0` for an empty cell. Partial boards are valid drafts; only analysis asks for a solvable one.
 */
data class SudokuDraft(val size: Int, val givens: List<Int>) {
    init {
        require(size in SIZES) { "size $size not one of $SIZES" }
        require(givens.size == size * size) { "expected ${size * size} cells, got ${givens.size}" }
        val bad = givens.firstOrNull { it !in 0..size }
        require(bad == null) { "digit $bad outside 0..$size" }
    }

    companion object {
        val SIZES: Set<Int> = setOf(6, 9)
    }
}
