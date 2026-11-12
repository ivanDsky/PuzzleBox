package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.data.repository.DailyEntry
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.game.replay.Lead
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.play.api.ReplayCompareKey
import com.ivandsky.puzzlebox.play.api.ReplayKey
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.replay.ReplayFixtures
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/** The compare page's state holder: it loads both sides once through the real resolver; a side without a replay is null (S7). */
class ReplayCompareViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val gameId = ReplayFixtures.GAME
    private val game = gameId.value
    private val games = replayCapableGames()
    private val today = LocalDate.parse("2026-11-04")
    private val yesterday = today.minusDays(1)
    private val completions = FakeCompletionRepository()
    private val daily = FakeDailyRepository().apply {
        setHistory(
            gameId,
            listOf(DailyEntry(gameId, today, 7, "fixture/v1", 0L), DailyEntry(gameId, yesterday, 6, "fixture/v1", 0L)),
        )
        setCached(gameId, today, TestGames.puzzle(gameId))
        setCached(gameId, yesterday, TestGames.puzzle(gameId))
    }

    private fun store(day: LocalDate, number: Int) {
        val ref = TestGames.Ref(gameId, PuzzleKey.Daily(day, number))
        runBlocking {
            completions.onCompleted(
                CompletionRecord(ref, 12_000, 0, RecordStats(1, "{}"), isRecord = true),
                ReplayFixtures.sealed(ReplayFixtures.SHORT_MOVES),
            )
        }
    }

    private fun vm(key: ReplayCompareKey, flagOn: Boolean = true, board: Boolean = true): ReplayCompareViewModel {
        val ui = FakePlayUi(gameId).apply { if (board) replayBoard = ReplayGoldenBoard }
        return ReplayCompareViewModel(
            key = key,
            loadReplay = loadSolveReplay(
                games, daily, completions,
                if (flagOn) FakeReleaseFlags(ReleaseFlag.FullReplay) else FakeReleaseFlags(),
            ),
            games = games,
            ui = GamePlayRegistry(listOf(ui)),
            calendar = FakeGameCalendar(today),
        )
    }

    private fun pair(day: LocalDate, number: Int, lead: String? = null) =
        ReplayCompareKey.of(
            mine = ReplayKey.daily(game, day.toString(), number, latest = false),
            theirs = ReplayKey.daily(game, day.toString(), number, latest = true),
            myName = "First win",
            theirName = "Latest",
            lead = lead,
        )

    @Test
    fun `both sides stored open as ready with both timelines`() {
        store(today, 7)
        store(yesterday, 6)

        val ready = vm(pair(today, 7)).state.value as ReplayCompareState.Ready

        assertNotNull(ready.mine)
        assertNotNull(ready.theirs)
    }

    @Test
    fun `one side without a replay is ready with that side null`() {
        store(today, 7)

        val noOtherSide = pair(today, 7).copy(theirsKind = ReplayKey.THEIR_LEG) // a leg of no duel names no solve
        val ready = vm(noOtherSide).state.value as ReplayCompareState.Ready

        assertNotNull(ready.mine)
        assertNull(ready.theirs)
    }

    @Test
    fun `neither side stored is none`() {
        assertEquals(ReplayCompareState.None, vm(pair(today, 7)).state.value)
    }

    @Test
    fun `flag off is none even with both replays stored`() {
        store(today, 7)
        store(yesterday, 6)

        assertEquals(ReplayCompareState.None, vm(pair(today, 7), flagOn = false).state.value)
    }

    @Test
    fun `a game without a replay board has none to draw`() {
        store(today, 7)

        assertNull(vm(pair(today, 7), board = false).board)
    }

    @Test
    fun `the key's names and lead reach the page`() {
        val vm = vm(pair(today, 7, ReplayCompareKey.LEAD_LEVEL))

        assertEquals("First win", vm.names.mine)
        assertEquals("Latest", vm.names.theirs)
        assertEquals(Lead.Level, vm.finalLead)
        assertNull(vm(pair(today, 7)).finalLead)
    }

    @Test
    fun `the context names the game and where the first side came from`() {
        assertEquals("Fixture · #7 · Today · Wed 4 Nov", vm(pair(today, 7)).context)
    }
}
