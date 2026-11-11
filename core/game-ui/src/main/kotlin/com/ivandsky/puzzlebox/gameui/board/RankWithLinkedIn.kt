package com.ivandsky.puzzlebox.gameui.board

import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.game.linkedin.linkedInScore
import com.ivandsky.puzzlebox.game.linkedin.linkedInTimeWins

/**
 * [rankWithGhosts] once a LinkedIn time wins over the PuzzleBox one (see [linkedInTimeWins]): the player's row, which
 * is this profile's own row, else the row named like their LinkedIn name, else their LinkedIn row added at the end of
 * [rows] (ranked by its time, with no PuzzleBox rank), is the highlighted you row with the [you] photo and imported
 * time; a ghost named like another PuzzleBox row is that row at the ghost's time. The rows are then in the order of the
 * times they show, each keeping its own PuzzleBox rank. With no [you] and no [ghosts] this is exactly [rankWithGhosts].
 * [rows] must be the whole board, as there.
 */
fun rankWithLinkedIn(
    rows: List<DailyBoardRowModel>,
    ghosts: List<GhostRowModel>,
    you: LinkedInYou?,
    order: RankOrder,
): List<DailyBoardLine> {
    if (you == null && ghosts.isEmpty()) return rankWithGhosts(rows, ghosts, order)
    val wins = linkedInTimeWins(
        rowNames = rows.map { it.name },
        rowScores = rows.map { BestScore(it.timeMs, it.hints) },
        selfRow = rows.indexOfFirst { it.isSelf }.takeIf { it >= 0 },
        ghostNames = ghosts.map { it.name },
        ghostScores = ghosts.map { ghost -> ghost.timeMs?.let { linkedInScore(it, ghost.hintFree) } },
        own = you?.score,
        ownName = you?.name,
    )
    if (ghosts.isEmpty() && wins.youRow == null && !wins.addOwnRow) return rankWithGhosts(rows, ghosts, order)
    val comparator = order.comparator
    val photo = you?.photo
    val shown = rows.mapIndexed { index, row ->
        val score = wins.rowScores[index]
        val shownRow = row.copy(
            timeMs = score.timeMs,
            hints = score.hints,
            timeFromLinkedIn = score != BestScore(row.timeMs, row.hints) || (index == wins.youRow && you?.score != null),
        )
        if (index == wins.youRow) shownRow.copy(isSelf = true, photo = photo ?: shownRow.photo) else shownRow
    }
    val added = listOfNotNull(
        if (wins.addOwnRow && you?.score != null && you.name != null) {
            DailyBoardRowModel(rows.size + 1, you.name, you.score.timeMs, you.score.hints, isSelf = true, photo = photo, fromLinkedIn = true, timeFromLinkedIn = true)
        } else {
            null
        },
    )
    // The sort is stable, so rows with equal scores keep the board's order, the added row after them.
    val sorted = (shown + added).sortedWith { a, b -> comparator.compare(BestScore(a.timeMs, a.hints), BestScore(b.timeMs, b.hints)) }
    return mergeRanked(sorted, ghosts.filterIndexed { index, _ -> wins.ghostKept[index] }, order)
}
