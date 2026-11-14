package com.ivandsky.puzzlebox.history.ranks

import com.ivandsky.puzzlebox.domain.ranks.BoardLoad
import com.ivandsky.puzzlebox.domain.ranks.GameWindowStandings
import com.ivandsky.puzzlebox.domain.ranks.RankWindowBoards
import com.ivandsky.puzzlebox.domain.ranks.RankedBoard
import com.ivandsky.puzzlebox.domain.ranks.gameWindowStandings
import com.ivandsky.puzzlebox.domain.ranks.connectionUid
import com.ivandsky.puzzlebox.domain.ranks.rankBoard
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.board.BoardRowValue
import com.ivandsky.puzzlebox.gameui.board.DailyBoardLine
import com.ivandsky.puzzlebox.gameui.board.DailyBoardRowModel
import com.ivandsky.puzzlebox.gameui.board.DailyBoardState
import com.ivandsky.puzzlebox.gameui.board.LinkedInYou
import com.ivandsky.puzzlebox.history.ranks.RanksSelection.Window
import com.ivandsky.puzzlebox.model.GameId
import kotlin.math.roundToInt

/**
 * One game's board out of the hub's shared window boards (null = no answer for this window yet),
 * so a swipe to a game reads nothing of its own. A day ranks that day's entries by [order]; a week
 * or month ranks each player by the average over the days they played. Rows are keyed by
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
 * A game's Day board in Friends scope: the viewer and their friends ranked among themselves. Every line's place is the
 * one the same day has on [boards]' counted board, which the domain's Friends pipeline built (`friendBoard`) and
 * [toGameBoardState] ranks, so it is the place an entry point shows. A connection is a ghost line (a shared place, no
 * PuzzleBox rank, no actions); a friend keeps the rank they have among friends. The viewer's unofficial row stays out
 * of that ranking and keeps its own place, taking none from anyone.
 * [RanksBoardSlice.friendsAlone] marks a list of the viewer alone.
 */
internal fun RanksBoardSlice.inFriends(boards: BoardGhosts.Counted?, game: GameId, selfUid: String?): RanksBoardSlice =
    withGhosts(boards, game, selfUid).let { it.copy(friendsAlone = it.state.isViewerAlone()) }

private fun RanksBoardSlice.withGhosts(boards: BoardGhosts.Counted?, game: GameId, selfUid: String?): RanksBoardSlice {
    val ranked = state as? DailyBoardState.Ranked
    val input = boards?.ghostInput(game, selfUid) ?: BoardGhostInput.None
    if (ranked == null || (input.ghosts.isEmpty() && input.you == null)) return this
    val counted = boards?.counted.toGameBoardState(game, order, Window.Day, selfUid) as? DailyBoardState.Ranked ?: return this
    val (unofficial, official) = ranked.rows.partition { it.unofficial }
    val friends = official.associateBy { it.playerId }
    val ghosts = input.ghosts.associateBy { connectionUid(it.memberRef) }
    // The viewer's LinkedIn picture and import mark go only on a row they really have; off the board it is the unofficial row's business.
    val you = input.you.takeIf { official.any { it.isSelf } }
    val placed: List<DailyBoardLine> = counted.rows.mapNotNull { row ->
        friends[row.playerId]?.let { DailyBoardLine.Real(it.shownAs(row, you), row.rank) } ?: ghosts[row.playerId]?.let { DailyBoardLine.Ghost(it, row.rank) }
    }
    val lines = unofficial.fold(placed + input.unplaced.map { DailyBoardLine.Ghost(it, null) }) { merged, row -> merged.withUnofficialLine(row) }
    val merged = DailyBoardState.Ranked(rows = lines.filterIsInstance<DailyBoardLine.Real>().map { it.row }, lines = lines)
    return copy(state = merged, note = WITH_LINKEDIN.takeIf { input.ghosts.isNotEmpty() })
}

/** This friend's row with the score [counted] ranks them by, marked when LinkedIn gave it; the viewer's row takes [you]'s picture. */
private fun DailyBoardRowModel.shownAs(counted: DailyBoardRowModel, you: LinkedInYou?): DailyBoardRowModel {
    val own = you.takeIf { isSelf }
    return copy(
        timeMs = counted.timeMs,
        hints = counted.hints,
        timeFromLinkedIn = counted.timeMs != timeMs || counted.hints != hints || own?.score != null,
        photo = own?.photo ?: photo,
    )
}

/**
 * A Friends board whose rows already rank the connections among the friends: a game's Week or Month, and
 * Overall. A connection's row is the one whose uid is a key of [photos] (every counted connection has a key, its picture
 * possibly null): it is marked as LinkedIn's (its dot and picture, no player to open) and the board
 * says so beside whatever it already notes. [RanksBoardSlice.friendsAlone] is as on [inFriends]: a connection is company.
 */
internal fun RanksBoardSlice.inFriendsCounted(photos: Map<String, String?>): RanksBoardSlice {
    val ranked = state as? DailyBoardState.Ranked
    if (ranked == null || ranked.rows.none { it.playerId in photos }) return copy(friendsAlone = state.isViewerAlone())
    val rows = ranked.rows.map { row ->
        if (row.playerId in photos) row.copy(fromLinkedIn = true, timeFromLinkedIn = true, photo = photos[row.playerId]) else row
    }
    return copy(state = ranked.copy(rows = rows), note = listOfNotNull(note, WITH_LINKEDIN).joinToString(" · "))
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
