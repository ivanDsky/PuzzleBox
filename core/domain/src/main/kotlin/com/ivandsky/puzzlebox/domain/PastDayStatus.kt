package com.ivandsky.puzzlebox.domain

/** One past day's state on this device (spec A20); precedence Solved > InProgress > Stored. */
sealed interface PastDayStatus {

    /** The day's completion record exists; [timeMs] is its solve time. */
    data class Solved(val timeMs: Long) : PastDayStatus

    /** A play is saved for the day; [elapsedMs] is its displayed clock at the last save. */
    data class InProgress(val elapsedMs: Long) : PastDayStatus

    /** The puzzle is on the device and was never played. */
    data object Stored : PastDayStatus

    /** The puzzle is not on the device; opening it fetches. */
    data object NeedsConnection : PastDayStatus
}
