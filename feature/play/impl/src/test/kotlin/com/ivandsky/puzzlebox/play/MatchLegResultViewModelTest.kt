package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.LegResult
import com.ivandsky.puzzlebox.data.match.MatchDraft
import com.ivandsky.puzzlebox.data.match.MatchLeg
import com.ivandsky.puzzlebox.data.match.MatchFetch
import com.ivandsky.puzzlebox.data.match.MatchRepository
import com.ivandsky.puzzlebox.data.match.ResultRead
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.data.match.ResultPostState
import com.ivandsky.puzzlebox.domain.FinishLeg
import com.ivandsky.puzzlebox.domain.LoadPastDay
import com.ivandsky.puzzlebox.domain.LoadCustomReplay
import com.ivandsky.puzzlebox.domain.LoadDailyReplay
import com.ivandsky.puzzlebox.domain.LoadLegReplay
import com.ivandsky.puzzlebox.domain.LoadPlayerReplay
import com.ivandsky.puzzlebox.domain.LoadSharedPuzzle
import com.ivandsky.puzzlebox.domain.LoadSolveReplay
import com.ivandsky.puzzlebox.domain.ObserveMatch
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.replay.ReplayOutcome
import com.ivandsky.puzzlebox.gameui.DuelSideState
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.replay.ReplaySectionState
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.model.SolveReplay
import com.ivandsky.puzzlebox.play.api.MatchLegKey
import com.ivandsky.puzzlebox.play.api.MatchLegResultKey
import com.ivandsky.puzzlebox.play.api.ReplayKey
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.replay.ReplayFixtures
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyReplayRepository
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.coroutines.CoroutineContext
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * A duel puzzle's result page over the rules-faithful match fakes and a real [ObserveMatch]: Ann (the
 * viewer) challenges and Bob claims a duel of three dailies, alpha, beta and the replay-capable fixture game. The
 * state is read, never its text: the next action, the opponent's side, the posting state, the hero facts and my
 * own replay section, which never asks for the opponent's.
 */
class MatchLegResultViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val alpha = TestGames.alpha
    private val beta = TestGames.beta
    private val code = ShareCode.parse("K7Q2MX")!!
    private val ann = Player("ann", "Ann", null)
    private val bob = Player("bob", "Bob", null)

    private val fixture = ReplayFixtures.GAME
    private val server = FakeMatchRepository()
    private val theirReads = mutableListOf<String>()
    private var replayLookups = 0
    private val matches = object : MatchRepository by server {
        override fun observe(code: ShareCode): Flow<MatchFetch> {
            replayLookups++
            return server.observe(code)
        }

        override suspend fun readResult(code: ShareCode, legIndex: Int, uid: String): ResultRead {
            theirReads += "$code/$legIndex/$uid"
            return server.readResult(code, legIndex, uid)
        }
    }
    private val outbox = FakeMatchResultOutbox()
    private val identity = FakeIdentityService(Identity(ann.uid, ann.name, null))
    private val fakes = FakeGames(listOf(alpha, beta))
    private val games = GameRegistry(fakes.games.all + replayCapableGames().all)
    private val fixtureUi = FakePlayUi(fixture).apply { replayBoard = ReplayGoldenBoard }
    private val uis = GamePlayRegistry(fakes.uis.values + fixtureUi)
    private val daily = FakeDailyRepository().apply {
        setCached(fixture, FIXTURE_DAY, TestGames.puzzle(fixture))
    }
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
        val calendar = FakeGameCalendar(LocalDate.of(2026, 11, 1))
        return PlaySessionStore(
            games = games,
            ui = uis,
            opening = SlotOpening(
                daily, LoadPastDay(daily, games), saved, calendar, games, lane, FakeReceivedPuzzleRepository(),
                server,
                LoadSharedPuzzle(games, FakeSharedPuzzleRepository(), FakeReceivedPuzzleRepository(), { Budget.Unlimited }, { Instant.EPOCH }),
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

    private fun legs() = listOf(alpha, beta, fixture).mapIndexed { index, game ->
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

    private fun result(uid: String, leg: Int, timeMs: Long = 60_000, gaveUp: Boolean = false, replay: SolveReplay? = null) =
        LegResult(uid, leg, if (gaveUp) 0 else timeMs, hints = 1, completedAtMs = 1_000, gaveUp = gaveUp, replay = replay)

    /** My result for [leg] in my outbox, in [state]; a Posted one also reaches the server, as the poster would. */
    private fun myRow(
        leg: Int,
        state: ResultPostState = ResultPostState.Pending,
        gaveUp: Boolean = false,
        replay: SolveReplay? = null,
    ) = runBlocking {
        val row = result(ann.uid, leg, gaveUp = gaveUp, replay = replay)
        outbox.enqueue(code, row)
        outbox.update(code, leg, ann.uid, state, attempts = 1)
        if (state == ResultPostState.Posted) server.post(code, row)
    }

    /** Bob's poster lands his result for [leg] on the server. */
    private fun bobPlays(leg: Int, timeMs: Long = 70_000, replay: SolveReplay? = null) = runBlocking {
        server.signedInUid = bob.uid
        server.post(code, result(bob.uid, leg, timeMs, replay = replay))
        server.signedInUid = ann.uid
    }

    private fun page(
        leg: Int = 0,
        timeMs: Long = 60_000,
        hints: Int = 1,
        gaveUp: Boolean = false,
        decode: DispatcherProvider = testDispatchers(dispatcher),
    ): MatchLegResultViewModel {
        val observe = ObserveMatch(server, outbox, FakeMatchLocalRepository(), identity, games) { Instant.EPOCH }
        val key = MatchLegResultKey(code.value, leg, if (gaveUp) 0 else timeMs, if (gaveUp) 0 else hints, gaveUp)
        return MatchLegResultViewModel(key, store, observe, games, uis, loadReplay(decode = decode)).also { vm ->
            screen.launch { vm.state.collect {} }
            dispatcher.scheduler.runCurrent()
        }
    }

    private fun loadReplay(
        flags: FakeReleaseFlags = FakeReleaseFlags(ReleaseFlag.FullReplay),
        decode: DispatcherProvider = testDispatchers(dispatcher),
    ): LoadSolveReplay {
        val received = FakeReceivedPuzzleRepository()
        val loadShared = LoadSharedPuzzle(games, FakeSharedPuzzleRepository(), received, { Budget.Unlimited }, { Instant.EPOCH })
        val completions = FakeCompletionRepository()
        return LoadSolveReplay(
            flags = flags,
            games = games,
            daily = LoadDailyReplay(completions, daily),
            custom = LoadCustomReplay(completions, received, loadShared),
            leg = LoadLegReplay(matches, outbox, identity, daily, received, loadShared),
            player = LoadPlayerReplay(FakeDailyReplayRepository(), daily),
            completions = completions,
            dispatchers = decode,
        )
    }

    /** Holds every piece of work sent to it until [release]: a decode that stays suspended. */
    private class GatedDispatcher : CoroutineDispatcher() {
        private val held = ArrayList<Runnable>()
        private var open = false
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            if (open) block.run() else held += block
        }
        fun release() {
            open = true
            held.toList().also { held.clear() }.forEach { it.run() }
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
        assertEquals(MatchLegKey(code.value, 2, fixture.value), (next as LegNext.Puzzle).key)
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
    fun aSolvedLegWithAReplayShowsMyReplayAndOpensMyLeg() {
        duel()
        myRow(2, replay = ReplayFixtures.sealed(ReplayFixtures.SHORT_MOVES))
        val ready = page(leg = 2).ready()
        val section = ready.replay as ReplaySectionState.Ready
        assertEquals(ReplayFixtures.SHORT_MOVES, section.timeline.moveTimesMs.size)
        assertEquals(ReplayKey.leg(fixture.value, code.value, 2), ready.replayKey)
        assertEquals(ReplayKey.MY_LEG, ready.replayKey.kind)
        assertEquals(fixtureUi, ready.playUi)
    }

    @Test
    fun aGaveUpLegWithAReplayShowsItsGaveUpReplayAndNoHero() {
        duel()
        myRow(2, gaveUp = true, replay = ReplayFixtures.sealed(ReplayFixtures.SHORT_MOVES, outcome = ReplayOutcome.GaveUp))
        val ready = page(leg = 2, gaveUp = true).ready()
        assertNull(ready.hero)
        val section = ready.replay as ReplaySectionState.Ready
        assertEquals(ReplayFixtures.SHORT_MOVES, section.timeline.moveTimesMs.size)
    }

    @Test
    fun aLegWithoutAReplayShowsNone() {
        duel()
        myRow(2)
        assertEquals(ReplaySectionState.Hidden, page(leg = 2).ready().replay)
    }

    @Test
    fun theReplayWaitsForMyResultToBeQueued() {
        duel()
        val vm = page(leg = 2)
        assertEquals(ReplaySectionState.Hidden, vm.ready().replay)
        assertEquals(0, replayLookups)

        myRow(2, replay = ReplayFixtures.sealed(ReplayFixtures.SHORT_MOVES))
        dispatcher.scheduler.runCurrent()
        assertTrue(vm.ready().replay is ReplaySectionState.Ready)
        assertEquals(1, replayLookups)
    }

    @Test
    fun thePageIsReadyWhileTheReplayDecodeIsStillSuspended() {
        duel()
        myRow(2, replay = ReplayFixtures.sealed(ReplayFixtures.SHORT_MOVES))
        val gate = GatedDispatcher()
        val vm = page(leg = 2, decode = object : DispatcherProvider {
            override val main = dispatcher
            override val io = dispatcher
            override val default: CoroutineDispatcher = gate
        })

        assertEquals(ReplaySectionState.Hidden, vm.ready().replay)

        gate.release()
        dispatcher.scheduler.runCurrent()
        assertTrue(vm.ready().replay is ReplaySectionState.Ready)
    }

    @Test
    fun aGameWithoutAReplayBoardShowsNone() {
        fixtureUi.replayBoard = null
        duel()
        myRow(2, replay = ReplayFixtures.sealed(ReplayFixtures.SHORT_MOVES))
        assertEquals(ReplaySectionState.Hidden, page(leg = 2).ready().replay)
    }

    @Test
    fun theOpponentsReplayIsNeverRequested() {
        duel()
        myRow(2, state = ResultPostState.Posted, replay = ReplayFixtures.sealed(ReplayFixtures.SHORT_MOVES))
        bobPlays(2, replay = ReplayFixtures.sealed(ReplayFixtures.SHORT_MOVES))
        val vm = page(leg = 2)
        runBlocking { outbox.update(code, 2, ann.uid, ResultPostState.Posted, attempts = 2) }
        dispatcher.scheduler.runCurrent()

        assertTrue(vm.ready().replay is ReplaySectionState.Ready)
        assertEquals(DuelSideState.Time(70_000, hints = 1), vm.ready().them.state)
        assertEquals(emptyList<String>(), theirReads)
    }

    @Test
    fun theTimeMarginIsTheGapOfWholeSeconds() {
        assertEquals(1L, timeMarginSeconds(59_900, 60_100))
        assertEquals(1L, timeMarginSeconds(59_000, 60_900))
        assertEquals(1L, timeMarginSeconds(60_900, 59_000))
        assertEquals(3L, timeMarginSeconds(70_000, 67_000))
    }

    private companion object {
        val FIXTURE_DAY: LocalDate = LocalDate.of(2026, 11, 3)
    }
}
