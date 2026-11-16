package com.ivandsky.puzzlebox.games.zip.editor

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.ZipBoard
import com.ivandsky.puzzlebox.games.zip.ZipGenerator
import kotlin.random.Random

/**
 * A random full path for a draft (Finish for me): the author's path stays as its first cells and
 * is walked on until every cell is covered, never through a wall. A path-less board is walked
 * from its first number to its last, through the others in their order.
 *
 * The walk is [ZipGenerator]'s: the move with the fewest onward moves first, ties broken at
 * random, stepping back when stuck. A branch is dropped as soon as the cells left cannot all be
 * covered: [canStillCover]. The first walk visits at most [WALK_NODES] nodes, then the next
 * starts over with a new shuffle and twice the limit: a bad early turn costs little, and a draft
 * whose one way out lies deep still gets a long walk.
 */
internal class ZipPathExtension(private val draft: ZipDraft, private val budget: Budget) {
    private val n = draft.n
    private val cells = n * n

    /** Per cell, the neighbours no wall cuts it off from. */
    private val open: Array<IntArray> = ZipBoard(n, draft.walls, emptyList()).let { board ->
        Array(cells) { cell ->
            board.neighbors(Cell(cell)).filter { !board.blocked(Cell(cell), it) }.map { it.idx }.toIntArray()
        }
    }

    /** The cells every walk starts with: the author's path, or the first number of a path-less board. */
    private val start: List<Int> = draft.path.ifEmpty { draft.waypoints.take(1) }

    /** On a path-less board, each number's place in the order (its label - 1); [NONE] elsewhere and on a drawn board. */
    private val order = IntArray(cells) { NONE }.also { order ->
        if (draft.path.isEmpty()) draft.waypoints.forEachIndexed { place, cell -> order[cell] = place }
    }

    /** The place of the number the path has to end on: the last of a path-less board with both ends numbered. */
    private val endPlace = if (draft.path.isEmpty() && draft.waypoints.size >= 2) draft.waypoints.lastIndex else NONE

    private val visited = BooleanArray(cells)
    private val path = IntArray(cells)
    private var size = 0
    private var numbersPassed = 0
    private val unvisitedOfColour = IntArray(2)
    private val reachedIn = IntArray(cells)
    private val queue = IntArray(cells)
    private var floods = 0
    private var nodes = 0
    private var nodeLimit = 0
    private var stopped = false

    /** True once a walk has tried every continuation, or the start is no path: these pieces have no full path. */
    var noPath: Boolean = false
        private set

    /** A full path, or null when there is none ([noPath]), [budget] is spent or [RESTARTS] walks found none. */
    fun extend(random: Random): List<Int>? {
        nodeLimit = WALK_NODES
        repeat(RESTARTS) {
            if (noPath || budget.exhausted()) return null
            nodes = 0
            stopped = false
            when {
                !seat() -> noPath = true
                walk(random) -> return path.toList()
                !stopped -> noPath = true
            }
            nodeLimit *= 2
        }
        return null
    }

    /** Lays [start] down on a cleared board; false when it is empty or not a walk (a jump, a wall crossed, a cell twice). */
    private fun seat(): Boolean {
        visited.fill(false)
        size = 0
        numbersPassed = 0
        for (colour in 0..1) unvisitedOfColour[colour] = (0 until cells).count { colourOf(it) == colour }
        for (cell in start) {
            if (visited[cell] || size > 0 && cell !in open[path[size - 1]]) return false
            step(cell)
        }
        return size > 0
    }

    private fun walk(random: Random): Boolean {
        if (size == cells) return true
        if (++nodes > nodeLimit || nodes % POLL_INTERVAL == 0 && budget.exhausted()) {
            stopped = true
            return false
        }
        val end = path[size - 1]
        if (!canStillCover(end)) return false
        val moves = open[end].filter { !visited[it] && mayEnter(it) }
            .shuffled(random)
            .sortedBy { move -> open[move].count { !visited[it] } }
        for (move in moves) {
            step(move)
            if (walk(random)) return true
            stepBack()
            if (stopped) return false
        }
        return false
    }

    /** A numbered cell of a path-less board is entered in its turn, and the last one as the last cell. */
    private fun mayEnter(cell: Int): Boolean =
        order[cell] == NONE || order[cell] == numbersPassed && (order[cell] != endPlace || size == cells - 1)

    /**
     * False when no walk from [end] can cover the unvisited cells. Colours alternate along a
     * path, so the cells left must split between the two colours as the walk will take them.
     * A cell with no way in is lost, and a cell with one way in has to be where the path stops:
     * there is one such cell at most, and it is the end number when the board has one. Last,
     * every cell left must be reachable from [end] through cells left.
     */
    private fun canStillCover(end: Int): Boolean {
        val left = cells - size
        if (unvisitedOfColour[1 - colourOf(end)] != (left + 1) / 2) return false
        var stops = 0
        for (cell in 0 until cells) {
            if (visited[cell]) continue
            val ways = open[cell].count { !visited[it] || it == end }
            if (ways == 0) return false
            if (ways == 1 && (++stops > 1 || endPlace != NONE && order[cell] != endPlace)) return false
        }
        return reachable(end) == left
    }

    /** Unvisited cells a walk from [end] can get to through unvisited cells. */
    private fun reachable(end: Int): Int {
        val flood = ++floods
        var head = 0
        var tail = 0
        queue[tail++] = end
        while (head < tail) {
            for (next in open[queue[head++]]) {
                if (visited[next] || reachedIn[next] == flood) continue
                reachedIn[next] = flood
                queue[tail++] = next
            }
        }
        return tail - 1
    }

    private fun step(cell: Int) {
        visited[cell] = true
        path[size++] = cell
        unvisitedOfColour[colourOf(cell)]--
        if (order[cell] != NONE) numbersPassed++
    }

    private fun stepBack() {
        val cell = path[--size]
        visited[cell] = false
        unvisitedOfColour[colourOf(cell)]++
        if (order[cell] != NONE) numbersPassed--
    }

    private fun colourOf(cell: Int): Int = (cell / n + cell % n) % 2

    private companion object {
        const val NONE = -1

        /**
         * Walks per [extend], each with twice the node limit of the one before: about 1.3
         * million nodes in all, half a second on a desktop JVM. Most drafts are settled by the
         * first walk within a few hundred nodes. A drawn path that hems cells in can need a few
         * hundred thousand, to find its one way out or to prove there is none; some are still
         * open after millions, and those give up here.
         */
        const val RESTARTS = 8

        /** Nodes the first walk may visit before the next starts over. */
        const val WALK_NODES = 5_000

        /** Nodes between budget polls: a node scans the board once, so well under 1 ms apart. */
        const val POLL_INTERVAL = 256
    }
}
