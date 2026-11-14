package com.ivandsky.puzzlebox.domain

/** One streak's state at today's puzzle day, as [ComputeStreak.invoke] replays it. */
data class StreakLedger(
    val current: Int,
    val best: Int,
    val freezes: StreakFreezes,
)
