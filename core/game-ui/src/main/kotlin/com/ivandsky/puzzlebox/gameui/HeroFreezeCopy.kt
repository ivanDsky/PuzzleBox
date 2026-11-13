package com.ivandsky.puzzlebox.gameui

import com.ivandsky.puzzlebox.domain.StreakFreezes

/**
 * The streak card's sub line once freezes are on: the days left to earn the next freeze, or at
 * the cap (no days to count) the freezes held. Replaces [heroStreakSub], which only speaks of the streak.
 */
internal fun heroFreezeSub(freezes: StreakFreezes): String = when (val days = freezes.daysToNext) {
    null -> "${freezeCountLabel(freezes.held)} held"
    1 -> "1 more day to earn a freeze"
    else -> "$days more days to earn a freeze"
}
