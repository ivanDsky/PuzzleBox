package com.ivandsky.puzzlebox.domain.linkedin

import com.ivandsky.puzzlebox.game.BestScore

/** What the day card shows of one game day's LinkedIn data: a status line and the connections board. */
data class LinkedInDayView(val status: LinkedInDayStatus, val board: LinkedInBoardView) {

    /**
     * The player's own imported LinkedIn result as a score, null while there is none; boards show and rank it in
     * place of the player's PuzzleBox time.
     */
    val ownScore: BestScore?
        get() = (status as? LinkedInDayStatus.Imported)?.let { BestScore(it.timeMs, it.hints) }
}
