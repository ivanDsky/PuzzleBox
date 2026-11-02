package com.ivandsky.puzzlebox.games.queensui

import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.QueensSession
import java.time.Instant

/**
 * Shared queens-ui test fixtures: a 4×4 board whose regions ARE its rows (region rule
 * ≡ row rule), with a known-unique solution — queen of row r sits at column [r][cols].
 * Solution cells: (0,1), (1,3), (2,0), (3,2) — columns distinct, no diagonal touches.
 */
object QueensUiFixtures {

    val T0: Instant = Instant.parse("2026-11-02T00:00:00Z")

    fun puzzle(): QueensPuzzle {
        val n = 4
        return QueensPuzzle(
            size = n,
            regions = List(n * n) { it / n }, // region id = row
            solution = listOf(Cell(1), Cell(7), Cell(8), Cell(14)),
        )
    }

    /** Empty play session over [puzzle]. */
    fun session(): QueensSession = QueensSession(ref = null, puzzle = puzzle())

    /** The four solution cells in row order. */
    val solutionCells: List<Cell> = listOf(Cell(1), Cell(7), Cell(8), Cell(14))
}
