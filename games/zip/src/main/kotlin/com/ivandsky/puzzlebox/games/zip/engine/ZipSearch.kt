package com.ivandsky.puzzlebox.games.zip.engine

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.ZipBoard
import com.ivandsky.puzzlebox.games.zip.ZipPuzzle

/**
 * The DFS core (A18 addendum §3): exhaustive path search with explicit prefix frame
 * step/undo — the recursion already reuses the common prefix; this is that part named.
 * It NEVER calls [ZipRule.check] (failures dominate the search and ZipViolation
 * allocates): the pipeline predicates appear here as their primitive forms — the
 * completion gate is [EndAtLastWaypointRule] (`path.last() == last waypoint`), waypoint
 * monotonicity is [WaypointOrderRule] over the [ZipVerifier.waypointIndexTable].
 *
 * [bounded] polls its [Budget] once up front and then every [POLL_INTERVAL] DFS nodes, and
 * stops past its node cap; either way it unwinds and answers [ZipSearchOutcome.Exhausted].
 * The cap counts work, not time, so a board gets the same outcome on any device. [search]
 * runs the same walk with [Budget.Unlimited] and no cap, so the solver, hints and generator
 * see unchanged results.
 */
object ZipSearch {

    fun search(puzzle: ZipPuzzle, prefix: List<Cell>, limit: Int): ZipSolver.Result {
        var found = 0
        var first: List<Cell>? = null
        walk(puzzle, prefix, limit, Budget.Unlimited, maxNodes = Int.MAX_VALUE) { path ->
            found++
            if (first == null) first = path.toList()
        }
        return ZipSolver.Result(found, first)
    }

    /**
     * Up to [limit] distinct full paths in search order, or Exhausted once [budget] is spent or
     * the DFS has visited more than [maxNodes] nodes.
     */
    fun bounded(puzzle: ZipPuzzle, limit: Int, budget: Budget, maxNodes: Int = Int.MAX_VALUE): ZipSearchOutcome {
        if (budget.exhausted()) return ZipSearchOutcome.Exhausted
        val solutions = ArrayList<List<Cell>>()
        val settled = walk(puzzle, emptyList(), limit, budget, maxNodes) { path -> solutions.add(path.toList()) }
        return if (settled) ZipSearchOutcome.Done(solutions) else ZipSearchOutcome.Exhausted
    }

    /**
     * Hands each full path, live, to [onSolution] until [limit] are found. Returns false
     * when [budget] ran out or the DFS passed [maxNodes] nodes before the search settled.
     */
    private fun walk(
        puzzle: ZipPuzzle,
        prefix: List<Cell>,
        limit: Int,
        budget: Budget,
        maxNodes: Int,
        onSolution: (List<Cell>) -> Unit,
    ): Boolean {
        val board = puzzle.board
        val total = board.cellCount
        if (board.waypoints.isEmpty() || limit <= 0 || !parityAllows(board)) return true
        if (prefix.isNotEmpty() && !ZipVerifier.verifyPath(puzzle, prefix)) return true

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
        var nodes = 0
        var exhausted = false

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
                    onSolution(path)
                }
                return found >= limit
            }
            if (++nodes > maxNodes || nodes % POLL_INTERVAL == 0 && budget.exhausted()) {
                exhausted = true
                return true
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
        return !exhausted
    }

    /**
     * A full path visits every cell (walls only cut edges) and alternates checkerboard
     * colours. On an even board its ends differ in colour; on an odd board both sit on the
     * majority colour, the colour of cell 0. The dead-end prune cannot see this.
     */
    private fun parityAllows(board: ZipBoard): Boolean {
        fun colour(cell: Cell): Int = (board.rowOf(cell) + board.colOf(cell)) % 2
        val start = colour(board.waypoints.first())
        val end = colour(board.waypoints.last())
        return if (board.cellCount % 2 == 0) start != end else start == 0 && end == 0
    }

    /** DFS nodes between budget polls: each node scans the board, so well under 1 ms apart. */
    private const val POLL_INTERVAL = 256
}
