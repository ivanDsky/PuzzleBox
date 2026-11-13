package com.ivandsky.puzzlebox.history.ranks

import com.ivandsky.puzzlebox.domain.GameStats
import com.ivandsky.puzzlebox.history.CalendarState

/**
 * Everything the hub renders, one slice per concern; each slot reads only its own slice. Later
 * slots (top bar, calendar, sheets) add their slices here, each type in its own file. [position]
 * of [boardCount] boards is where the swipe stands. The [calendar] and the month's [stats] come from local
 * history alone, so they never wait on the network or a sign-in; [yourSolve] is the viewer's own solve of the board's day.
 */
internal data class RanksUiState(
    val board: RanksBoardSlice,
    val signIn: RanksSignIn = RanksSignIn.Idle,
    val position: Int = 0,
    val boardCount: Int = 1,
    val calendar: CalendarState = CalendarState(),
    val stats: GameStats? = null,
    val yourSolve: YourSolve? = null,
)
