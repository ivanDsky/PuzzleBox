package com.ivandsky.puzzlebox.domain.ranks

import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.data.leaderboard.entryComparator
import com.ivandsky.puzzlebox.game.RankOrder

/**
 * One board with every read entry ranked and scored.
 *
 * @property n the board's true size, which exceeds [rows] when only a page was read.
 * @property truncated the board holds entries that were not read.
 */
data class RankedBoard(
    val board: LeaderboardRef,
    val rows: List<Row>,
    val n: Int,
    val truncated: Boolean,
) {
    data class Row(val entry: LeaderboardEntry, val rank: Int, val points: Double)
}

/**
 * Ranks [entries] by [order]: rank is 1 plus the entries strictly better, so ties share a
 * rank and its points (A9). [count] is the board's size from the server, not the page
 * length; the caller decides when a count read is needed (A11).
 */
fun rankBoard(
    board: LeaderboardRef,
    entries: List<LeaderboardEntry>,
    order: RankOrder,
    count: Int,
): RankedBoard {
    val comparator = order.entryComparator()
    val sorted = entries.sortedWith(comparator)
    val n = maxOf(count, sorted.size)
    var rank = 1
    val rows = sorted.mapIndexed { index, entry ->
        if (index > 0 && comparator.compare(sorted[index - 1], entry) != 0) rank = index + 1
        RankedBoard.Row(entry, rank, percentilePoints(rank, n))
    }
    return RankedBoard(board, rows, n, truncated = n > sorted.size)
}
