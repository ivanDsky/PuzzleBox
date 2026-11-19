package com.ivandsky.puzzlebox.insights.domain.recap

import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.isSolvedDaily
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * The months that have a recap, newest first: every month with at least one counted solve, imports included, and none
 * without (A2). A month is the puzzle day's, so a 2025 puzzle imported today opens the 2025 month it was played in.
 * The current month is listed as soon as it has a solve. Years arrive with the yearly recap.
 */
internal class ObserveRecapAvailability(private val observeGameHistory: ObserveGameHistory) {

    operator fun invoke(): Flow<List<RecapPeriod>> =
        observeGameHistory().map { history ->
            history.filter { it.isSolvedDaily() }
                .mapTo(sortedSetOf(compareByDescending<YearMonth> { it })) { YearMonth.from(it.day) }
                .map { RecapPeriod.Month(it) }
        }.distinctUntilChanged()
}
