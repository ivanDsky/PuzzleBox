package com.ivandsky.puzzlebox.games.queens.engine.hints.ladder

import com.ivandsky.puzzlebox.games.queens.QueensPuzzle

/**
 * Immutable per-cell ladder state: the [queens] standing on the board and the cells
 * [excluded] from holding one. A cell is open while it is neither.
 */
data class QueensCandidates(val size: Int, val queens: Set<Int>, val excluded: Set<Int>) {

    fun isOpen(cell: Int): Boolean = cell !in queens && cell !in excluded

    /** The queens [unit] still lacks: [QueensPuzzle.queensPerUnit] minus those already standing on it. */
    fun missing(puzzle: QueensPuzzle, unit: List<Int>): Int = puzzle.queensPerUnit - unit.count { it in queens }

    /**
     * Stands a queen on [cell] and excludes its 8 neighbours plus its row, column and
     * region once that unit holds [QueensPuzzle.queensPerUnit] queens (at once for N=1).
     */
    fun place(puzzle: QueensPuzzle, cell: Int): QueensCandidates {
        val queens = queens + cell
        val n = puzzle.size
        val row = cell / n
        val col = cell % n
        val region = puzzle.regions[cell]
        val closed = HashSet<Int>()
        for (r in maxOf(0, row - 1)..minOf(n - 1, row + 1)) {
            for (c in maxOf(0, col - 1)..minOf(n - 1, col + 1)) closed += r * n + c
        }
        // The unit counted `queens + cell`: one more than this state's queens, unless [cell] already stood.
        val added = if (cell in this.queens) 0 else 1
        fun closeIfFull(unit: List<Int>) {
            if (missing(puzzle, unit) <= added) closed += unit
        }
        closeIfFull(List(n) { row * n + it })
        closeIfFull(List(n) { it * n + col })
        closeIfFull(puzzle.regions.indices.filter { puzzle.regions[it] == region })
        return copy(queens = queens, excluded = excluded + closed - queens)
    }

    fun exclude(cells: Set<Int>): QueensCandidates = copy(excluded = excluded + (cells - queens))

    companion object {
        /** The state with [placed] standing (in ascending cell order) and nothing else known. */
        fun start(puzzle: QueensPuzzle, placed: Set<Int>): QueensCandidates =
            placed.sorted().fold(QueensCandidates(puzzle.size, emptySet(), emptySet())) { candidates, cell ->
                candidates.place(puzzle, cell)
            }
    }
}
