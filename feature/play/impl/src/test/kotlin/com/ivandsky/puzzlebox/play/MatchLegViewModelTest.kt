package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.LegResult
import com.ivandsky.puzzlebox.data.match.MatchDraft
import com.ivandsky.puzzlebox.data.match.MatchLeg
import com.ivandsky.puzzlebox.data.match.MatchRepository
import com.ivandsky.puzzlebox.data.match.OutboxResult
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.data.match.ResultPostState
import com.ivandsky.puzzlebox.data.match.ResultRead
import com.ivandsky.puzzlebox.domain.FinishLeg
import com.ivandsky.puzzlebox.domain.LoadPastDay
import com.ivandsky.puzzlebox.domain.LoadSharedPuzzle
import com.ivandsky.puzzlebox.game.SessionBlob
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.api.MatchLegKey
import com.ivandsky.puzzlebox.play.api.MatchLegResultKey
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceTimeBy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * A duel puzzle's board over the rules-faithful match fakes: Ann (the viewer)
 * challenges and Ben claims a duel of three alpha dailies. The state and the exits are read, never the
 * route's text: whether the board opened, where the route leaves to, and what reached the outbox first.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MatchLegViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val game = TestGames.alpha
    private val code = checkNotNull(ShareCode.parse("M8T3VQ"))
    private val ann = Player("ann-uid", "Ann", photoUrl = null)
    private val ben = Player("ben-uid", "Ben", photoUrl = null)
    private val today = LocalDate.parse("2026-11-04")

    private val server = FakeMatchRepository()
    private val outbox = FakeMatchResultOutbox()
    private val identity = FakeIdentityService(Identity(ann.uid, ann.name, photoUrl = null))
    private val saved = FakeSavedPlayRepository()
    private val fakes = FakeGames(listOf(game))
    private val dispatcher = main.testDispatcher
    private var resultReads = 0

    /** The server as the leg screen sees it: every sealed result read is counted. */
    private val countingServer = object : MatchRepository by server {
        override suspend fun readResult(code: ShareCode, legIndex: Int, uid: String): ResultRead {
            resultReads++
            return server.readResult(code, legIndex, uid)
        }
    }
    private val store = store()

    /** Collectors standing in for the screen: the state only runs while observed. */
    private val screen = CoroutineScope(dispatcher + Job())
    private val exits = mutableListOf<MatchLegExit>()
    private var rowsWhenLeaving: List<OutboxResult>? = null

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

    private fun leg(index: Int) = MatchLegKey(code.value, index, game.value)

    private fun slot(index: Int) = PlaySlot.Leg(game, code.value, index)

    /** Ann creates the duel of three puzzles and Ben takes the seat; Ann is the viewer after. Every leg has a saved board. */
    private fun duel(seated: Boolean = true) = runBlocking {
        val legs = (1..3).map { MatchLeg(LegRef.Daily(game, LocalDate.parse("2026-11-0$it"), it), authorUid = null) }
        server.signedInUid = ann.uid
        server.create(MatchDraft(code, 24, ann, invitedUid = null, legs, includeFriends = false, rematchOf = null))
        if (seated) {
            server.signedInUid = ben.uid
            server.claim(code, ben)
            server.signedInUid = ann.uid
        }
        legs.indices.forEach { index ->
            val session = FakeController.encode(0, false, TestGames.Ref(game, PuzzleKey.Daily(today, 7)))
            saved.save(slot(index), SessionBlob.encode(game, session), 0L)
        }
    }

    private fun solved(player: Player, legIndex: Int, timeMs: Long, hints: Int) =
        LegResult(player.uid, legIndex, timeMs, hints, completedAtMs = 1_000L, gaveUp = false)

    private fun row(result: LegResult) = runBlocking { outbox.enqueue(code, result) }

    private fun route(leg: Int = 1): MatchLegViewModel {
        return MatchLegViewModel(leg(leg), store, countingServer, outbox, identity).also { vm ->
            screen.launch { vm.state.collect {} }
            screen.launch {
                vm.exits.collect {
                    rowsWhenLeaving = outbox.observe(code).first()
                    exits += it
                }
            }
            dispatcher.scheduler.runCurrent()
        }
    }

    private fun MatchLegViewModel.playing() = state.value as MatchLegState.Playing

    private fun signIn(player: Player) {
        identity.currentIdentity = Identity(player.uid, player.name, photoUrl = null)
        dispatcher.scheduler.runCurrent()
    }

    @Test
    fun aSignedInPlayerWithoutAResultOpensTheBoardAtItsPlaceInTheDuel() {
        duel()
        val vm = route(leg = 1)

        val playing = vm.playing()
        assertNotNull("the puzzle's play is open", playing.play)
        assertEquals(LegPosition("Ben", 2, 3), playing.position)
        assertTrue(playing.canGiveUp)
        assertTrue("nothing to leave for", exits.isEmpty())
    }

    @Test
    fun theBoardReadsOnlyTheMatchDocumentNeverAResult() {
        duel()
        row(solved(ann, legIndex = 0, timeMs = 5_000L, hints = 0))
        runBlocking {
            outbox.update(code, 0, ann.uid, ResultPostState.Posted, attempts = 1)
            server.post(code, solved(ann, legIndex = 0, timeMs = 5_000L, hints = 0))
            server.signedInUid = ben.uid
            server.post(code, solved(ben, legIndex = 0, timeMs = 6_000L, hints = 0))
            server.signedInUid = ann.uid
        }

        route(leg = 1)
        row(solved(ann, legIndex = 2, timeMs = 7_000L, hints = 0))
        dispatcher.scheduler.runCurrent()

        assertEquals("no sealed result is read from the puzzle's board", 0, resultReads)
    }

    @Test
    fun anOpenSeatHasNoOpponentName() {
        duel(seated = false)

        assertEquals(LegPosition(null, 1, 3), route(leg = 0).playing().position)
    }

    @Test
    fun aPuzzleIndexPastTheEndOfTheDuelHasNoPosition() {
        duel()

        assertNull(route(leg = 3).playing().position)
    }

    @Test
    fun theOpponentsRowDoesNotTakeThePuzzle() {
        duel()
        row(solved(ben, legIndex = 1, timeMs = 9_000L, hints = 1))

        assertNotNull(route(leg = 1).playing().play)
        assertTrue(exits.isEmpty())
    }

    @Test
    fun aTakenPuzzleLeavesForItsResultAndNeverOpensItsPlay() {
        duel()
        row(solved(ann, legIndex = 1, timeMs = 61_000L, hints = 2))

        val vm = route(leg = 1)

        assertEquals(listOf<MatchLegExit>(MatchLegExit.Taken(MatchLegResultKey(code.value, 1, 61_000L, 2, gaveUp = false))), exits)
        assertEquals(MatchLegState.Checking, vm.state.value)
        assertNull("a taken puzzle never opens its play", store.peek(slot(1)))
    }

    @Test
    fun aRestoringPlayerIsWaitedForAndATakenPuzzleStillNeverOpens() {
        duel()
        row(LegResult(ann.uid, 1, timeMs = 0L, hints = 0, completedAtMs = 1_000L, gaveUp = true))
        identity.currentIdentity = null

        val vm = route(leg = 1)
        dispatcher.scheduler.advanceTimeBy(1_999L)
        dispatcher.scheduler.runCurrent()

        assertEquals("still waiting for the player", MatchLegState.Checking, vm.state.value)
        assertTrue(exits.isEmpty())
        assertNull("no play while the player is unknown", store.peek(slot(1)))

        signIn(ann)

        assertEquals(listOf<MatchLegExit>(MatchLegExit.Taken(MatchLegResultKey(code.value, 1, 0L, 0, gaveUp = true))), exits)
        assertNull("a taken puzzle never opens its play", store.peek(slot(1)))
    }

    @Test
    fun aPlayerWhoNeverArrivesOpensTheBoardAfterTheWaitWithoutGiveUp() {
        duel()
        identity.currentIdentity = null

        val vm = route(leg = 1)
        dispatcher.scheduler.advanceTimeBy(1_999L)
        dispatcher.scheduler.runCurrent()
        assertEquals(MatchLegState.Checking, vm.state.value)

        dispatcher.scheduler.advanceTimeBy(1L)
        dispatcher.scheduler.runCurrent()

        val playing = vm.playing()
        assertNotNull(playing.play)
        assertEquals("signed out sees no opponent's name", LegPosition(null, 2, 3), playing.position)
        assertTrue("signed out nothing could record a give up", !playing.canGiveUp)
    }

    @Test
    fun givingUpQueuesTheLossBeforeTheRouteLeaves() {
        duel()
        val vm = route(leg = 1)

        vm.giveUp()

        assertEquals(listOf<MatchLegExit>(MatchLegExit.GaveUp), exits)
        val queued = checkNotNull(rowsWhenLeaving).single().result
        assertEquals(1, queued.legIndex)
        assertEquals(ann.uid, queued.uid)
        assertTrue("the loss is a give up", queued.gaveUp)
        assertNull("the play is dropped", store.peek(slot(1)))
    }

    @Test
    fun givingUpTwiceQueuesOneLoss() {
        duel()
        val vm = route(leg = 1)

        vm.giveUp()
        vm.giveUp()

        assertEquals(listOf<MatchLegExit>(MatchLegExit.GaveUp), exits)
        assertEquals(1, runBlocking { outbox.observe(code).first() }.size)
    }

    @Test
    fun aSolveThatLandedFirstIsTheResultOfAGiveUp() {
        duel()
        val vm = route(leg = 1)
        row(solved(ann, legIndex = 1, timeMs = 40_000L, hints = 1))

        vm.giveUp()

        assertEquals(listOf<MatchLegExit>(MatchLegExit.Taken(MatchLegResultKey(code.value, 1, 40_000L, 1, gaveUp = false))), exits)
    }

    @Test
    fun givingUpSignedOutRecordsNothingAndCanBeTriedAgain() {
        duel()
        val vm = route(leg = 1)
        identity.currentIdentity = null
        dispatcher.scheduler.runCurrent()

        vm.giveUp()
        assertTrue("nothing could record it", exits.isEmpty())
        assertTrue(runBlocking { outbox.observe(code).first() }.isEmpty())

        signIn(ann)
        vm.giveUp()

        assertEquals(listOf<MatchLegExit>(MatchLegExit.GaveUp), exits)
    }
}
