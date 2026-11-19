package com.ivandsky.puzzlebox.insights.domain.stats

import com.ivandsky.puzzlebox.common.log.FailureLog
import com.ivandsky.puzzlebox.common.log.LogcatFailureLog
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.isSolvedDaily
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.insights.domain.recap.ComputePeriodFacts
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * The stats page of one [scope] over one [StatsRange], read from the full game history ([ObserveGameHistory]) so the
 * page counts what the game page and the recap count. The range's days come from the puzzle day of [calendar], the
 * scope game's, or the first registered game's for every game, never the device's date (A10).
 *
 * The page is one fold of the history ([ComputePeriodFacts]) that every section reads (A16); each
 * [StatsSectionProvider] then answers with its own flow of [StatsSection]. The page combines them, so a section that is
 * still loading or has failed shows its own state while the others are ready. A provider that throws is logged and its
 * section is left out; one that needs the network maps its own failure to [SectionState.Unavailable] instead. The page
 * with no section is empty: the range has nothing to show.
 */
internal class ObserveStatsPage(
    private val observeGameHistory: ObserveGameHistory,
    private val computePeriodFacts: ComputePeriodFacts,
    private val calendar: GameCalendar,
    private val registry: GameRegistry,
    vsFriends: StatsSectionProvider,
    private val providers: List<StatsSectionProvider> = statsSectionProviders(vsFriends),
    private val failures: FailureLog = LogcatFailureLog,
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(scope: GameId?, range: StatsRange): Flow<StatsPage> {
        val clockGame = scope ?: registry.all.firstOrNull()?.definition?.id ?: return flowOf(StatsPage(scope, range, emptyList()))
        val games = registry.all.map { it.definition.id }
        return combine(observeGameHistory(), calendar.observeToday(clockGame)) { history, today ->
            val firstSolve = history.filter { it.isSolvedDaily() && (scope == null || it.gameId == scope) }.minOfOrNull { it.day }
            StatsInput(range, computePeriodFacts(history, range.span(today, firstSolve), scope), games, today)
        }.flatMapLatest { input ->
            if (providers.isEmpty()) {
                flowOf(StatsPage(scope, range, emptyList()))
            } else {
                combine(providers.map { guarded(it, input) }) { sections -> StatsPage(scope, range, sections.filterNotNull()) }
            }
        }
    }

    private fun guarded(provider: StatsSectionProvider, input: StatsInput): Flow<StatsSection?> =
        flow { emitAll(provider.section(input)) }.catch { cause ->
            failures.warn(TAG, "stats section ${provider::class.simpleName} failed", cause)
            emit(null)
        }

    private companion object {
        const val TAG = "ObserveStatsPage"
    }
}
