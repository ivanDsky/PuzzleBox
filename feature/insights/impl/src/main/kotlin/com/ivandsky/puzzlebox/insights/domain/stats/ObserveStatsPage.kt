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
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.shareIn
import kotlin.coroutines.coroutineContext

/**
 * The stats page of one [scope] over one [StatsRange], read from the full game history ([ObserveGameHistory]) so the
 * page counts what the game page and the recap count. The range's days come from the puzzle day of [calendar], the
 * scope game's, or the first registered game's for every game, never the device's date (A10).
 *
 * The page is one fold of the history ([ComputePeriodFacts]) that every section reads (A16); each
 * [StatsSectionProvider] then answers with its own flow of [StatsSection]. The page combines them, so a section that is
 * still loading or has failed shows its own state while the others are ready. A section is asked again only when its
 * [StatsSectionProvider.readKey] changes, so the vs friends read is not repeated by a solve that leaves its window as it was.
 * With [allTimeRecords] the Records section reads the scope's all-time facts (a second fold, only when the range is not
 * All and there is a solve), so a range cuts every other section but not the records. A provider that throws is logged and its
 * section is left out; one that needs the network maps its own failure to [SectionState.Unavailable] instead. The page
 * with no section is empty: the range has nothing to show.
 */
internal class ObserveStatsPage(
    private val observeGameHistory: ObserveGameHistory,
    private val computePeriodFacts: ComputePeriodFacts,
    private val calendar: GameCalendar,
    private val registry: GameRegistry,
    rankHistory: StatsSectionProvider,
    vsFriends: StatsSectionProvider,
    private val providers: List<StatsSectionProvider> = statsSectionProviders(rankHistory, vsFriends),
    private val failures: FailureLog = LogcatFailureLog,
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(scope: GameId?, range: StatsRange, allTimeRecords: Boolean = false): Flow<StatsPage> {
        val clockGame = scope ?: registry.all.firstOrNull()?.definition?.id ?: return flowOf(StatsPage(scope, range, emptyList()))
        val games = registry.all.map { it.definition.id }
        if (providers.isEmpty()) return flowOf(StatsPage(scope, range, emptyList()))
        return flow {
            coroutineScope {
                val inputs = combine(observeGameHistory(), calendar.observeToday(clockGame)) { history, today ->
                    val firstSolve = history.filter { it.isSolvedDaily() && (scope == null || it.gameId == scope) }.minOfOrNull { it.day }
                    val facts = computePeriodFacts(history, range.span(today, firstSolve), scope)
                    val recordFacts = when {
                        !allTimeRecords || range == StatsRange.All || firstSolve == null -> facts
                        else -> computePeriodFacts(history, DayRange(firstSolve, today), scope)
                    }
                    StatsInput(range, facts, games, today, recordFacts)
                }.shareIn(this, SharingStarted.Eagerly, replay = 1)
                emitAll(combine(providers.map { section(it, inputs) }) { sections -> StatsPage(scope, range, sections.filterNotNull()) })
                coroutineContext.cancelChildren()
            }
        }
    }

    /** [provider]'s sections: asked again only when its [StatsSectionProvider.readKey] changes, the older read cancelled. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun section(provider: StatsSectionProvider, inputs: Flow<StatsInput>): Flow<StatsSection?> =
        inputs.distinctUntilChangedBy(provider::readKey).flatMapLatest { guarded(provider, it) }

    private fun guarded(provider: StatsSectionProvider, input: StatsInput): Flow<StatsSection?> =
        flow { emitAll(provider.section(input)) }.catch { cause ->
            failures.warn(TAG, "stats section ${provider::class.simpleName} failed", cause)
            emit(null)
        }

    private companion object {
        const val TAG = "ObserveStatsPage"
    }
}
