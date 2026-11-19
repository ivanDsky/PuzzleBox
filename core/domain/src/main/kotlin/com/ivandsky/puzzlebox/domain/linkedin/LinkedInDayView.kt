package com.ivandsky.puzzlebox.domain.linkedin

import com.ivandsky.puzzlebox.data.linkedin.LinkedInOwner
import com.ivandsky.puzzlebox.game.BestScore

/**
 * What the day card and the boards show of one game day's LinkedIn data: its sync status and the connections board. [owner] is the
 * player's own LinkedIn row (name and photo) from the board the phone read, never one of the connections; null while
 * no board that names them was read. [boardRead] says when that board was read; null with no board.
 *
 * [ownScore] is the player's own imported LinkedIn result as a score, null while there is none; boards show and rank it
 * in place of the player's PuzzleBox time. It is the import the phone holds, whatever [status] says: a pause, a
 * disconnect or an unreachable LinkedIn changes the status line, never whether an import counts. Left out, it is the
 * import [status] itself carries.
 */
data class LinkedInDayView(
    val status: LinkedInDayStatus,
    val board: LinkedInBoardView,
    val owner: LinkedInOwner? = null,
    val boardRead: LinkedInBoardRead? = null,
    val ownScore: BestScore? = (status as? LinkedInDayStatus.Imported)?.let { BestScore(it.timeMs, it.hints) },
)
