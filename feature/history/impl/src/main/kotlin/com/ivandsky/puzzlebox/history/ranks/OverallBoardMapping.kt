package com.ivandsky.puzzlebox.history.ranks

import com.ivandsky.puzzlebox.data.leaderboard.BoardPage
import com.ivandsky.puzzlebox.history.domain.ranks.OverallStandings
import com.ivandsky.puzzlebox.history.domain.ranks.OverallState
import com.ivandsky.puzzlebox.resultui.board.BoardRowValue
import com.ivandsky.puzzlebox.resultui.board.DailyBoardRowModel
import com.ivandsky.puzzlebox.resultui.board.DailyBoardState

/**
 * The Overall result as the kit's board. The viewer's row is a normal row, marked
 * [DailyBoardRowModel.isSelf] so the board can dock it. A truncated total is a lower bound (≥)
 * and a place past the page cap reads 100+. The caveat that a result is partial or offline is the
 * board's [boardNote]: the kit's note enum has no room for "k of n".
 */
internal fun OverallState.toDailyBoardState(selfUid: String?): DailyBoardState = when (this) {
    OverallState.Loading -> DailyBoardState.Loading
    OverallState.SignedOut -> DailyBoardState.SignedOut
    OverallState.Empty -> DailyBoardState.Ranked(emptyList())
    is OverallState.Partial -> DailyBoardState.Ranked(standings.toRows(selfUid))
    is OverallState.Complete -> DailyBoardState.Ranked(standings.toRows(selfUid))
    is OverallState.Offline -> standings?.let { DailyBoardState.Ranked(it.toRows(selfUid)) } ?: DailyBoardState.Unavailable
}

/** What to say under the Overall rows: how complete they are. Null when they are all there is to say. */
internal fun OverallState.boardNote(): String? = when (this) {
    is OverallState.Partial -> "$loaded of $total games"
    is OverallState.Offline -> standings?.let { "Offline — showing Ranks saved on this device" }
    else -> null
}

private fun OverallStandings.toRows(selfUid: String?): List<DailyBoardRowModel> =
    pointRows(selfUid) { BoardRowValue.Points(it.total, it.games, atLeast = it.truncated) }

/** Points standings as the kit's rows, each scoring [value] of its row; a place past the page cap reads 100+. */
internal fun OverallStandings.pointRows(selfUid: String?, value: (OverallStandings.Row) -> BoardRowValue): List<DailyBoardRowModel> =
    rows.map { row ->
        DailyBoardRowModel(
            rank = row.place,
            name = row.displayName,
            timeMs = 0L,
            hints = 0,
            isSelf = row.uid == selfUid,
            playerId = row.uid,
            value = value(row),
            beyondPage = row.place > BoardPage.PAGE_SIZE,
        )
    }
