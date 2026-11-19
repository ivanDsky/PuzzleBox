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
 * The one network fact, the friends standing, is resolved outside those providers ([ResolveFriendsStanding]). The
 * first emission is the whole local story, out as soon as the history is read, with the friends card already in its
 * slot as [FriendsStanding.Reading] (or [FriendsStanding.Settling]) when it applies, so the story has the length it will keep. When the read answers, a
 * second emission is the same story with that card settled in place; it is left out when nothing changed. Only an
 * answer that finds no friends takes the card out again.
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
        val played = local.inGameOrder(facts.solvedByGame).map { (game, _) -> game }
        val begun = resolveFriends.start(period, played)
        val localStory = story(local.copy(friends = begun))
        emit(localStory)
        if (begun == FriendsStanding.NotApplicable) return@flow
        val settledStory = story(local.copy(friends = resolveFriends(period, played)))
        if (settledStory != localStory) emit(settledStory)
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
