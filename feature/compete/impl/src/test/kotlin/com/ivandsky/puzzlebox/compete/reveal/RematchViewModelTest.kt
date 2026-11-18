package com.ivandsky.puzzlebox.compete.reveal

import com.ivandsky.puzzlebox.common.log.FailureLog
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.match.CreateResult
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.Match
import com.ivandsky.puzzlebox.data.match.MatchDraft
import com.ivandsky.puzzlebox.data.match.MatchLeg
import com.ivandsky.puzzlebox.data.match.MatchRepository
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.data.match.SelfSeats
import com.ivandsky.puzzlebox.domain.ComputeMatchLegs
import com.ivandsky.puzzlebox.domain.ComputeSolvedPool
import com.ivandsky.puzzlebox.domain.CreateMatch
import com.ivandsky.puzzlebox.domain.CreateSelfDuel
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.lineup.ValidateExactPuzzles
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import com.ivandsky.puzzlebox.testing.game.TestGames.beta
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeMatchLocalRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchRepository
import com.ivandsky.puzzlebox.testing.repository.FakeReceivedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSelfMatchRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import java.time.Instant
import java.time.LocalDate
import kotlin.random.Random
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The rematch sheet's Send new invite: its sending flag, refusal note and created duel, over the fakes and the real use case. */
class RematchViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val today = LocalDate.of(2026, 11, 9)
    private val registry = TestGames.registry(listOf(alpha, beta))
    private val calendar = FakeGameCalendar(today)
    private val daily = FakeDailyRepository().apply {
        listOf(alpha, beta).forEach { game ->
            setArchiveRange(game, today.minusDays(29)..today)
            setPuzzleNumbers(game) { day -> (day.toEpochDay() % 10_000).toInt() + 1 }
        }
    }
    private val completions = FakeCompletionRepository()
    private val identity = FakeIdentityService(ANN)
    private val server = FakeMatchRepository().apply { signedInUid = ANN.uid }
    private val local = FakeMatchLocalRepository()
    private val received = FakeReceivedPuzzleRepository()
    private val draw = ComputeMatchLegs(
        registry, daily, calendar, ObserveGameHistory(registry, daily, completions), received, Random(DRAW_SEED),
    )
    private val selfMatches = FakeSelfMatchRepository()
    private val selfDuel = CreateSelfDuel(
        ComputeSolvedPool(registry, completions, received), completions, received, selfMatches, local,
        { Instant.parse("2026-11-09T10:00:00Z") }, Random(CODE_SEED),
    )
    private val played = Match(
        code = ShareCode.parse("K7Q2MX")!!,
        formatVersion = Match.FORMAT_VERSION,
        createdAt = Instant.parse("2026-11-08T10:00:00Z"),
        deadlineHours = 48,
        challenger = Player(ANN.uid, "Ann", null),
        invitedUid = null,
        opponent = Player("bob", "Bob", null),
        legs = listOf(alpha, beta).mapIndexed { i, game -> MatchLeg(LegRef.Daily(game, today.minusDays(1), 100 + i), authorUid = null) },
        includeFriends = false,
        cancelled = false,
        declined = false,
        rematchOf = null,
        progress = emptyMap(),
    )

    /** The fake server, with Create held until [gate] opens and counted in [creates]. */
    private class GatedMatchRepository(private val server: FakeMatchRepository, private val gate: CompletableDeferred<Unit>) :
        MatchRepository by server {
        var creates = 0
            private set

        override suspend fun create(draft: MatchDraft): CreateResult {
            creates++
            gate.await()
            return server.create(draft)
        }
    }

    private fun viewModel(repo: MatchRepository = server, match: Match = played) =
        RematchViewModel(match, CreateMatch(repo, local, draw, identity, Random(CODE_SEED), ValidateExactPuzzles(FakeSharedPuzzleRepository())), selfDuel)

    @Test
    fun aRematchKeepsTheDuelsGameOrder() = runTest {
        val custom = LegRef.Custom(beta, ShareCode.parse("M3N4P5")!!)
        val lineupDuel = played.copy(
            legs = listOf(MatchLeg(custom, "bob"), MatchLeg(LegRef.Daily(alpha, today.minusDays(1), 101), null), MatchLeg(LegRef.Daily(beta, today.minusDays(1), 102), null)),
        )
        val on = viewModel(match = lineupDuel)

        on.send()

        val created = on.created.first()
        assertEquals(listOf(beta, alpha, beta), server.matches.getValue(created.code).legs.map { it.ref.gameId })
        assertTrue(server.matches.getValue(created.code).legs.none { it.ref == custom })
    }

    @Test
    fun sendingShowsAsSendingThenHandsOverTheCreatedDuel() = runTest {
        val gate = CompletableDeferred<Unit>()
        val vm = viewModel(GatedMatchRepository(server, gate))

        vm.send()
        assertTrue(vm.state.value.sending)
        assertNull(vm.state.value.note)

        gate.complete(Unit)

        val created = vm.created.first()
        assertEquals(setOf(created.code), server.matches.keys)
        assertEquals(created.legs.size, played.legs.size)
        assertTrue("a created duel keeps the sheet busy until it leaves", vm.state.value.sending)
    }

    @Test
    fun theNewDuelInvitesTheOtherPlayerAndLinksTheOne() = runTest {
        val vm = viewModel()

        vm.send()

        val sent = server.matches.getValue(vm.created.first().code)
        assertEquals("bob", sent.invitedUid)
        assertEquals(played.code, sent.rematchOf)
        assertEquals(played.deadlineHours, sent.deadlineHours)
    }

    @Test
    fun aRefusalIsANoteAndTheButtonComesBack() = runTest {
        server.online = false
        val vm = viewModel()

        vm.send()

        assertEquals("You're offline. Check the connection and try again.", vm.state.value.note)
        assertFalse(vm.state.value.sending)
        assertNull(withTimeoutOrNull(1) { vm.created.first() })
        assertTrue(server.matches.isEmpty())
    }

    @Test
    fun aSecondTryClearsTheNoteWhileItSends() = runTest {
        server.online = false
        val gate = CompletableDeferred<Unit>()
        val vm = viewModel(GatedMatchRepository(server, gate))
        vm.send()
        assertNotNull(vm.state.value.note)
        server.online = true

        vm.send()

        assertNull(vm.state.value.note)
        assertTrue(vm.state.value.sending)
    }

    @Test
    fun aSignedOutSendIsRefusedWithASignInNote() = runTest {
        identity.currentIdentity = null
        val vm = viewModel()

        vm.send()

        assertEquals("Sign in to send a rematch.", vm.state.value.note)
        assertTrue(server.matches.isEmpty())
    }

    @Test
    fun aDoubleTapSendsOnce() = runTest {
        val gate = CompletableDeferred<Unit>()
        val held = GatedMatchRepository(server, gate)
        val vm = viewModel(held)

        vm.send()
        vm.send()
        gate.complete(Unit)

        vm.created.first()
        assertEquals(1, held.creates)
        assertEquals(1, server.matches.size)
    }

    @Test
    fun resetLeavesAFreshSheetBehind() = runTest {
        server.online = false
        val vm = viewModel()
        vm.send()
        assertNotNull(vm.state.value.note)

        vm.reset()

        assertEquals(RematchState(), vm.state.value)
    }

    private fun record(game: GameId, number: Int) = CompletionRecord(
        puzzleRef = TestGames.Ref(game, PuzzleKey.Daily(today.minusDays(number.toLong()), puzzleNumber = number)),
        time = 30_000L,
        hints = 0,
        stats = RecordStats(schemaVersion = 1, blob = "{}"),
        firstCompletedAt = Instant.ofEpochMilli(1_000L),
        isRecord = true,
    )

    @Test
    fun aSelfDuelRematchDrawsANewSelfDuelWithTheSameSettings() = runTest {
        completions.setRecords((1..4).flatMap { listOf(record(alpha, it), record(beta, it + 10)) })
        val self = played.copy(challenger = Player(SelfSeats.ME, "You", null), opponent = Player(SelfSeats.PAST, SelfSeats.PAST_NAME, null))
        val vm = RematchViewModel(self, CreateMatch(server, local, draw, identity, Random(CODE_SEED), ValidateExactPuzzles(FakeSharedPuzzleRepository())), selfDuel)

        vm.send()

        val created = vm.created.first()
        assertEquals(self.legs.size, created.legs.size)
        assertEquals(setOf(created.code), selfMatches.observeAll().first().map { it.code }.toSet())
        assertTrue("no invite goes to the server", server.matches.isEmpty())
    }

    private fun selfDuelOf(games: List<GameId>) = played.copy(
        challenger = Player(SelfSeats.ME, "You", null),
        opponent = Player(SelfSeats.PAST, SelfSeats.PAST_NAME, null),
        legs = games.mapIndexed { i, game -> MatchLeg(LegRef.Daily(game, today.minusDays(1), 100 + i), authorUid = null) },
    )

    private fun selfViewModel(self: Match) =
        RematchViewModel(self, CreateMatch(server, local, draw, identity, Random(CODE_SEED), ValidateExactPuzzles(FakeSharedPuzzleRepository())), selfDuel)

    @Test
    fun aSelfDuelRematchKeepsTheGameOrder() = runTest {
        completions.setRecords((1..5).flatMap { listOf(record(alpha, it), record(beta, it + 10)) })
        val vm = selfViewModel(selfDuelOf(listOf(beta, alpha, beta, beta, alpha)))

        vm.send()

        val created = vm.created.first()
        assertEquals(listOf(beta, alpha, beta, beta, alpha), selfMatches.observeAll().first().single { it.code == created.code }.legs.map { it.ref.gameId })
        assertTrue("no invite goes to the server", server.matches.isEmpty())
    }

    @Test
    fun aRefusedSelfLineupRematchIsThePastYouNote() = runTest {
        completions.setRecords(listOf(record(alpha, 1)) + (1..4).map { record(beta, it + 10) })
        val vm = selfViewModel(selfDuelOf(listOf(alpha, beta, alpha)))

        vm.send()

        assertEquals(RematchState(note = "Couldn't start the rematch. Try again."), vm.state.value)
        assertTrue(selfMatches.observeAll().first().isEmpty())
    }

    @Test
    fun aSelfDuelRematchThatThrowsIsTheFailureNoteAndTheButtonComesBack() = runTest {
        val self = played.copy(challenger = Player(SelfSeats.ME, "You", null), opponent = Player(SelfSeats.PAST, SelfSeats.PAST_NAME, null))
        val room = IllegalStateException("room closed")
        val broken = object : CompletionRepository by completions {
            override fun observeRecords() = flow<List<CompletionRecord>> { throw room }
        }
        val failing = CreateSelfDuel(
            ComputeSolvedPool(registry, broken, received), broken, received, selfMatches, local,
            { Instant.parse("2026-11-09T10:00:00Z") }, Random(CODE_SEED),
        )
        val reported = mutableListOf<Throwable>()
        val vm = RematchViewModel(self, CreateMatch(server, local, draw, identity, Random(CODE_SEED), ValidateExactPuzzles(FakeSharedPuzzleRepository())), failing, FailureLog { _, _, cause -> reported += cause })

        vm.send()

        assertEquals(RematchState(note = "Couldn't start the rematch. Try again."), vm.state.value)
        assertEquals(listOf<Throwable>(room), reported)
    }

    @Test
    fun aFriendRematchThatThrowsKeepsTheInviteNote() = runTest {
        val broken = object : MatchRepository by server {
            override suspend fun create(draft: MatchDraft): CreateResult = throw IllegalStateException("server down")
        }
        val vm = RematchViewModel(
            played, CreateMatch(broken, local, draw, identity, Random(CODE_SEED), ValidateExactPuzzles(FakeSharedPuzzleRepository())), selfDuel,
            FailureLog { _, _, _ -> },
        )

        vm.send()

        assertEquals(RematchState(note = "Couldn't send the invite. Try again."), vm.state.value)
    }

    private companion object {
        const val CODE_SEED = 7
        const val DRAW_SEED = 3
        val ANN = Identity(uid = "ann", displayName = "Ann", photoUrl = null)
    }
}
