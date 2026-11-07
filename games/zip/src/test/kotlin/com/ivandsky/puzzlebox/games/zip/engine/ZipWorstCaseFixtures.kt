package com.ivandsky.puzzlebox.games.zip.engine

import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.ZipBoard
import com.ivandsky.puzzlebox.games.zip.ZipPuzzle
import com.ivandsky.puzzlebox.games.zip.ZipTestSupport

/**
 * Open boards of the largest variant (n = 8): no walls and only two waypoints, both on
 * corners, so wall and waypoint pruning never fire and the DFS branches as wide as it can.
 */
object ZipWorstCaseFixtures {

    private const val N = 8

    /**
     * Corner to opposite corner. Both corners share a checkerboard colour, and a 64-cell
     * path must end on the other colour, so no path exists. The dead-end prune cannot see
     * parity, so an unbudgeted search would have to walk the whole tree to say so.
     */
    val oppositeCorners: ZipPuzzle = open(Cell(0), Cell(N * N - 1))

    /** Corner to the adjacent corner on the same row: solvable, with many paths. */
    val adjacentCorners: ZipPuzzle = open(Cell(0), Cell(N - 1))

    val all: Map<String, ZipPuzzle> = mapOf(
        "opposite-corners" to oppositeCorners,
        "adjacent-corners" to adjacentCorners,
    )

    private fun open(start: Cell, end: Cell): ZipPuzzle = ZipPuzzle(
        ZipTestSupport.variantOf(N).id,
        ZipBoard(N, emptySet(), listOf(start, end)),
        null,
    )
}
