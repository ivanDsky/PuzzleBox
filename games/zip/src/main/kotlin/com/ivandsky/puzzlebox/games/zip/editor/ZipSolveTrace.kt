package com.ivandsky.puzzlebox.games.zip.editor

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.Wall
import com.ivandsky.puzzlebox.games.zip.WallDirection
import com.ivandsky.puzzlebox.games.zip.ZipBoard
import kotlin.math.abs

/**
 * A solve of [draft]'s board along [path], its answer, counting what a solver has to rule out on
 * the way (ticket 94). At each cell of the path the solver may step to any open neighbour that is
 * not visited and keeps the numbers in order; every such step but the path's own is a wrong move.
 * A wrong move is searched until every way on from it is dead, and costs by how much that took:
 * [GLANCE] when it is dead as it stands, else [LOOK_AHEAD] and one more for each doubling of the
 * positions looked at. [points] is the sum over the whole path.
 *
 * A position is dead by what a person sees on the board: a cell with no way in left, a second
 * cell with one way in (only the path's last cell may be a dead end), a cell the path can no
 * longer reach, or a next number shut off behind other numbers. There is no colour-parity rule
 * as in the solver: people do not count squares.
 *
 * More walls and numbers leave fewer wrong moves and kill the ones left sooner, so on one path
 * [points] never rises when a wall or a number is added. The search is the whole search for
 * another answer, so [walk] also tells whether [path] is the only one.
 */
internal class ZipSolveTrace(
    draft: ZipDraft,
    private val path: List<Int>,
    private val budget: Budget,
    private val nodeCap: Int = Int.MAX_VALUE,
) {
    enum class Outcome { Unique, Another, Spent }

    private val n = draft.n
    private val cells = n * n
    private val open: Array<IntArray> = ZipBoard(n, draft.walls, emptyList()).let { board ->
        Array(cells) { cell -> board.neighbors(Cell(cell)).filter { !board.blocked(Cell(cell), it) }.map { it.idx }.toIntArray() }
    }

    /** A numbered cell's place in number order, -1 for a cell without a number. */
    private val place = IntArray(cells) { -1 }.also { places -> draft.waypoints.forEachIndexed { at, cell -> places[cell] = at } }
    private val lastPlace = draft.waypoints.lastIndex
    private val visited = BooleanArray(cells)
    private var length = 0
    private var numbersPassed = 0
    private val flooded = IntArray(cells)
    private val queue = IntArray(cells)
    private var flood = 0
    private var nodes = 0
    private var spent = false
    private var worstCost = 0

    /** The cost of every wrong move on the path, summed. Read after [walk] returned [Outcome.Unique]. */
    var points: Int = 0
        private set

    /** A wall that shuts the wrong move that cost the most; null when the path has no wrong move. */
    var worstMove: Wall? = null
        private set

    /** Walks [path], ruling out each wrong move. Spent once [budget] is, or past [nodeCap] positions. */
    fun walk(): Outcome {
        if (budget.exhausted()) return Outcome.Spent
        enter(path[0])
        for (step in 0 until cells - 1) {
            val from = path[step]
            val right = path[step + 1]
            for (move in open[from]) {
                if (move == right || !mayEnter(move)) continue
                val before = nodes
                enter(move)
                val completes = completes(move)
                leave(move)
                if (spent) return Outcome.Spent
                if (completes) return Outcome.Another
                count(from, move, looked = nodes - before)
            }
            enter(right)
        }
        return Outcome.Unique
    }

    private fun count(from: Int, move: Int, looked: Int) {
        val cost = if (looked == 0) GLANCE else LOOK_AHEAD + (Int.SIZE_BITS - 1 - looked.countLeadingZeroBits())
        points += cost
        if (cost > worstCost) {
            worstCost = cost
            worstMove = Wall(Cell(minOf(from, move)), if (abs(from - move) == 1) WallDirection.RIGHT else WallDirection.DOWN)
        }
    }

    /** Whether some way on from [end] covers the board. Counts a node for each position it has to look past. */
    private fun completes(end: Int): Boolean {
        if (length == cells) return true
        if (dead(end)) return false
        if (++nodes > nodeCap || nodes % POLL_EVERY == 0 && budget.exhausted()) {
            spent = true
            return false
        }
        for (move in open[end]) {
            if (!mayEnter(move)) continue
            enter(move)
            val completes = completes(move)
            leave(move)
            if (completes) return true
            if (spent) return false
        }
        return false
    }

    /** An unvisited cell without a number, or the next number; the last number only as the last cell. */
    private fun mayEnter(cell: Int): Boolean =
        !visited[cell] && (place[cell] == -1 || place[cell] == numbersPassed && (place[cell] != lastPlace || length == cells - 1))

    private fun dead(end: Int): Boolean {
        for (cell in 0 until cells) {
            if (visited[cell]) continue
            val waysIn = open[cell].count { !visited[it] || it == end }
            if (waysIn == 0 || waysIn == 1 && place[cell] != lastPlace) return true
        }
        return reached(end, throughNumbers = true) != cells - length || !nextNumberReached(end)
    }

    /** Whether the next number can be reached from [end] over cells without a number. */
    private fun nextNumberReached(end: Int): Boolean {
        reached(end, throughNumbers = false)
        return numberSeen
    }

    private var numberSeen = false

    /** Unvisited cells reachable from [end]; with [throughNumbers] false a numbered cell is seen, not passed. */
    private fun reached(end: Int, throughNumbers: Boolean): Int {
        val mark = ++flood
        var head = 0
        var tail = 0
        queue[tail++] = end
        numberSeen = false
        while (head < tail) {
            for (next in open[queue[head++]]) {
                if (visited[next] || flooded[next] == mark) continue
                if (!throughNumbers && place[next] != -1) {
                    if (place[next] == numbersPassed) numberSeen = true
                    continue
                }
                flooded[next] = mark
                queue[tail++] = next
            }
        }
        return tail - 1
    }

    private fun enter(cell: Int) {
        visited[cell] = true
        length++
        if (place[cell] != -1) numbersPassed++
    }

    private fun leave(cell: Int) {
        visited[cell] = false
        length--
        if (place[cell] != -1) numbersPassed--
    }

    private companion object {
        /** A wrong move that is dead as it stands. */
        const val GLANCE = 1

        /** A wrong move that takes looking at one position more; each doubling of the positions adds one. */
        const val LOOK_AHEAD = 2

        /** Positions between two reads of the budget's clock. */
        const val POLL_EVERY = 256
    }
}
