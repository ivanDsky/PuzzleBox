package com.ivandsky.puzzlebox.gameui.board

import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.game.linkedin.linkedInScore

/** Interleaves ghosts by [order]; ghosts never take a rank; hidden times go last. */
fun mergeGhosts(rows: List<DailyBoardRowModel>, ghosts: List<GhostRowModel>, order: RankOrder): List<DailyBoardLine> {
    if (ghosts.isEmpty()) return rows.map(DailyBoardLine::Real)
    val comparator = order.comparator
    // Hidden times are not 0:00; they sort after everything, in the order given.
    val (timed, hidden) = ghosts.partition { it.timeMs != null }
    val sorted = timed.sortedWith { a, b -> comparator.compare(a.score(), b.score()) }
    val lines = ArrayList<DailyBoardLine>(rows.size + ghosts.size)
    var next = 0
    for (row in rows) {
        val real = BestScore(row.timeMs, row.hints)
        // Strictly better only: on a tie the real row stays first.
        while (next < sorted.size && comparator.compare(sorted[next].score(), real) < 0) {
            lines += DailyBoardLine.Ghost(sorted[next++])
        }
        lines += DailyBoardLine.Real(row)
    }
    while (next < sorted.size) lines += DailyBoardLine.Ghost(sorted[next++])
    hidden.mapTo(lines, DailyBoardLine::Ghost)
    return lines
}

private fun GhostRowModel.score(): BestScore = linkedInScore(checkNotNull(timeMs), hintFree)
