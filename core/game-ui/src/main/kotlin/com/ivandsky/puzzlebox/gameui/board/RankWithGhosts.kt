package com.ivandsky.puzzlebox.gameui.board

import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.game.linkedin.linkedInScore
import com.ivandsky.puzzlebox.game.linkedin.rankCombined

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
    val slots = rankCombined(
        rows = rows.map { BestScore(it.timeMs, it.hints) },
        ghosts = ghosts.map { ghost -> ghost.timeMs?.let { linkedInScore(it, ghost.hintFree) } },
        order = order.comparator,
    )
    return slots.map { slot ->
        if (slot.ghost) DailyBoardLine.Ghost(ghosts[slot.index], slot.place) else DailyBoardLine.Real(rows[slot.index], checkNotNull(slot.place))
    }
}
