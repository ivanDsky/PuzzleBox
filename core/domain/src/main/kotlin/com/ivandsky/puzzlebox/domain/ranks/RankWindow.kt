package com.ivandsky.puzzlebox.domain.ranks

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * The puzzle-day span an Overall or per-game board is read over (A13). The anchor names the
 * span: a week is the Mon–Sun week containing it, a month is its calendar month.
 */
sealed interface RankWindow {
    val anchor: LocalDate

    data class Day(override val anchor: LocalDate) : RankWindow

    data class Week(override val anchor: LocalDate) : RankWindow

    data class Month(override val anchor: LocalDate) : RankWindow
}

/**
 * The window's puzzle days in ascending order, clipped at [today]: a future day has no board,
 * so a window that lies wholly ahead of [today] has no days.
 */
fun RankWindow.days(today: LocalDate): List<LocalDate> {
    val (first, last) = when (this) {
        is RankWindow.Day -> anchor to anchor
        is RankWindow.Week -> {
            val monday = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            monday to monday.plusDays(6)
        }
        is RankWindow.Month -> anchor.withDayOfMonth(1) to anchor.with(TemporalAdjusters.lastDayOfMonth())
    }
    val end = minOf(last, today)
    return generateSequence(first) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.toList()
}
