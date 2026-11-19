package com.ivandsky.puzzlebox.history.domain.ranks

import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.leaderboard.BoardPage
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.domain.ranks.BoardLoad
import com.ivandsky.puzzlebox.domain.ranks.beyondPage
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.model.PuzzleKey

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

/**
 * How many of [loaded]'s days the viewer solved ([records]) but stands [ViewerStanding.Unofficial] on, as each day's
 * board marks them: the days a game's Week or Month leaves out of their points (ADR-0031 §11). [inWindow] is the
 * client's one submit-window rule. A board A11 places them past the page on counts for them, so it is not one of these;
 * nor is a Friends board their own LinkedIn result put them on (`friendBoard`).
 */
fun unofficialDays(loaded: List<BoardLoad.Loaded>, me: Identity?, records: List<CompletionRecord>, inWindow: (LeaderboardRef) -> Boolean): Int {
    if (me == null) return 0
    val solves = records.mapNotNull { record -> record.board()?.let { it to record } }.toMap()
    val counted = beyondPage(loaded, me, records)
    return loaded.count { load ->
        load.board !in counted &&
            viewerStanding(solves[load.board], inWindow(load.board), load.page, me.uid, load.order) is ViewerStanding.Unofficial
    }
}

/** The daily board this record's day is on; null for a puzzle with no day. */
private fun CompletionRecord.board(): LeaderboardRef? =
    (puzzleRef.key as? PuzzleKey.Daily)?.let { LeaderboardRef(puzzleRef.gameId.value, it.date) }
