package com.ivandsky.puzzlebox.games.zip.engine

import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.ZipPuzzle

/**
 * Exhaustive Hamiltonian-path oracle (A16): solve(), countSolutions() with early exit,
 * and prefix-aware queries powering the hint policy. Deterministic — the same puzzle
 * always yields the same first solution, so hints and tests are stable.
 *
 * Search: DFS over path extensions with cheap pruning — dead-end counting (an unvisited
 * cell with at most one unvisited neighbor must be the FINAL path cell), waypoint
 * monotonicity (a future waypoint cell may not be entered early), and a Warnsdorff-style
 * candidate order (fewest unvisited neighbors first). Uniqueness claims are exact: the
 * search is exhaustive up to the requested limit.
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
        if (!isPrefixCandidate(puzzle, path)) return 0
        var low = 0
        var high = path.size
        while (low < high) {
            val mid = (low + high + 1) / 2
            if (canComplete(puzzle, path.subList(0, mid))) low = mid else high = mid - 1
        }
        return low
    }

    private fun isPrefixCandidate(puzzle: ZipPuzzle, path: List<Cell>): Boolean =
        ZipRules.verifyPath(puzzle, path)

    fun search(puzzle: ZipPuzzle, prefix: List<Cell>, limit: Int): Result {
        val board = puzzle.board
        val total = board.cellCount
        if (board.waypoints.isEmpty() || limit <= 0) return Result(0, null)
        if (prefix.isNotEmpty() && !ZipRules.verifyPath(puzzle, prefix)) return Result(0, null)

        val waypointIndex = IntArray(total) { -1 }
        board.waypoints.forEachIndexed { i, cell -> waypointIndex[cell.idx] = i }

        val visited = BooleanArray(total)
        val path = ArrayList<Cell>(total)
        var waypointsInPath = 0

        if (prefix.isEmpty()) {
            val start = board.waypoints.first()
            visited[start.idx] = true
            path.add(start)
            if (waypointIndex[start.idx] >= 0) waypointsInPath = 1
        } else {
            prefix.forEach { cell ->
                visited[cell.idx] = true
                path.add(cell)
            }
            waypointsInPath = prefix.count { waypointIndex[it.idx] >= 0 }
        }

        var found = 0
        var first: List<Cell>? = null

        fun neighborsOf(cell: Cell): List<Cell> = board.neighbors(cell)

        fun availableDegree(cell: Cell, pathEnd: Cell): Int = neighborsOf(cell).count {
            !visited[it.idx] || it == pathEnd
        }

        fun dfs(): Boolean {
            if (path.size == total) {
                found++
                if (first == null) first = path.toList()
                return found >= limit
            }
            // Prune: unvisited cells reachable only as dead ends. Every unvisited cell with
            // <= 1 available neighbor must be the single FINAL cell of the path.
            var deadEndCandidates = 0
            for (idx in 0 until total) {
                if (visited[idx]) continue
                val degree = availableDegree(Cell(idx), path.last())
                if (degree == 0) return false // unreachable forever
                if (degree == 1) {
                    deadEndCandidates++
                    if (deadEndCandidates > 1) return false
                }
            }
            val end = path.last()
            val candidates = neighborsOf(end)
                .filter { !visited[it.idx] && !board.blocked(end, it) }
                .filter { waypointIndex[it.idx] < 0 || waypointIndex[it.idx] == waypointsInPath }
                .sortedBy { candidate ->
                    neighborsOf(candidate).count { !visited[it.idx] } // Warnsdorff order
                }
            for (candidate in candidates) {
                val wIdx = waypointIndex[candidate.idx]
                visited[candidate.idx] = true
                path.add(candidate)
                if (wIdx >= 0) waypointsInPath++
                val hitLimit = dfs()
                if (wIdx >= 0) waypointsInPath--
                path.removeAt(path.lastIndex)
                visited[candidate.idx] = false
                if (hitLimit) return true
            }
            return false
        }

        dfs()
        return Result(found, first)
    }
}
