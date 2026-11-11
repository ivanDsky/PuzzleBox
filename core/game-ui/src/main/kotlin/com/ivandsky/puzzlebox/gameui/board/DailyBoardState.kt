package com.ivandsky.puzzlebox.gameui.board

/** The board states a caller hands the board; the board draws exactly one at a time. */
sealed interface DailyBoardState {
    data object Loading : DailyBoardState

    /** Soft "not right now" (A14). */
    data object Unavailable : DailyBoardState

    /** Signed out: the caller's sign-in slot renders (A13). */
    data object SignedOut : DailyBoardState

    /**
     * Empty [rows] = "No one is on today's board yet". [pinned] is the player's own row, drawn set apart
     * under [rows] when it is outside them. [lines] are [rows] and the ghosts already ranked together over the
     * whole board before the caller cut [rows], and [pinnedPlace] the pinned row's place in that ranking; null
     * lines mean the board ranks [rows] with the ghosts it was given, and a null place means the row's own rank.
     */
    data class Ranked(
        val rows: List<DailyBoardRowModel>,
        val note: DailyBoardNote? = null,
        val pinned: DailyBoardRowModel? = null,
        val lines: List<DailyBoardLine>? = null,
        val pinnedPlace: Int? = null,
    ) : DailyBoardState
}
