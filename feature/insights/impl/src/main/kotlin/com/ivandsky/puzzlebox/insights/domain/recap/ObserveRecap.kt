package com.ivandsky.puzzlebox.insights.domain.recap

import com.ivandsky.puzzlebox.common.log.FailureLog
import com.ivandsky.puzzlebox.common.log.LogcatFailureLog
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.guardedAsk
import com.ivandsky.puzzlebox.game.GameRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The cards of one period's recap, in story order, read from the full game history ([ObserveGameHistory]) so a recap
 * counts what the game page counts. Every emission is the whole story at that moment; the story's view model takes the
 * first and keeps it, so a result that lands while a recap is open never moves a page under the player.
 *
 * The recap chain asks each [RecapFactProvider] in list order, with no time limit: they read only local data, so page
 * 1 never waits on the network. A provider that throws is logged and adds no card; the rest still show. A period with
 * nothing to say is an empty list.
 */
internal class ObserveRecap(
    private val observeGameHistory: ObserveGameHistory,
    private val computePeriodFacts: ComputePeriodFacts,
    private val registry: GameRegistry,
    private val providers: List<RecapFactProvider> = RecapFactProviders,
    private val failures: FailureLog = LogcatFailureLog,
) {

    operator fun invoke(period: RecapPeriod): Flow<List<RecapCard>> =
        observeGameHistory().map { history ->
            val input = RecapInput(
                facts = computePeriodFacts(history, period),
                games = registry.all.map { it.definition.id },
            )
            providers.flatMap { provider ->
                guardedAsk(failures, TAG, "recap provider ${provider::class.simpleName}") {
                    provider.cards(input)
                }.orEmpty()
            }
        }

    private companion object {
        const val TAG = "ObserveRecap"
    }
}
