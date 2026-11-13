package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.data.repository.DailyEntry
import com.ivandsky.puzzlebox.data.repository.UserPreferencesRepository
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.play.api.ReplayKey
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.replay.ReplayFixtures
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyReplayRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeUserPreferencesRepository
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import java.time.LocalDate
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The replay page's state holder: it loads once through the real resolver, and says no replay for every miss (A13). */
class ReplayViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val gameId = ReplayFixtures.GAME
    private val game = gameId.value
    private val games = replayCapableGames()
    private val today = LocalDate.parse("2026-11-04")
    private val completions = FakeCompletionRepository()
    private val daily = FakeDailyRepository().apply {
        setHistory(gameId, listOf(DailyEntry(gameId, today, 7, "fixture/v1", 0L)))
        setCached(gameId, today, TestGames.puzzle(gameId))
    }

    private fun store(day: LocalDate = today) {
        val ref = TestGames.Ref(gameId, PuzzleKey.Daily(day, 7))
        runBlocking {
            completions.onCompleted(
                CompletionRecord(ref, 12_000, 0, RecordStats(1, "{}"), isRecord = true),
                ReplayFixtures.sealed(ReplayFixtures.SHORT_MOVES),
            )
        }
    }

    private val replays = FakeDailyReplayRepository()

    private fun vm(
        key: ReplayKey,
        flagOn: Boolean = true,
        onDay: LocalDate = today,
        preferences: UserPreferencesRepository = FakeUserPreferencesRepository(),
    ) = ReplayViewModel(
        key = key,
        loadReplay = loadSolveReplay(
            games, daily, completions,
            if (flagOn) FakeReleaseFlags(ReleaseFlag.FullReplay) else FakeReleaseFlags(),
            replays,
        ),
        games = games,
        ui = GamePlayRegistry(emptyList()),
        calendar = FakeGameCalendar(onDay),
        preferences = preferences,
    )

    private fun daily(day: LocalDate = today, number: Int? = 7) =
        ReplayKey.daily(game, day.toString(), number, latest = true)

    @Test
    fun `a stored solve opens as ready`() {
        store()

        val ready = vm(daily()).state.value as ReplayState.Ready

        assertEquals(ReplayFixtures.SHORT_MOVES, ready.timeline.moveTimesMs.size)
    }

    @Test
    fun `no stored replay is none`() {
        assertEquals(ReplayState.None, vm(daily()).state.value)
    }

    @Test
    fun `flag off is none even with the replay stored`() {
        store()

        assertEquals(ReplayState.None, vm(daily(), flagOn = false).state.value)
    }

    @Test
    fun `an unknown game or a bad day is none`() {
        store()

        assertEquals(ReplayState.None, vm(daily().copy(gameId = "nope")).state.value)
        assertEquals(ReplayState.None, vm(ReplayKey.daily(game, "junk", 7, latest = true)).state.value)
    }

    @Test
    fun `a daily of today says today with its number`() {
        assertEquals("Fixture · #7 · Today · Wed 4 Nov", vm(daily()).context)
    }

    @Test
    fun `a past daily without a number says its date only`() {
        val past = today.minusDays(1)

        assertEquals("Fixture · Tue 3 Nov", vm(daily(past, number = null)).context)
    }

    @Test
    fun `a friend's puzzle and a duel name where they came from`() {
        assertEquals("Fixture · Friend's puzzle", vm(ReplayKey.custom(game, "abc", latest = true)).context)
        assertTrue(vm(ReplayKey.leg(game, "M8T3VQ", legIndex = 0)).context.endsWith("Duel"))
    }

    private fun player(name: String = "Maya") = ReplayKey.player(game, today.toString(), "them", name)

    @Test
    fun `a fetched player replay opens as ready`() {
        replays.putReplay(LeaderboardRef(game, today), "them", ReplayFixtures.sealed(ReplayFixtures.SHORT_MOVES).toByteArray())

        val ready = vm(player()).state.value as ReplayState.Ready

        assertEquals(ReplayFixtures.SHORT_MOVES, ready.timeline.moveTimesMs.size)
    }

    @Test
    fun `a player replay that is absent or denied is none`() {
        assertEquals(ReplayState.None, vm(player()).state.value)

        val board = LeaderboardRef(game, today)
        replays.putReplay(board, "them", ReplayFixtures.sealed(ReplayFixtures.SHORT_MOVES).toByteArray())
        replays.deniedReads += board to "them"

        assertEquals(ReplayState.None, vm(player()).state.value)
    }

    @Test
    fun `a player replay is titled by the player's name`() {
        assertEquals("Fixture · Maya's solve · Today · Wed 4 Nov", vm(player()).context)
        assertEquals("Fixture · Player's solve · Today · Wed 4 Nov", vm(player(name = "")).context)
    }

    @Test
    fun `the page opens with the stored Show errors off, read before it leaves Loading`() {
        store()

        val vm = vm(daily(), preferences = FakeUserPreferencesRepository(showReplayErrors = false))

        assertTrue(vm.state.value is ReplayState.Ready)
        assertEquals(false, vm.showErrors.value)
    }

    @Test
    fun `Show errors is on when nothing was stored`() {
        store()

        assertEquals(true, vm(daily()).showErrors.value)
    }

    @Test
    fun `Show errors is on while the stored value is still loading, and the page stays Loading`() {
        val slow = object : UserPreferencesRepository by FakeUserPreferencesRepository(showReplayErrors = false) {
            override val showReplayErrors: Flow<Boolean> = flow { awaitCancellation() }
        }

        val vm = vm(daily(), preferences = slow)

        assertEquals(ReplayState.Loading, vm.state.value)
        assertEquals(true, vm.showErrors.value)
    }

    @Test
    fun `switching Show errors updates the page and is stored, both ways`() {
        val preferences = FakeUserPreferencesRepository()
        val vm = vm(daily(), preferences = preferences)

        vm.setShowErrors(false)

        assertEquals(false, vm.showErrors.value)
        assertEquals(false, runBlocking { preferences.showReplayErrors.first() })

        vm.setShowErrors(true)

        assertEquals(true, vm.showErrors.value)
        assertEquals(true, runBlocking { preferences.showReplayErrors.first() })
    }
}
