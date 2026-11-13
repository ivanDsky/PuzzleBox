package com.ivandsky.puzzlebox.domain

import java.time.LocalDate

/**
 * The freeze side of a streak. Derived from the solved days on every replay and never stored, so
 * [frozenDays] are provisional: a day that arrives later (an import, a sync) can unfreeze them.
 * [daysToNext] is null at the cap of 5 [held], where a finished ladder step banks nothing.
 */
data class StreakFreezes(
    val held: Int,
    val frozenDays: Set<LocalDate>,
    val daysToNext: Int?,
    val lastSolve: Moment?,
) {
    /**
     * What the newest solved day did to the freezes: the missed days just before it that it found
     * [spent] (empty when the gap broke the streak instead), and whether it [earned] one. An
     * [earned] at the cap is false, as nothing was banked.
     */
    data class Moment(val day: LocalDate, val spent: List<LocalDate>, val earned: Boolean)
}
