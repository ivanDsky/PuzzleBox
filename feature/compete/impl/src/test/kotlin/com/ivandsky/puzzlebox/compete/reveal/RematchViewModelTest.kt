package com.ivandsky.puzzlebox.compete.reveal

import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.match.CreateResult
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.Match
import com.ivandsky.puzzlebox.data.match.MatchDraft
import com.ivandsky.puzzlebox.data.match.MatchLeg
import com.ivandsky.puzzlebox.data.match.MatchRepository
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.domain.ComputeMatchLegs
import com.ivandsky.puzzlebox.domain.CreateMatch
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
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
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import java.time.Instant
import java.time.LocalDate
import kotlin.random.Random
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
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
    private val draw = ComputeMatchLegs(
        registry, daily, calendar, ObserveGameHistory(registry, daily, completions), FakeReceivedPuzzleRepository(), Random(DRAW_SEED),
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

    private fun viewModel(repo: MatchRepository = server) =
        RematchViewModel(match = played, createMatch = CreateMatch(repo, local, draw, identity, Random(CODE_SEED)))

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

    private companion object {
        const val CODE_SEED = 7
        const val DRAW_SEED = 3
        val ANN = Identity(uid = "ann", displayName = "Ann", photoUrl = null)
    }
}
