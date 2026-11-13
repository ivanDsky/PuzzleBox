package com.ivandsky.puzzlebox.domain

/**
 * One streak's state at today's puzzle day, as [ComputeStreak.invoke] replays it. [freezes] is
 * null while the `StreakFreeze` flag is off: surfaces hide every freeze element on that null and
 * never read the flag themselves.
 */
data class StreakLedger(
    val current: Int,
    val best: Int,
    val freezes: StreakFreezes? = null,
)
