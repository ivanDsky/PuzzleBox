package com.ivandsky.puzzlebox.history.ranks

import com.ivandsky.puzzlebox.domain.GameStats
import com.ivandsky.puzzlebox.domain.ranks.RankScope
import com.ivandsky.puzzlebox.gameui.board.DailyBoardLine
import com.ivandsky.puzzlebox.gameui.board.DailyBoardState
import com.ivandsky.puzzlebox.history.CalendarState

/**
 * Everything the hub renders, one slice per concern; each slot reads only its own slice. Later
 * slots (top bar, calendar, sheets) add their slices here, each type in its own file. [position]
 * of [boardCount] boards is where the swipe stands. The [calendar] and the month's [stats] come from local
 * history alone, so they never wait on the network or a sign-in; [yourSolve] is the viewer's own solve of the board's day.
 * [scope] and [window] are the tune sheet's picks the board already answers: in Friends every board and place is among
 * the viewer and their friends (F18). [places] is the viewer's place on every board, as the boards show it, for the
 * title menu; [nudge] says the one-time swipe hint has not been shown yet. [boards] is every board's
 * slice in swipe order ([board] is the one at [position]), there for a swipe to scrub to; empty when only [board] is known.
 */
internal data class RanksUiState(
    val board: RanksBoardSlice,
    val signIn: RanksSignIn = RanksSignIn.Idle,
    val position: Int = 0,
    val boardCount: Int = 1,
    val calendar: CalendarState = CalendarState(),
    val stats: GameStats? = null,
    val yourSolve: YourSolve? = null,
    val scope: RankScope = RankScope.Global,
    val window: RanksSelection.Window = RanksSelection.Window.Day,
    val places: List<BoardPlace> = emptyList(),
    val nudge: Boolean = false,
    val boards: List<RanksBoardSlice> = emptyList(),
)

/** True for a list of rows holding nobody but the viewer, and no LinkedIn ghost either; empty counts. Any other state is not "alone". */
internal fun DailyBoardState.isViewerAlone(): Boolean =
    this is DailyBoardState.Ranked && rows.all { it.isSelf } && lines.orEmpty().all { it is DailyBoardLine.Real }
