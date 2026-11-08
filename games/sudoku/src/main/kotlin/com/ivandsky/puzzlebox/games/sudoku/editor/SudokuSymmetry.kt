package com.ivandsky.puzzlebox.games.sudoku.editor

/**
 * How the editor pairs cells across the board. A help while placing givens, never a rule;
 * default Off.
 */
enum class SudokuSymmetry {
    Off,
    Rotational,
    Mirror,
    ;

    /**
     * The cell paired with [cell] on a [size] × [size] board; null when Off or [cell] is its
     * own pair.
     */
    fun mirrorOf(cell: Int, size: Int): Int? {
        val other = when (this) {
            Off -> return null
            Rotational -> size * size - 1 - cell
            Mirror -> cell - cell % size + (size - 1 - cell % size)
        }
        return other.takeIf { it != cell }
    }

    /** Empty cells of [draft] whose pair holds a given. */
    fun unpaired(draft: SudokuDraft): Set<Int> =
        draft.givens.indices.filterTo(mutableSetOf()) { cell ->
            draft.givens[cell] == 0 && mirrorOf(cell, draft.size)?.let { draft.givens[it] != 0 } == true
        }
}
