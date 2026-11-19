package com.ivandsky.puzzlebox.play.domain.moments

/** The one ladder of "sub-X" times every game shares (L1): 5:00, 3:00, 2:00, 1:30, 1:00, 0:45, 0:30, 0:20, 0:10. */
internal object SubXThresholds {

    /** Loosest first. */
    private val ladderMs: List<Long> = listOf(300, 180, 120, 90, 60, 45, 30, 20, 10).map { it * 1_000L }

    /** The tightest threshold [timeMs] is under, or null when it is under none. A time on a threshold is not under it. */
    fun tightestUnder(timeMs: Long): Long? = ladderMs.lastOrNull { timeMs < it }
}
