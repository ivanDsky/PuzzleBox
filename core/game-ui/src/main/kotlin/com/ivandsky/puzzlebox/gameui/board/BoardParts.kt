package com.ivandsky.puzzlebox.gameui.board

import com.ivandsky.puzzlebox.game.RankOrder

private const val MaxGhosts = 20

/**
 * One thing a board draws under its caption, top to bottom: a line of text, a row, the divider over the pinned row.
 * Its [key] is what it is whatever board it is on, so two boards' parts with one key are the same part (see [boardKeys]).
 */
internal sealed interface BoardPart {
    val key: String

    data class Row(
        override val key: String,
        val line: DailyBoardLine,
        val order: RankOrder,
        val withPuzzleBoxRank: Boolean,
    ) : BoardPart

    data object Divider : BoardPart {
        override val key: String get() = DIVIDER_KEY
    }

    data class Message(val text: String, val tag: String) : BoardPart {
        override val key: String get() = "message:$tag"
    }
}

/** The lines a board in [state] draws: the ranked rows with the [ghosts] among them, or the ghosts alone when signed out. */
internal fun boardLines(state: DailyBoardState, ghosts: List<GhostRowModel>, order: RankOrder): List<DailyBoardLine> = when (state) {
    is DailyBoardState.Ranked -> (state.lines ?: rankWithGhosts(state.rows, ghosts, order)).capGhosts()
    DailyBoardState.SignedOut -> rankWithGhosts(emptyList(), ghosts, order)
    DailyBoardState.Loading, DailyBoardState.Unavailable -> emptyList()
}

/** The player's own row set apart under the lines, at its place in the board's one ranking. */
internal fun pinnedLine(state: DailyBoardState): DailyBoardLine.Real? =
    (state as? DailyBoardState.Ranked)?.pinned?.let { DailyBoardLine.Real(it, state.pinnedPlace ?: it.rank) }

/** What a board in [state] says instead of rows, if anything; a ranked board only when it is [empty], worded for [isToday]. */
internal fun boardMessage(state: DailyBoardState, empty: Boolean, isToday: Boolean): BoardPart.Message? = when (state) {
    DailyBoardState.Loading -> BoardPart.Message("Loading…", "daily_board.loading")
    DailyBoardState.Unavailable -> BoardPart.Message("Leaderboard unavailable right now.", "daily_board.unavailable")
    DailyBoardState.SignedOut -> null
    is DailyBoardState.Ranked -> if (empty) {
        BoardPart.Message(if (isToday) "No one is on today's board yet." else "No one is on this day's board yet.", "daily_board.empty")
    } else {
        null
    }
}

/** Everything this board draws under its caption but the sign-in slot, in order: its message, rows, pinned row and note. */
internal fun BoardFace.parts(isToday: Boolean): List<BoardPart> {
    val lines = boardLines(state, ghosts, order)
    val pinned = pinnedLine(state)
    val all = lines + listOfNotNull(pinned)
    val keys = boardKeys(all, pinned != null)
    val withPuzzleBoxRank = all.placesDifferFromPuzzleBox()
    return buildList {
        boardMessage(state, lines.isEmpty(), isToday)?.let(::add)
        lines.forEachIndexed { index, line -> add(BoardPart.Row(keys[index], line, order, withPuzzleBoxRank)) }
        if (pinned != null) {
            add(BoardPart.Divider)
            add(BoardPart.Row(keys.last(), pinned, order, withPuzzleBoxRank))
        }
        (state as? DailyBoardState.Ranked)?.note?.let { add(BoardPart.Message(it.text, "daily_board.note")) }
    }
}

// Cut by merge order so the fastest ghosts stay and hidden times go first; real rows are never dropped.
private fun List<DailyBoardLine>.capGhosts(): List<DailyBoardLine> {
    var ghosts = 0
    return filter { it is DailyBoardLine.Real || ++ghosts <= MaxGhosts }
}
