package com.ivandsky.puzzlebox.domain.ranks

import com.ivandsky.puzzlebox.data.leaderboard.BoardPage
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.RankOrder

/** How the viewer stands on one day's board (A14). */
sealed interface ViewerStanding {
    /** On the official board: the board's own row says where. */
    data object Official : ViewerStanding

    /** Device-only: solved for a day outside the submit window, so it sits at [place] and takes none from anyone. */
    data class Unofficial(val place: Int) : ViewerStanding

    /** Nothing to show: no solve, signed out, or an in-window solve that has not posted yet. */
    data object None : ViewerStanding
}

/**
 * The viewer's standing on [board] with their [local] solve of its day. A row on the board is official whatever
 * the clock says; a solve that is not on it is unofficial only once its day is outside the submit window
 * ([inWindow] false), when it can never post. Inside the window it is still on its way, so there is nothing to mark.
 */
fun viewerStanding(local: CompletionRecord?, inWindow: Boolean, board: BoardPage, uid: String?, order: RankOrder): ViewerStanding = when {
    uid == null || local == null -> ViewerStanding.None
    board.entries.any { it.uid == uid } -> ViewerStanding.Official
    inWindow -> ViewerStanding.None
    else -> ViewerStanding.Unofficial(unofficialPlace(local.time, local.hints, board.entries, order))
}
