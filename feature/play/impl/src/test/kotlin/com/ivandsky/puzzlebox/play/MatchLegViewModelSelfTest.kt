package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.LegResult
import com.ivandsky.puzzlebox.data.match.MatchLeg
import com.ivandsky.puzzlebox.data.match.MatchResultPoster
import com.ivandsky.puzzlebox.data.match.OutboxResult
import com.ivandsky.puzzlebox.data.match.ResultPostState
import com.ivandsky.puzzlebox.data.match.ResultRead
import com.ivandsky.puzzlebox.data.match.RoutingMatchRepository
import com.ivandsky.puzzlebox.data.match.SelfSeats
import com.ivandsky.puzzlebox.play.domain.FinishLeg
import com.ivandsky.puzzlebox.domain.LegSeen
import com.ivandsky.puzzlebox.domain.LegVerdict
import com.ivandsky.puzzlebox.domain.LoadPastDay
import com.ivandsky.puzzlebox.domain.LoadSharedPuzzle
import com.ivandsky.puzzlebox.domain.MatchState
import com.ivandsky.puzzlebox.play.domain.ObserveLegTarget
import com.ivandsky.puzzlebox.domain.ObserveMatch
import com.ivandsky.puzzlebox.domain.buildSelfMatch
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.game.SessionBlob
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.api.MatchLegKey
import com.ivandsky.puzzlebox.play.api.MatchLegResultKey
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.network.FakeNetworkStatus
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeMatchLocalRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchResultOutbox
import com.ivandsky.puzzlebox.testing.repository.FakeReceivedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSelfMatchRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeUserPreferencesRepository
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import java.time.Instant
import java.time.LocalDate
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
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
 * A self duel puzzle's board (me against my own first solves of three alpha dailies) over the app's routing
 * repository and the device's self store. Nobody is signed in unless a test says so: the seat is the match's,
 * never the account's.
 */
class MatchLegViewModelSelfTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val game = TestGames.alpha
    private val code = checkNotNull(ShareCode.parse("M8T3VQ"))
    private val account = Identity("ann-uid", "Ann", photoUrl = null)
    private val today = LocalDate.parse("2026-11-04")
    private val created = Instant.parse("2026-11-04T10:00:00Z")

    private val self = FakeSelfMatchRepository()
    private val preferences = FakeUserPreferencesRepository()
    private val matches = RoutingMatchRepository(self, FakeMatchRepository())
    private val outbox = FakeMatchResultOutbox()
    private val identity = FakeIdentityService()
    private val saved = FakeSavedPlayRepository()
    private val fakes = FakeGames(listOf(game))
    private val dispatcher = main.testDispatcher
    private val store = store()

    /** Collectors standing in for the screen, and the app process the poster runs on. */
    private val screen = CoroutineScope(dispatcher + Job())
    private val exits = mutableListOf<MatchLegExit>()

    @After
    fun leaveScreen() {
        screen.cancel()
    }

    private fun store(): PlaySessionStore {
        val lane = WriteLane()
        val daily = FakeDailyRepository()
        val calendar = FakeGameCalendar(today)
        return PlaySessionStore(
            games = fakes.games,
            ui = fakes.registry,
            opening = SlotOpening(
                daily, LoadPastDay(daily, fakes.games), saved, calendar, fakes.games, lane, FakeReceivedPuzzleRepository(),
                matches,
                LoadSharedPuzzle(fakes.games, FakeSharedPuzzleRepository(), FakeReceivedPuzzleRepository(), { Budget.Unlimited }, { Instant.EPOCH }),
                testDispatchers(dispatcher),
            ),
            writers = { SlotWriter(it, saved, lane, CoroutineScope(dispatcher)) },
            completions = FakeCompletionRepository(),
            finishLeg = FinishLeg(outbox, self, identity) { created },
            calendar = calendar,
            dispatchers = testDispatchers(dispatcher),
        )
    }

    private fun leg(index: Int) = MatchLegKey(code.value, index, game.value)

    private fun slot(index: Int) = PlaySlot.Leg(game, code.value, index)

    private fun ref(index: Int) = TestGames.Ref(game, PuzzleKey.Daily(LocalDate.parse("2026-11-0${index + 1}"), index + 1))

    /** Past me solved each of the three puzzles in 50 s; every puzzle has a saved board [savedMoves] moves in. */
    private fun selfDuel(savedMoves: Int = 0) = runBlocking {
        val legs = List(3) { MatchLeg(LegRef.Daily(game, LocalDate.parse("2026-11-0${it + 1}"), it + 1), authorUid = null) }
        val records = List(3) {
            CompletionRecord(ref(it), time = PAST_TIME_MS, hints = 0, stats = RecordStats(1, "{}"), firstCompletedAt = Instant.EPOCH)
        }
        val draft = buildSelfMatch(code, created, includeFriends = false, legs = legs, records = records)
        self.save(draft.match, draft.past)
        legs.indices.forEach { index ->
            saved.save(slot(index), SessionBlob.encode(game, FakeController.encode(savedMoves, false, ref(index))), 0L)
        }
    }

    private fun row(result: LegResult) = runBlocking { outbox.enqueue(code, result) }

    private fun rows(): List<OutboxResult> = runBlocking { outbox.observe(code).first() }

    private fun route(leg: Int = 1): MatchLegViewModel =
        MatchLegViewModel(leg(leg), store, matches, outbox, identity, self, ObserveLegTarget(self, preferences)).also { vm ->
            screen.launch { vm.state.collect {} }
            screen.launch { vm.exits.collect { exits += it } }
            dispatcher.scheduler.runCurrent()
        }

    private fun MatchLegViewModel.playing() = state.value as MatchLegState.Playing

    @Test
    fun aSignedOutPlayerOpensTheirSelfDuelPuzzleWithoutWaitingForASignIn() {
        selfDuel()

        val playing = route(leg = 1).playing()

        assertNotNull("the board opens at once: no identity wait for a seat the match already gives", playing.play)
        assertEquals(LegPosition(SelfSeats.PAST_NAME, 2, 3, played = 0), playing.position)
        assertTrue("the self seat can record a give up", playing.canGiveUp)
        assertTrue(exits.isEmpty())
    }

    @Test
    fun aSelfDuelPuzzleCarriesPastYousTimeAsItsTimerTargetWhileTheSettingIsOn() {
        selfDuel()

        val vm = route(leg = 1)
        assertEquals(PAST_TIME_MS.milliseconds, vm.playing().target)

        runBlocking { preferences.setShowSelfDuelTimeLeft(false) }
        dispatcher.scheduler.runCurrent()
        assertNull("setting off: the normal timer", vm.playing().target)
    }

    @Test
    fun aSavedSelfDuelPuzzleResumesSignedOut() {
        selfDuel(savedMoves = 4)

        route(leg = 1)

        assertNotNull(store.peek(slot(1)))
        assertEquals("the saved board, not a fresh one", 4, fakes[game].latest.moves)
    }

    @Test
    fun aSelfDuelPuzzleIPlayedLeavesForItsResultSignedOut() {
        selfDuel()
        row(LegResult(SelfSeats.ME, 1, timeMs = 61_000L, hints = 2, completedAtMs = 1_000L, gaveUp = false))

        val vm = route(leg = 1)

        assertEquals(listOf<MatchLegExit>(MatchLegExit.Taken(MatchLegResultKey(code.value, 1, 61_000L, 2, gaveUp = false))), exits)
        assertEquals(MatchLegState.Checking, vm.state.value)
        assertNull("a taken puzzle never opens its play", store.peek(slot(1)))
    }

    @Test
    fun aSignedInPlayersSelfDuelPuzzleIsTakenByTheSelfSeatNotTheAccount() {
        selfDuel()
        identity.currentIdentity = account
        row(LegResult(SelfSeats.ME, 1, timeMs = 61_000L, hints = 0, completedAtMs = 1_000L, gaveUp = false))
        row(LegResult(account.uid, 2, timeMs = 9_000L, hints = 0, completedAtMs = 1_000L, gaveUp = false))

        route(leg = 1)
        assertEquals(listOf<MatchLegExit>(MatchLegExit.Taken(MatchLegResultKey(code.value, 1, 61_000L, 0, gaveUp = false))), exits)
        assertNull(store.peek(slot(1)))

        val other = route(leg = 2).playing()
        assertNotNull("a row under the account is not the self seat's", other.play)
        assertEquals(LegPosition(SelfSeats.PAST_NAME, 3, 3, played = 0), other.position)
    }

    @Test
    fun givingUpSignedOutQueuesTheLossUnderTheSelfSeat() {
        selfDuel()
        val vm = route(leg = 1)

        vm.giveUp()

        assertEquals(listOf<MatchLegExit>(MatchLegExit.GaveUp), exits)
        val queued = rows().single().result
        assertEquals(SelfSeats.ME, queued.uid)
        assertEquals(1, queued.legIndex)
        assertTrue(queued.gaveUp)
        assertNull("the play is dropped", store.peek(slot(1)))
    }

    /** The whole signed-out loop: play, queue, post to the device, read as taken, and see what past me did. */
    @Test
    fun aSignedOutSolveIsPostedToTheDeviceTakesThePuzzleAndRevealsThePastSolve() {
        selfDuel()
        MatchResultPoster(identity, outbox, matches, FakeNetworkStatus(), screen).start()
        route(leg = 0)

        fakes[game].latest.win()
        dispatcher.scheduler.runCurrent()

        val mine = LegResult(SelfSeats.ME, 0, FakeController.TIME_MS, 0, created.toEpochMilli(), gaveUp = false)
        assertEquals(listOf(OutboxResult(code, mine, ResultPostState.Posted, 0)), rows())
        assertEquals(ResultRead.Found(mine), runBlocking { self.readResult(code, 0, SelfSeats.ME) })

        route(leg = 0)
        assertEquals(
            listOf<MatchLegExit>(MatchLegExit.Taken(MatchLegResultKey(code.value, 0, FakeController.TIME_MS, 0, gaveUp = false))),
            exits,
        )

        val observe = ObserveMatch(matches, outbox, FakeMatchLocalRepository(), identity, fakes.games) { created }
        val view = (runBlocking { observe(code).first() } as MatchState.Loaded).view
        assertEquals(SelfSeats.ME, view.me)
        assertEquals(
            listOf(LegSeen.Revealed(LegResult(SelfSeats.PAST, 0, PAST_TIME_MS, 0, 0, gaveUp = false)), LegSeen.Played, LegSeen.Played),
            view.theirs,
        )
        assertTrue("42 s beats past me's 50 s", view.outcome.legs[0] is LegVerdict.Win)
    }

    private companion object {
        const val PAST_TIME_MS = 50_000L
    }
}
