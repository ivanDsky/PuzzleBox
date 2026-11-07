package com.ivandsky.puzzlebox.games.zip.editor

import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.ZipBoard

/**
 * Local causes for a Zip board without a solution: dead ends and end-colour parity.
 * Other reasons exist, so an empty set is normal.
 */
object ZipClashLocator {
    /** Dead ends (a non-endpoint cell with < 2 open neighbours) and, when n² parity forbids the
     *  first/last waypoint colours, those two waypoints. Empty when no local cause is found. */
    fun conflicts(board: ZipBoard): Set<Int> = deadEnds(board) + parityClash(board)

    private fun deadEnds(board: ZipBoard): Set<Int> {
        val ends = setOfNotNull(board.waypoints.firstOrNull()?.idx, board.waypoints.lastOrNull()?.idx)
        return (0 until board.cellCount).filterTo(HashSet()) {
            it !in ends && openNeighbours(board, it) < MIN_OPEN
        }
    }

    private fun openNeighbours(board: ZipBoard, idx: Int): Int {
        val row = idx / board.n
        val col = idx % board.n
        val candidates = buildList {
            if (row > 0) add(idx - board.n)
            if (row < board.n - 1) add(idx + board.n)
            if (col > 0) add(idx - 1)
            if (col < board.n - 1) add(idx + 1)
        }
        return candidates.count { !board.blocked(Cell(idx), Cell(it)) }
    }

    /** Even n: ends differ in colour. Odd n: both ends sit on the majority (corner) colour. */
    private fun parityClash(board: ZipBoard): Set<Int> {
        if (board.waypoints.size < 2) return emptySet()
        val first = board.waypoints.first().idx
        val last = board.waypoints.last().idx
        val firstEven = isEven(board, first)
        val lastEven = isEven(board, last)
        val allowed = if (board.n % 2 == 0) firstEven != lastEven else firstEven && lastEven
        return if (allowed) emptySet() else setOf(first, last)
    }

    private fun isEven(board: ZipBoard, idx: Int): Boolean = (idx / board.n + idx % board.n) % 2 == 0

    private const val MIN_OPEN = 2
}
