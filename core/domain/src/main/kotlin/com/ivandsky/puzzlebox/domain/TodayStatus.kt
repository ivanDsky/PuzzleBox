package com.ivandsky.puzzlebox.domain

/** One game's state on its cadence day (spec A14). */
sealed interface TodayStatus {

    /** No record and no session kept for today (a stale kept session counts as none). */
    data object NotStarted : TodayStatus

    /** A session is kept for today; [elapsedMs] is its displayed clock at the last change. */
    data class InProgress(val elapsedMs: Long) : TodayStatus

    /** Today's completion record exists; [timeMs] is its solve time. */
    data class Solved(val timeMs: Long) : TodayStatus
}
