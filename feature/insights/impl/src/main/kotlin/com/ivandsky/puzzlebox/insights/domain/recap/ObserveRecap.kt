package com.ivandsky.puzzlebox.insights.domain.recap

import com.ivandsky.puzzlebox.common.log.FailureLog
import com.ivandsky.puzzlebox.common.log.LogcatFailureLog
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.guardedAsk
import com.ivandsky.puzzlebox.game.GameRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

/**
 * The cards of one period's recap, in story order, from one read of the full game history ([ObserveGameHistory]) so a
 * recap counts what the game page counts. The recap is computed once per open: the history is not followed, so a
 * solve that lands while a recap is open changes nothing.
 *
 * The recap chain asks each [RecapFactProvider] in list order, with no time limit: they read only local data, so page
 * 1 never waits on the network. A provider that throws is logged and adds no card; the rest still show. A period with
 * nothing to say is an empty list.
 *
 * The one network fact, the friends standing, is resolved outside those providers ([ResolveFriendsStanding]) and the
 * flow has two emissions for it. The first is the local story, with the friends answer still
 * [FriendsStanding.Reading], so it is out as soon as the history is read. The second, when the answer lands, is the
 * same story with the friends card in its place at the end; it is left out when the answer adds no card (A12), so a
 * period with nothing to add emits once. Everything before the friends card is identical in both, so a story that
 * keeps the first and swaps in the second moves no page: the card only joins at the end.
 */
internal class ObserveRecap(
    private val observeGameHistory: ObserveGameHistory,
    private val computePeriodFacts: ComputePeriodFacts,
    private val registry: GameRegistry,
    private val resolveFriends: ResolveFriendsStanding,
    private val providers: List<RecapFactProvider> = RecapFactProviders,
    private val failures: FailureLog = LogcatFailureLog,
) {

    operator fun invoke(period: RecapPeriod): Flow<List<RecapCard>> = flow {
        val facts = computePeriodFacts(observeGameHistory().first(), period)
        val local = RecapInput(facts = facts, games = registry.all.map { it.definition.id })
        val localStory = story(local)
        emit(localStory)
        val played = local.inGameOrder(facts.solvedByGame).map { (game, _) -> game }
        val fullStory = story(local.copy(friends = resolveFriends(period, played)))
        if (fullStory != localStory) emit(fullStory)
    }

    private suspend fun story(input: RecapInput): List<RecapCard> =
        providers.flatMap { provider ->
            guardedAsk(failures, TAG, "recap provider ${provider::class.simpleName}") {
                provider.cards(input)
            }.orEmpty()
        }

    private companion object {
        const val TAG = "ObserveRecap"
    }
}
