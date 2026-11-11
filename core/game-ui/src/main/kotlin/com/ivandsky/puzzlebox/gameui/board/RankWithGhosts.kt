package com.ivandsky.puzzlebox.gameui.board

import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.game.linkedin.linkedInScore

/**
 * [rows] and [ghosts] as one board, ranked together by [order]: equal scores share a place and the next one skips
 * past them. On a tie the real row comes first; a hidden time is unranked (null place) and goes last, in the order
 * given. Real rows keep their order and their own PuzzleBox rank. With no ghosts the places are the rows' own ranks,
 * exactly as the board draws them without LinkedIn.
 *
 * [rows] must be the whole board: a place counts every entry ahead of it, so rows cut beforehand would understate it.
 */
fun rankWithGhosts(rows: List<DailyBoardRowModel>, ghosts: List<GhostRowModel>, order: RankOrder): List<DailyBoardLine> {
    if (ghosts.isEmpty()) return rows.map { DailyBoardLine.Real(it, it.rank) }
    return mergeRanked(rows, ghosts, order)
}

/** [rankWithGhosts] without its shortcut: [rows] sorted by [order] are ranked together with [ghosts], even with none. */
internal fun mergeRanked(rows: List<DailyBoardRowModel>, ghosts: List<GhostRowModel>, order: RankOrder): List<DailyBoardLine> {
    val comparator = order.comparator
    val (timed, hidden) = ghosts.partition { it.timeMs != null }
    val sorted = timed.sortedWith { a, b -> comparator.compare(a.score(), b.score()) }
    val lines = ArrayList<DailyBoardLine>(rows.size + ghosts.size)
    var placed = 0
    var place = 0
    var previous: BestScore? = null
    fun placeOf(score: BestScore): Int {
        placed++
        val before = previous
        if (before == null || comparator.compare(score, before) != 0) place = placed
        previous = score
        return place
    }
    var next = 0
    for (row in rows) {
        val real = BestScore(row.timeMs, row.hints)
        // Strictly better only: on a tie the real row stays first.
        while (next < sorted.size && comparator.compare(sorted[next].score(), real) < 0) {
            val ghost = sorted[next++]
            lines += DailyBoardLine.Ghost(ghost, placeOf(ghost.score()))
        }
        lines += DailyBoardLine.Real(row, placeOf(real))
    }
    while (next < sorted.size) {
        val ghost = sorted[next++]
        lines += DailyBoardLine.Ghost(ghost, placeOf(ghost.score()))
    }
    hidden.mapTo(lines) { DailyBoardLine.Ghost(it, null) }
    return lines
}

private fun GhostRowModel.score(): BestScore = linkedInScore(checkNotNull(timeMs), hintFree)
