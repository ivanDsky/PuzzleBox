package com.ivandsky.puzzlebox.gameui

import java.time.LocalDate

/**
 * One column of a [WeekStrip] (spec A18): the puzzle [date], the brands of the games
 * solved on it (one [DayRing] segment each), and whether it is today. A pure value:
 * callers build the week from their own data, so no domain logic lives in the UI.
 *
 * [frozen] marks a day a streak freeze covers (spec A13). Orthogonal to solved: ring
 * segments stay, only the day number gives way to the snowflake.
 */
data class WeekDay(
    val date: LocalDate,
    val solved: List<GameBrand>,
    val isToday: Boolean,
    val frozen: Boolean = false,
)
