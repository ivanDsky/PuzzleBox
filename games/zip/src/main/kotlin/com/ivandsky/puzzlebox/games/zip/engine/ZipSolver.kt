package com.ivandsky.puzzlebox.games.zip.engine

import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.ZipPuzzle

/**
 * Exhaustive Hamiltonian-path oracle (A16): solve(), countSolutions() with early exit,
 * and prefix-aware queries powering the hint fallback. Deterministic — the same puzzle
 * always yields the same first solution, so hints and tests are stable.
 *
 * Thin facade (A18 addendum §3): [ZipSearch] owns the DFS, [ZipVerifier] owns path
 * verification + completion tables; this class keeps the public API (and [Result]) that
 * the fetch gate, generator, and hints read. Search internals: dead-end counting (an
 * unvisited cell with at most one unvisited neighbor must be the FINAL path cell),
 * waypoint monotonicity, and a Warnsdorff-style candidate order. Uniqueness claims are
 * exact: the search is exhaustive up to the requested limit.
 */
class ZipSolver {

    data class Result(
        val solutionCount: Int,
        val firstSolution: List<Cell>?,
    )

    /** The unique full solution, or null when none exists / the prefix is invalid. */
    fun solve(puzzle: ZipPuzzle): List<Cell>? = search(puzzle, emptyList(), limit = 2).firstSolution

    /** Number of I1–I7-satisfying paths, counted up to [limit] (I10 needs limit = 2). */
    fun countSolutions(puzzle: ZipPuzzle, limit: Int = 2): Int = search(puzzle, emptyList(), limit).solutionCount

    /** Can the current trace still be extended to a full solution? */
    fun canComplete(puzzle: ZipPuzzle, path: List<Cell>): Boolean =
        search(puzzle, path, limit = 1).solutionCount > 0

    /** A full solution having [path] as prefix, or null. */
    fun completionFrom(puzzle: ZipPuzzle, path: List<Cell>): List<Cell>? =
        search(puzzle, path, limit = 1).firstSolution

    /**
     * Longest prefix length L <= path.size whose trace still completes to a full solution.
     * Monotone (every completion of a longer prefix completes the shorter), so binary
     * search applies. 0 is always solvable for a valid puzzle (empty trace -> solve).
     */
    fun longestSolvablePrefix(puzzle: ZipPuzzle, path: List<Cell>): Int {
        if (!ZipVerifier.verifyPath(puzzle, path)) return 0
        var low = 0
        var high = path.size
        while (low < high) {
            val mid = (low + high + 1) / 2
            if (canComplete(puzzle, path.subList(0, mid))) low = mid else high = mid - 1
        }
        return low
    }

    /** The exhaustive search itself: [ZipSearch]. */
    fun search(puzzle: ZipPuzzle, prefix: List<Cell>, limit: Int): Result =
        ZipSearch.search(puzzle, prefix, limit)
}
