package com.ivandsky.puzzlebox.insights.domain.recap

import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.domain.GameHistoryDay
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.isSolvedDaily
import com.ivandsky.puzzlebox.game.GameRegistry
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * The periods that have a recap, years first and each kind newest first. A month is listed once it has a counted solve,
 * imports included, and not before (A2). A year is listed the same way, except that the current year waits for Dec 20
 * (A3); a past year with counted solves, such as 2024 or 2025 from imports, is listed at any time (A4). A period is the
 * puzzle day's, so a 2025 puzzle imported today opens the 2025 month and year it was played in. The flag that keeps
 * years dark is the caller's to apply.
 *
 * [due] is what Today offers right now. Both read the puzzle day from [calendar] — the first registered game's, as
 * every app-level "today" does — never the device's date.
 */
internal class ObserveRecapAvailability(
    private val observeGameHistory: ObserveGameHistory,
    private val calendar: GameCalendar,
    private val registry: GameRegistry,
) {

    operator fun invoke(): Flow<List<RecapPeriod>> =
        combine(counted(), today()) { solved, today ->
            val years = solved.map { it.day.year }.distinct().filter { opensYear(it, today) }.sortedDescending()
            val months = solved.mapTo(sortedSetOf(compareByDescending<YearMonth> { it })) { YearMonth.from(it.day) }
            years.map { RecapPeriod.Year(it) } + months.map { RecapPeriod.Month(it) }
        }.distinctUntilChanged()

    /**
     * What Today offers now, the year first: the year just ended on Jan 1–7, then the month just ended on days 1–3,
     * each once that puzzle day has a counted solve and only when the period has a recap of its own (A2, A3). The
     * caller shows one at a time and the next after the first is dismissed. From day 4 (Jan 8 for a year) nothing is
     * due, so a recap that first ships after its window gets no late card and stays reachable from the hub (A4). Emits
     * again when the day's first solve lands and at each puzzle-day rollover. Each period carries its [summarize]d
     * counts, from the same counted history, so Today pays a group-by and no second read.
     */
    fun due(): Flow<List<DuePeriod>> =
        combine(counted(), today()) { solved, today ->
            if (today == null || solved.none { it.day == today }) return@combine emptyList<DuePeriod>()
            val year = RecapPeriod.Year(today.year - 1).takeIf {
                today.month == Month.JANUARY && today.dayOfMonth <= YEAR_DUE_DAYS && solved.any { day -> day.day in it }
            }
            val ended = YearMonth.from(today).minusMonths(1)
            val month = RecapPeriod.Month(ended).takeIf {
                today.dayOfMonth <= MONTH_DUE_DAYS && solved.any { day -> YearMonth.from(day.day) == ended }
            }
            listOfNotNull(year, month).map { DuePeriod(it, summarize(it, solved)) }
        }.distinctUntilChanged()

    private fun counted(): Flow<List<GameHistoryDay>> = observeGameHistory().map { history -> history.filter { it.isSolvedDaily() } }

    /** The puzzle day; null while no game is registered, so nothing is due and no current year opens. */
    private fun today(): Flow<LocalDate?> {
        val game = registry.all.firstOrNull()?.definition?.id ?: return flowOf(null)
        return calendar.observeToday(game)
    }

    /** A past year opens always; the current year from Dec [OPEN_DAY]; a year that has not begun never. */
    private fun opensYear(year: Int, today: LocalDate?): Boolean = when {
        today == null -> false
        year < today.year -> true
        year == today.year -> !today.isBefore(LocalDate.of(year, Month.DECEMBER, OPEN_DAY))
        else -> false
    }

    private companion object {
        /** The month Today card's window: days 1–3 of the month after the one it recaps. */
        const val MONTH_DUE_DAYS = 3

        /** The year Today card's window: Jan 1–7 for the year just ended. */
        const val YEAR_DUE_DAYS = 7

        /** The day of December the current year's recap opens (A3). */
        const val OPEN_DAY = 20
    }
}
