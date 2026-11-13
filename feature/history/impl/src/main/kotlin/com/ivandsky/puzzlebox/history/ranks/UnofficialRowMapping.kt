package com.ivandsky.puzzlebox.history.ranks

import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.domain.ranks.BoardLoad
import com.ivandsky.puzzlebox.domain.ranks.RankWindowBoards
import com.ivandsky.puzzlebox.domain.ranks.ViewerStanding
import com.ivandsky.puzzlebox.domain.ranks.viewerStanding
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.board.DailyBoardLine
import com.ivandsky.puzzlebox.gameui.board.DailyBoardRowModel
import com.ivandsky.puzzlebox.gameui.board.DailyBoardState
import com.ivandsky.puzzlebox.history.HistoryDayRow
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate

/**
 * The viewer's device-only row for [game]'s day board (A14), or null when they have none to show: on the board
 * already, still inside the submit window, or no solve of that day. [inWindow] is the client's one window rule.
 */
internal fun RankWindowBoards?.unofficialRow(
    game: GameId,
    order: RankOrder,
    viewer: Identity?,
    history: List<HistoryDayRow>,
    inWindow: (LocalDate) -> Boolean,
): DailyBoardRowModel? {
    val board = (this as? RankWindowBoards.Boards)?.boards?.filterIsInstance<BoardLoad.Loaded>()
        ?.firstOrNull { it.board.gameId == game.value } ?: return null
    val day = board.board.day
    val local = history.firstOrNull { it.gameId == game && it.day == day && it.record != null }?.record
    val standing = viewerStanding(local, inWindow(day), board.page, viewer?.uid, order) as? ViewerStanding.Unofficial ?: return null
    return DailyBoardRowModel(
        rank = standing.place,
        name = checkNotNull(viewer).displayName,
        timeMs = checkNotNull(local).time,
        hints = local.hints,
        isSelf = true,
        playerId = viewer.uid,
        unofficial = true,
    )
}

/**
 * The board with [row] placed where it would rank. It takes no place from anyone: the rows around it keep the
 * ranks they had, and it goes ahead of any official row it ties with. No [row], or a board that is not a list, is unchanged.
 */
internal fun DailyBoardState.withUnofficialRow(row: DailyBoardRowModel?): DailyBoardState {
    if (row == null || this !is DailyBoardState.Ranked) return this
    val at = rows.indexOfFirst { it.rank >= row.rank }.takeIf { it >= 0 } ?: rows.size
    return copy(rows = rows.toMutableList().apply { add(at, row) }, lines = lines?.withUnofficialLine(row))
}

/** [row] in the ranked [lines] before every line at or below its place; a hidden-time ghost has no place and goes last. */
internal fun List<DailyBoardLine>.withUnofficialLine(row: DailyBoardRowModel): List<DailyBoardLine> {
    val at = indexOfFirst { line -> line.placeOrNull().let { it == null || it >= row.rank } }.takeIf { it >= 0 } ?: size
    return toMutableList().apply { add(at, DailyBoardLine.Real(row, row.rank)) }
}

private fun DailyBoardLine.placeOrNull(): Int? = when (this) {
    is DailyBoardLine.Real -> place
    is DailyBoardLine.Ghost -> place
}
