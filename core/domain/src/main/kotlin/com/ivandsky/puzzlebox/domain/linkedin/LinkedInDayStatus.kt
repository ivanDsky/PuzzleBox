package com.ivandsky.puzzlebox.domain.linkedin

import com.ivandsky.puzzlebox.game.linkedin.LinkedInRead

/** The one thing the day card's LinkedIn status line says. */
sealed interface LinkedInDayStatus {
    data object NotConnected : LinkedInDayStatus
    data object Loading : LinkedInDayStatus
    /** The player's own LinkedIn result: its [timeMs] and the [hints] it used (a hint-free solve has none). */
    data class Imported(val timeMs: Long, val hints: Int = 0) : LinkedInDayStatus
    data object DidNotPlay : LinkedInDayStatus
    data object Offline : LinkedInDayStatus
    data object SessionExpired : LinkedInDayStatus
    data class Paused(val kind: LinkedInRead.Rejected.Kind) : LinkedInDayStatus
    data object OverBudget : LinkedInDayStatus

    /** Connected, with nothing to say yet. */
    data object Idle : LinkedInDayStatus
}
