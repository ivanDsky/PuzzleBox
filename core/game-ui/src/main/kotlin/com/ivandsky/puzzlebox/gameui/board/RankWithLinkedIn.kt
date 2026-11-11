package com.ivandsky.puzzlebox.gameui.board

import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.game.linkedin.linkedInScore
import com.ivandsky.puzzlebox.game.linkedin.linkedInTimeWins

/**
 * [rankWithGhosts] once a LinkedIn time wins over the PuzzleBox one: the self row shows the player's imported [own]
 * score, and a ghost named like a PuzzleBox row is that row at the ghost's time (see [linkedInTimeWins]). The rows
 * are then in the order of the times they show, each keeping its own PuzzleBox rank. With no [own] and no [ghosts]
 * this is exactly [rankWithGhosts]. [rows] must be the whole board, as there.
 */
fun rankWithLinkedIn(
    rows: List<DailyBoardRowModel>,
    ghosts: List<GhostRowModel>,
    own: BestScore?,
    order: RankOrder,
): List<DailyBoardLine> {
    if (own == null && ghosts.isEmpty()) return rankWithGhosts(rows, ghosts, order)
    val wins = linkedInTimeWins(
        rowNames = rows.map { it.name },
        rowScores = rows.map { BestScore(it.timeMs, it.hints) },
        selfRow = rows.indexOfFirst { it.isSelf }.takeIf { it >= 0 },
        ghostNames = ghosts.map { it.name },
        ghostScores = ghosts.map { ghost -> ghost.timeMs?.let { linkedInScore(it, ghost.hintFree) } },
        own = own,
    )
    val comparator = order.comparator
    // The sort is stable, so rows with equal scores keep the board's order.
    val shown = rows
        .mapIndexed { index, row -> row.copy(timeMs = wins.rowScores[index].timeMs, hints = wins.rowScores[index].hints) }
        .sortedWith { a, b -> comparator.compare(BestScore(a.timeMs, a.hints), BestScore(b.timeMs, b.hints)) }
    return mergeRanked(shown, ghosts.filterIndexed { index, _ -> wins.ghostKept[index] }, order)
}
