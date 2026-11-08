package com.ivandsky.puzzlebox.gameui.board

/** The board states a caller hands the board; the board draws exactly one at a time. */
sealed interface DailyBoardState {
    data object Loading : DailyBoardState

    /** Soft "not right now" (A14). */
    data object Unavailable : DailyBoardState

    /** Signed out: the caller's sign-in slot renders (A13). */
    data object SignedOut : DailyBoardState

    /** Empty [rows] = "No one is on today's board yet". */
    data class Ranked(val rows: List<DailyBoardRowModel>, val note: DailyBoardNote? = null) : DailyBoardState
}
