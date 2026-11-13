package com.ivandsky.puzzlebox.history.ranks

import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.match.Match
import com.ivandsky.puzzlebox.data.visibility.VisibilityTier
import com.ivandsky.puzzlebox.domain.ComputeMatchLegs
import com.ivandsky.puzzlebox.domain.CreateMatch
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.ranks.GetPlayerActions
import com.ivandsky.puzzlebox.domain.ranks.PlayerActions
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.history.ranks.PlayerSheetViewModel.Challenge
import com.ivandsky.puzzlebox.history.ranks.PlayerSheetViewModel.Target
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import com.ivandsky.puzzlebox.testing.game.TestGames.beta
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeMatchLocalRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchRepository
import com.ivandsky.puzzlebox.testing.repository.FakeReceivedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeVisibilityRepository
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import java.time.LocalDate
import kotlin.random.Random
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The player sheet at the view-model seam: one tier read per open, Challenge locked below Duel, the invite and "Sent to <name>". */
class PlayerSheetViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val today = LocalDate.of(2026, 11, 9)
    private val registry = TestGames.registry(listOf(alpha, beta))
    private val daily = FakeDailyRepository().apply {
        listOf(alpha, beta).forEach { game ->
            setArchiveRange(game, today.minusDays(29)..today)
            setPuzzleNumbers(game) { day -> (day.toEpochDay() % 10_000).toInt() + 1 }
        }
    }
    private val completions = FakeCompletionRepository()
    private val identity = FakeIdentityService(Identity(uid = "ann", displayName = "Ann", photoUrl = null))
    private val server = FakeMatchRepository().apply { signedInUid = "ann" }
    private val local = FakeMatchLocalRepository()
    private val visibility = FakeVisibilityRepository().apply { putTier("maya", VisibilityTier.Duel) }
    private val createMatch = CreateMatch(
        server,
        local,
        ComputeMatchLegs(registry, daily, FakeGameCalendar(today), ObserveGameHistory(registry, daily, completions), FakeReceivedPuzzleRepository(), Random(DRAW_SEED)),
        identity,
        Random(CODE_SEED),
    )
    private val viewModel = PlayerSheetViewModel(
        GetPlayerActions(visibility, completions, FakeReleaseFlags(ReleaseFlag.FullReplay)),
        createMatch,
        registry,
        GamePlayRegistry(emptyList()),
    )

    private val maya = Target("maya", "Maya", Standing.Place(2), game = alpha, day = today)

    private suspend fun opened(target: Target = maya): PlayerSheetViewModel.State {
        viewModel.open(target)
        return viewModel.state.first { it?.actions != null }!!
    }

    @Test
    fun `opening reads the tier once, and opening the same row again reads nothing`() = runTest {
        opened()
        viewModel.open(maya)

        assertEquals(listOf("maya"), visibility.lookupCalls)
    }

    @Test
    fun `a new row is a new open and reads its own tier`() = runTest {
        opened()
        opened(maya.copy(uid = "jonas", name = "Jonas"))

        assertEquals(listOf("maya", "jonas"), visibility.lookupCalls)
    }

    @Test
    fun `challenge is locked below Duel and a tap creates nothing`() = runTest {
        visibility.putTier("maya", VisibilityTier.Board)

        val state = opened()
        viewModel.challenge(alpha)

        assertEquals(PlayerActions.Challenge.Private, state.actions!!.challenge)
        assertTrue(server.matches.isEmpty())
        assertEquals(Challenge.Idle, viewModel.state.value!!.challenge)
    }

    @Test
    fun `a failed tier read locks challenge`() = runTest {
        visibility.failingUids += "maya"

        val state = opened()
        viewModel.challenge(alpha)

        assertEquals(PlayerActions.Challenge.Private, state.actions!!.challenge)
        assertTrue(server.matches.isEmpty())
    }

    @Test
    fun `challenge invites the row's player to one puzzle of that game and ends on Sent to the sheet's name`() = runTest {
        opened()
        viewModel.challenge(alpha)

        val sent = viewModel.state.first { it?.challenge is Challenge.Sent }!!
        val match = server.matches.values.single()
        assertEquals("maya", match.invitedUid)
        assertEquals(1, match.legs.size)
        assertEquals(Match.DEADLINE_CHOICES.first(), match.deadlineHours)
        assertEquals(Challenge.Sent("Maya"), sent.challenge)
        assertNull(local.observeAll().first().single().opponentName)
    }

    @Test
    fun `a second tap after Sent creates no second duel`() = runTest {
        opened()
        viewModel.challenge(alpha)
        viewModel.state.first { it?.challenge is Challenge.Sent }
        viewModel.challenge(alpha)

        assertEquals(1, server.matches.size)
    }

    @Test
    fun `offline says so and the tap can be tried again`() = runTest {
        opened()
        server.online = false
        viewModel.challenge(alpha)
        viewModel.state.first { it?.challenge is Challenge.Failed }

        server.online = true
        viewModel.challenge(alpha)
        viewModel.state.first { it?.challenge is Challenge.Sent }

        assertEquals(1, server.matches.size)
    }

    @Test
    fun `overall offers every game and a game board only its own`() = runTest {
        val overall = opened(maya.copy(game = null, day = null))
        viewModel.close()
        val game = opened()

        assertEquals(listOf(alpha, beta), overall.games.map { it.id })
        assertEquals(listOf(alpha), game.games.map { it.id })
    }

    @Test
    fun `close leaves no sheet`() = runTest {
        opened()
        viewModel.close()

        assertNull(viewModel.state.value)
    }

    private companion object {
        const val CODE_SEED = 7
        const val DRAW_SEED = 3
    }
}
