package com.ivandsky.puzzlebox.play

import java.time.LocalDate

/**
 * The day a past-day Result continues on: [from]'s game after it. The first day after [from], up to [yesterday], that is
 * inside [available] (the days the game has a puzzle for) and not in [solved]; else the nearest such day before [from];
 * else null. A [from] outside [available] has no archive to continue.
 */
internal fun nextArchiveDay(
    solved: Set<LocalDate>,
    available: ClosedRange<LocalDate>,
    from: LocalDate,
    yesterday: LocalDate,
): LocalDate? {
    if (from !in available) return null
    val last = minOf(available.endInclusive, yesterday)
    generateSequence(from.plusDays(1)) { it.plusDays(1) }.takeWhile { it <= last }.firstOrNull { it !in solved }?.let { return it }
    return generateSequence(from.minusDays(1)) { it.minusDays(1) }.takeWhile { it >= available.start }.firstOrNull { it !in solved }
}
