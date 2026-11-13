package com.ivandsky.puzzlebox.history.ranks

import com.ivandsky.puzzlebox.domain.ranks.BoardLoad
import com.ivandsky.puzzlebox.domain.ranks.GameWindowStandings
import com.ivandsky.puzzlebox.domain.ranks.RankWindowBoards
import com.ivandsky.puzzlebox.domain.ranks.RankedBoard
import com.ivandsky.puzzlebox.domain.ranks.gameWindowStandings
import com.ivandsky.puzzlebox.domain.ranks.rankBoard
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.board.BoardRowValue
import com.ivandsky.puzzlebox.gameui.board.DailyBoardLine
import com.ivandsky.puzzlebox.gameui.board.DailyBoardRowModel
import com.ivandsky.puzzlebox.gameui.board.DailyBoardState
import com.ivandsky.puzzlebox.gameui.board.rankWithLinkedIn
import com.ivandsky.puzzlebox.history.ranks.RanksSelection.Window
import com.ivandsky.puzzlebox.model.GameId
import kotlin.math.roundToInt

/**
 * One game's board out of the hub's shared window boards (null = no answer for this window yet),
 * so a swipe to a game reads nothing of its own. A day ranks that day's entries by [order]; a week
 * or month ranks each player by the average over the days they played (A36). Rows are keyed by
 * player uid, so the same list re-ranks when the board changes.
 */
internal fun RankWindowBoards?.toGameBoardState(game: GameId, order: RankOrder, kind: Window, selfUid: String?): DailyBoardState {
    val boards = when (this) {
        null -> return DailyBoardState.Loading
        RankWindowBoards.SignedOut -> return DailyBoardState.SignedOut
        is RankWindowBoards.Boards -> boards.filter { it.board.gameId == game.value }
    }
    val loaded = boards.filterIsInstance<BoardLoad.Loaded>()
    if (loaded.isEmpty()) {
        return when {
            boards.any { it is BoardLoad.Loading } -> DailyBoardState.Loading
            boards.any { it is BoardLoad.Unavailable } -> DailyBoardState.Unavailable
            else -> DailyBoardState.Ranked(emptyList())
        }
    }
    val ranked = loaded.map { rankBoard(it.board, it.page.entries, it.order, it.page.count) }
    val rows = when (kind) {
        Window.Day -> ranked.flatMap(RankedBoard::rows).map { it.toRow(selfUid) }
        Window.Week, Window.Month -> gameWindowStandings(ranked, order).rows.map { it.toRow(selfUid) }
    }
    return DailyBoardState.Ranked(rows)
}

/** What to say under a week or month's rows when some of its days are not in yet; a day is one board and has nothing to add. */
internal fun RankWindowBoards?.gameBoardNote(game: GameId, kind: Window): String? {
    if (this !is RankWindowBoards.Boards || kind == Window.Day) return null
    val boards = boards.filter { it.board.gameId == game.value }
    val loaded = boards.count { it is BoardLoad.Loaded }
    return if (loaded in 1 until boards.size) "$loaded of ${boards.size} days" else null
}

private const val WITH_LINKEDIN = "with LinkedIn connections"

/**
 * A game's board in Friends scope (A6, A15): the connections [input] merge in as on the game page (`rankWithLinkedIn`: a
 * shared place, no PuzzleBox rank, no actions) over the whole board, and only then do the rows narrow to the viewer and
 * [friends], so every place counts everyone ahead of it. The viewer's unofficial row stays out of that ranking and goes back
 * at its own place: it takes none from anyone (A14). Without ghosts this is plain [friendsOnly].
 */
internal fun RanksBoardSlice.inFriends(friends: Set<String>, input: BoardGhostInput): RanksBoardSlice {
    val ranked = state as? DailyBoardState.Ranked
    if (ranked == null || (input.ghosts.isEmpty() && input.you == null)) return copy(state = state.friendsOnly(friends))
    val (unofficial, official) = ranked.rows.partition { it.unofficial }
    // A LinkedIn import replaces the viewer's time only on a row they really have; off the board it is the unofficial row's business.
    val you = input.you.takeIf { official.any { it.isSelf } }
    val lines = unofficial.fold(rankWithLinkedIn(official, input.ghosts, you, order)) { merged, row -> merged.withUnofficialLine(row) }
    val merged = DailyBoardState.Ranked(rows = lines.filterIsInstance<DailyBoardLine.Real>().map { it.row }, lines = lines)
    return copy(state = merged.friendsOnly(friends), note = WITH_LINKEDIN.takeIf { input.ghosts.isNotEmpty() })
}

private fun RankedBoard.Row.toRow(selfUid: String?) = DailyBoardRowModel(
    rank = rank,
    name = entry.displayName,
    timeMs = entry.timeMs,
    hints = entry.hints,
    isSelf = entry.uid == selfUid,
    playerId = entry.uid,
)

private fun GameWindowStandings.Row.toRow(selfUid: String?) = DailyBoardRowModel(
    rank = place,
    name = displayName,
    timeMs = avgTimeMs,
    hints = avgHints.roundToInt(),
    isSelf = uid == selfUid,
    playerId = uid,
    value = BoardRowValue.Average(avgTimeMs, avgHints),
)
