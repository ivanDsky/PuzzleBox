package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.LegResult
import com.ivandsky.puzzlebox.data.match.MatchDraft
import com.ivandsky.puzzlebox.data.match.MatchLeg
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.data.match.ResultPostState
import com.ivandsky.puzzlebox.domain.FinishLeg
import com.ivandsky.puzzlebox.domain.LoadPastDay
import com.ivandsky.puzzlebox.domain.LoadSharedPuzzle
import com.ivandsky.puzzlebox.domain.ObserveMatch
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.gameui.DuelSideState
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.api.MatchLegKey
import com.ivandsky.puzzlebox.play.api.MatchLegResultKey
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeMatchLocalRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchResultOutbox
import com.ivandsky.puzzlebox.testing.repository.FakeReceivedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * A duel puzzle's result page over the rules-faithful match fakes and a real [ObserveMatch]: Ann (the
 * viewer) challenges and Bob claims a duel of three dailies, alpha, beta, alpha. The state is read, never
 * its text: the next action, the opponent's side, the posting state and the hero facts.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MatchLegResultViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val alpha = TestGames.alpha
    private val beta = TestGames.beta
    private val code = ShareCode.parse("K7Q2MX")!!
    private val ann = Player("ann", "Ann", null)
    private val bob = Player("bob", "Bob", null)

    private val server = FakeMatchRepository()
    private val outbox = FakeMatchResultOutbox()
    private val identity = FakeIdentityService(Identity(ann.uid, ann.name, null))
    private val fakes = FakeGames(listOf(alpha, beta))
    private val dispatcher = main.testDispatcher
    private val store = store()

    /** Collectors standing in for the screen: the state only runs while observed. */
    private val screen = CoroutineScope(dispatcher + Job())

    @After
    fun leaveScreen() {
        screen.cancel()
    }

    private fun store(): PlaySessionStore {
        val lane = WriteLane()
        val saved = FakeSavedPlayRepository()
        val daily = FakeDailyRepository()
        val calendar = FakeGameCalendar(LocalDate.of(2026, 11, 1))
        return PlaySessionStore(
            games = fakes.games,
            ui = fakes.registry,
            opening = SlotOpening(
                daily, LoadPastDay(daily, fakes.games), saved, calendar, fakes.games, lane, FakeReceivedPuzzleRepository(),
                server,
                LoadSharedPuzzle(fakes.games, FakeSharedPuzzleRepository(), FakeReceivedPuzzleRepository(), { Budget.Unlimited }, { Instant.EPOCH }),
                testDispatchers(dispatcher),
            ),
            writers = { SlotWriter(it, saved, lane, CoroutineScope(dispatcher)) },
            completions = FakeCompletionRepository(),
            finishLeg = FinishLeg(outbox, identity) { Instant.EPOCH },
            calendar = calendar,
            dispatchers = object : DispatcherProvider {
                override val main = dispatcher
                override val io = dispatcher
                override val default = dispatcher
            },
        )
    }

    private fun legs() = listOf(alpha, beta, alpha).mapIndexed { index, game ->
        MatchLeg(LegRef.Daily(game, LocalDate.of(2026, 11, 1 + index), index + 1), authorUid = null)
    }

    /** Ann creates the duel and Bob takes the seat; Ann is the viewer after. */
    private fun duel() = runBlocking {
        server.signedInUid = ann.uid
        server.create(MatchDraft(code, 24, ann, invitedUid = null, legs(), includeFriends = false, rematchOf = null))
        server.signedInUid = bob.uid
        server.claim(code, bob)
        server.signedInUid = ann.uid
    }

    private fun result(uid: String, leg: Int, timeMs: Long = 60_000, gaveUp: Boolean = false) =
        LegResult(uid, leg, if (gaveUp) 0 else timeMs, hints = 1, completedAtMs = 1_000, gaveUp = gaveUp)

    /** My result for [leg] in my outbox, in [state]; a Posted one also reaches the server, as the poster would. */
    private fun myRow(leg: Int, state: ResultPostState = ResultPostState.Pending, gaveUp: Boolean = false) = runBlocking {
        val row = result(ann.uid, leg, gaveUp = gaveUp)
        outbox.enqueue(code, row)
        outbox.update(code, leg, ann.uid, state, attempts = 1)
        if (state == ResultPostState.Posted) server.post(code, row)
    }

    /** Bob's poster lands his result for [leg] on the server. */
    private fun bobPlays(leg: Int, timeMs: Long = 70_000) = runBlocking {
        server.signedInUid = bob.uid
        server.post(code, result(bob.uid, leg, timeMs))
        server.signedInUid = ann.uid
    }

    private fun page(leg: Int = 0, timeMs: Long = 60_000, hints: Int = 1, gaveUp: Boolean = false): MatchLegResultViewModel {
        val observe = ObserveMatch(server, outbox, FakeMatchLocalRepository(), identity, fakes.games) { Instant.EPOCH }
        val key = MatchLegResultKey(code.value, leg, if (gaveUp) 0 else timeMs, if (gaveUp) 0 else hints, gaveUp)
        return MatchLegResultViewModel(key, store, observe, fakes.games, fakes.registry).also { vm ->
            screen.launch { vm.state.collect {} }
            dispatcher.scheduler.runCurrent()
        }
    }

    private fun MatchLegResultViewModel.ready() = state.value as MatchLegResultState.Ready

    @Test
    fun theNextPuzzleIsTheFirstOneWithoutAResultOfMine() {
        duel()
        myRow(0)
        myRow(2)
        val next = page(leg = 0).ready().next
        assertEquals(MatchLegKey(code.value, 1, beta.value), (next as LegNext.Puzzle).key)
    }

    @Test
    fun aGivenUpPuzzleCountsAsTaken() {
        duel()
        myRow(0)
        myRow(1, gaveUp = true)
        val next = page(leg = 0).ready().next
        assertEquals(MatchLegKey(code.value, 2, alpha.value), (next as LegNext.Puzzle).key)
    }

    @Test
    fun theLastPuzzleOffersTheRevealInsteadOfNext() {
        duel()
        myRow(0)
        myRow(1)
        myRow(2)
        assertEquals(LegNext.Reveal, page(leg = 2).ready().next)
    }

    @Test
    fun theOpponentSideFollowsTheirProgress() {
        duel()
        myRow(0)
        val vm = page(leg = 0)
        assertEquals(DuelSideState.NotPlayed, vm.ready().them.state)

        bobPlays(0)
        dispatcher.scheduler.runCurrent()
        assertEquals(DuelSideState.Sealed, vm.ready().them.state)
    }

    @Test
    fun aPostedResultOfMineTurnsSealedIntoTheirTimeWhenTheyHavePlayed() {
        duel()
        myRow(0)
        bobPlays(0, timeMs = 70_000)
        val vm = page(leg = 0)
        assertEquals(DuelSideState.Sealed, vm.ready().them.state)

        runBlocking {
            server.post(code, result(ann.uid, 0))
            outbox.update(code, 0, ann.uid, ResultPostState.Posted, attempts = 1)
        }
        dispatcher.scheduler.runCurrent()
        assertEquals(DuelSideState.Time(70_000, hints = 1), vm.ready().them.state)
        assertEquals(DuelSideState.Time(60_000, hints = 1), vm.ready().you.state)
    }

    @Test
    fun aPendingLateOrLocalOnlyRowMapsToItsPostingState() {
        duel()
        myRow(0)
        val vm = page(leg = 0)
        for (state in listOf(ResultPostState.Pending, ResultPostState.Late, ResultPostState.LocalOnly)) {
            runBlocking { outbox.update(code, 0, ann.uid, state, attempts = 1) }
            dispatcher.scheduler.runCurrent()
            assertEquals(state, vm.ready().posting)
        }
        runBlocking { outbox.update(code, 0, ann.uid, ResultPostState.Posted, attempts = 1) }
        dispatcher.scheduler.runCurrent()
        assertNull(vm.ready().posting)
    }

    @Test
    fun aSolveShowsTheHeroFactsFromTheKeyAndNoVerdictOnAColdStart() {
        duel()
        myRow(0)
        val hero = page(leg = 0, timeMs = 60_000, hints = 1).ready().hero
        assertNotNull(hero)
        assertEquals(60_000L, hero!!.timeMs)
        assertEquals(1, hero.hintsUsed)
        assertEquals(1, hero.puzzleNumber)
        assertNull(hero.verdict)
        assertNull(hero.previousBest)
        assertNull(hero.primary)
    }

    @Test
    fun aGiveUpShowsNoHeroFactsAndTheGaveUpSide() {
        duel()
        myRow(0, gaveUp = true)
        val ready = page(leg = 0, gaveUp = true).ready()
        assertNull(ready.hero)
        assertEquals(DuelSideState.GaveUp, ready.you.state)
    }

    @Test
    fun aMatchThatCannotBeShownIsUnavailable() {
        server.signedInUid = ann.uid
        val state = page(leg = 0).state.value
        assertTrue(state is MatchLegResultState.Unavailable)
    }

    @Test
    fun aGiveUpThatWonTheRaceAgainstTheSolveShowsTheGaveUpCard() {
        duel()
        myRow(0, gaveUp = true)
        val ready = page(leg = 0, timeMs = 60_000, hints = 1).ready()
        assertNull(ready.hero)
        assertEquals(DuelSideState.GaveUp, ready.you.state)
    }

    @Test
    fun theHeroTimeComesFromMyOutboxRowWhenItDiffersFromTheKey() {
        duel()
        myRow(0)
        val hero = page(leg = 0, timeMs = 45_000, hints = 3).ready().hero
        assertEquals(60_000L, hero!!.timeMs)
        assertEquals(1, hero.hintsUsed)
    }

    @Test
    fun theTimeMarginIsTheGapOfWholeSeconds() {
        assertEquals(1L, timeMarginSeconds(59_900, 60_100))
        assertEquals(1L, timeMarginSeconds(59_000, 60_900))
        assertEquals(1L, timeMarginSeconds(60_900, 59_000))
        assertEquals(3L, timeMarginSeconds(70_000, 67_000))
    }
}
