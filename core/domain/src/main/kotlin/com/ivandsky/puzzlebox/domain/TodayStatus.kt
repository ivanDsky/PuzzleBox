package com.ivandsky.puzzlebox.domain

/** One game's state on its cadence day (spec A14). */
sealed interface TodayStatus {

    /** No record and no saved play for today (another day's saved play counts as none). */
    data object NotStarted : TodayStatus

    /** A play is saved for today; [elapsedMs] is its displayed clock at the last save. */
    data class InProgress(val elapsedMs: Long) : TodayStatus

    /** Today's completion record exists; [timeMs] is its solve time. */
    data class Solved(val timeMs: Long) : TodayStatus

    /** Nothing played, today's puzzle is not cached and the last [SyncTodayPuzzles] fetch failed. */
    data object Offline : TodayStatus
}
