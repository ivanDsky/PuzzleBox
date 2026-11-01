package com.ivandsky.puzzlebox.games.zip.engine

import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.ZipPuzzle

/**
 * The DFS core (A18 addendum §3): exhaustive path search with explicit prefix frame
 * step/undo — the recursion already reuses the common prefix; this is that part named.
 * It NEVER calls [ZipRule.check] (failures dominate the search and ZipViolation
 * allocates): the pipeline predicates appear here as their primitive forms — the
 * completion gate is [EndAtLastWaypointRule] (`path.last() == last waypoint`), waypoint
 * monotonicity is [WaypointOrderRule] over the [ZipVerifier.waypointIndexTable].
 */
object ZipSearch {

    fun search(puzzle: ZipPuzzle, prefix: List<Cell>, limit: Int): ZipSolver.Result {
        val board = puzzle.board
        val total = board.cellCount
        if (board.waypoints.isEmpty() || limit <= 0) return ZipSolver.Result(0, null)
        if (prefix.isNotEmpty() && !ZipVerifier.verifyPath(puzzle, prefix)) return ZipSolver.Result(0, null)

        val waypointIndex = ZipVerifier.waypointIndexTable(board)

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
                // I6 (EndAtLastWaypointRule): a solution must END at the final waypoint.
                // Full coverage alone is not enough — a walk that enters waypoint k
                // mid-path and finishes elsewhere is a dead trace, never a solution
                // (ZipRules.isComplete agrees).
                if (path.last() == board.waypoints.last()) {
                    found++
                    if (first == null) first = path.toList()
                }
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
        return ZipSolver.Result(found, first)
    }
}
