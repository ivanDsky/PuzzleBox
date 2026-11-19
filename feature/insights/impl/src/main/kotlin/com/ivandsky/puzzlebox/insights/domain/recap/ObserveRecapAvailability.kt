package com.ivandsky.puzzlebox.insights.domain.recap

import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.isSolvedDaily
import com.ivandsky.puzzlebox.game.GameRegistry
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * The months that have a recap, newest first: every month with at least one counted solve, imports included, and none
 * without (A2). A month is the puzzle day's, so a 2025 puzzle imported today opens the 2025 month it was played in.
 * The current month is listed as soon as it has a solve. Years arrive with the yearly recap.
 *
 * [due] is the one of them Today offers right now. It reads the puzzle day from [calendar] — the first registered
 * game's, as every app-level "today" does — never the device's date.
 */
internal class ObserveRecapAvailability(
    private val observeGameHistory: ObserveGameHistory,
    private val calendar: GameCalendar,
    private val registry: GameRegistry,
) {

    operator fun invoke(): Flow<List<RecapPeriod>> =
        observeGameHistory().map { history ->
            history.filter { it.isSolvedDaily() }
                .mapTo(sortedSetOf(compareByDescending<YearMonth> { it })) { YearMonth.from(it.day) }
                .map { RecapPeriod.Month(it) }
        }.distinctUntilChanged()

    /**
     * The month just ended while Today may show its recap, else null: on days 1–3 of a month, once that puzzle day has
     * a counted solve, and only when the month just ended has a recap of its own (A2). From day 4 nothing is due, so a
     * recap that first ships after its window gets no late card and stays reachable from the hub (A4). Emits again
     * when the day's first solve lands and at each puzzle-day rollover.
     */
    fun due(): Flow<RecapPeriod.Month?> {
        val game = registry.all.firstOrNull()?.definition?.id ?: return flowOf(null)
        return combine(observeGameHistory(), calendar.observeToday(game)) { history, today ->
            val ended = YearMonth.from(today).minusMonths(1)
            val solved = history.filter { it.isSolvedDaily() }
            if (today.dayOfMonth <= DUE_DAYS && solved.any { it.day == today } && solved.any { YearMonth.from(it.day) == ended }) {
                RecapPeriod.Month(ended)
            } else {
                null
            }
        }.distinctUntilChanged()
    }

    private companion object {
        /** The Today card's window: days 1–3 of the month after the one it recaps. */
        const val DUE_DAYS = 3
    }
}
