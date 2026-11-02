package com.ivandsky.puzzlebox.games.queens.engine.rules

import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle

/**
 * Linear verification over the board (zip ZipVerifier precedent): the shared math the
 * rules facade, the solver/generator (queens-03), and the fetch-time uniqueness gate
 * (A13) all read. Stateless; N-independent adjacency (research 01 §2).
 */
object QueensVerifier {

    /** True iff [a] and [b] are neighboring cells — the eight touching cells only. */
    fun areNeighbors(a: Cell, b: Cell, size: Int): Boolean {
        val dr = kotlin.math.abs(a.idx / size - b.idx / size)
        val dc = kotlin.math.abs(a.idx % size - b.idx % size)
        return dr <= 1 && dc <= 1 && (dr + dc > 0)
    }

    /**
     * A13 oracle: [queens] is a VALID solution of [puzzle] — exactly
     * [QueensPuzzle.queensPerUnit] queens in every row, column, and region, and no two
     * queens touching. Extra queens are rejected: the set size must be exact.
     */
    fun isSolution(puzzle: QueensPuzzle, queens: Set<Cell>): Boolean =
        QueensRules.evaluate(puzzle, queens) is QueensRulesVerdict.Valid

    /**
     * Per-unit counts for a placement: rows/columns indexed 0 until size, regions by id
     * (0 until size). Diagnostic/table output for consumers that need counts, not verdicts.
     */
    fun unitCounts(puzzle: QueensPuzzle, queens: Set<Cell>): Triple<IntArray, IntArray, IntArray> {
        val rows = IntArray(puzzle.size)
        val cols = IntArray(puzzle.size)
        val regions = IntArray(puzzle.size)
        for (q in queens) {
            rows[puzzle.rowOf(q)]++
            cols[puzzle.colOf(q)]++
            regions[puzzle.regionOf(q)]++
        }
        return Triple(rows, cols, regions)
    }
}
