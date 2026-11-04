package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.data.repository.DailyEntry
import com.ivandsky.puzzlebox.data.repository.DailyToday
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.RecordVerdict
import com.ivandsky.puzzlebox.gameui.degradeBannerMessage
import com.ivandsky.puzzlebox.gameui.unavailableMessage
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeKeptSessionRepository
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The play feature's game-agnostic half of a play, over a stand-in game: fetch → adopt
 * (A7/A8 degrade states first-class), the record identity (ADR-0004), the fire-and-observe
 * record write (F2 §7) and the A10 verdict from the session-start baseline. The game halves
 * (decode, moves, onWon once per win) are pinned in each games:*-ui controller test.
 */
class ActivePlayTest {

    private val game = TestGames.alpha
    private val day = LocalDate.parse("2026-10-31")
    private val ref = TestGames.Ref(game, day, 7)

    private val fakes = FakeGames(listOf(game))
    private val ui get() = fakes[game]
    private val daily = FakeDailyRepository().apply {
        setToday(game, DailyToday.Fresh(TestGames.puzzle(game)))
        setHistory(game, listOf(DailyEntry(game, day, 7, "alpha/v1", 0L)))
    }
    private val completions = FakeCompletionRepository()

    private val store = PlaySessionStore(
        games = fakes.games,
        ui = fakes.registry,
        daily = daily,
        completions = completions,
        kept = FakeKeptSessionRepository(),
        today = { day },
        scopeFactory = { CoroutineScope(Job() + UnconfinedTestDispatcher()) },
        keepScope = CoroutineScope(Job() + UnconfinedTestDispatcher()),
    )

    private fun play(): ActivePlay = store.start(game) {}!!

    private fun loaded(): ActivePlay = play().also { it.load() }

    private fun records() = runBlocking { completions.observeRecords().first() }

    @Test
    fun `fresh fetch adopts the ready board with today's identity`() {
        val play = loaded()
        assertEquals(PlayBoardStatus.Ready(banner = null), play.board.value.status)
        assertSame(ui.latest, play.controller)
        assertEquals(ref, ui.latest.ref)
        assertEquals(TestGames.puzzle(game).payload.toList(), ui.latest.puzzle!!.payload.toList())
        assertFalse(play.needsLoad)
    }

    @Test
    fun `today without a captured row adopts an unnumbered identity`() {
        daily.setHistory(game, emptyList())
        loaded()
        assertEquals(TestGames.Ref(game, day, null), ui.latest.ref)
    }

    @Test
    fun `cached degrade plays the saved puzzle with the banner`() {
        val error = FetchError.Throttled()
        daily.setToday(game, DailyToday.Cached(TestGames.puzzle(game), error))
        val play = loaded()
        assertEquals(PlayBoardStatus.Ready(banner = degradeBannerMessage(error)), play.board.value.status)
        assertEquals(ref, ui.latest.ref)
    }

    @Test
    fun `unavailable with nothing captured is a retryable error and retry refetches`() {
        val error = FetchError.Unavailable("offline")
        daily.setToday(game, DailyToday.Unavailable(error))
        val play = loaded()
        assertEquals(PlayBoardStatus.Unavailable(unavailableMessage(error)), play.board.value.status)
        assertNull(ui.latest.puzzle)
        assertTrue(play.needsLoad)

        daily.setToday(game, DailyToday.Fresh(TestGames.puzzle(game)))
        play.load()
        assertEquals(2, daily.todayCalls(game))
        assertEquals(PlayBoardStatus.Ready(banner = null), play.board.value.status)
    }

    @Test
    fun `an envelope the game rejects is never playable`() {
        ui.rejectWith = "untrusted"
        val play = loaded()
        assertEquals(PlayBoardStatus.Unavailable("untrusted"), play.board.value.status)
        assertTrue(play.needsLoad)
    }

    @Test
    fun `a fresh win writes exactly one record and the verdict is a first solve`() {
        val play = loaded()
        assertNull(play.record.value.verdict)

        ui.latest.win()

        assertEquals(listOf(FakeController.record(ref)), records())
        assertEquals(RecordVerdict.FirstSolve, play.record.value.verdict)
        assertFalse(play.record.value.writeFailed)
        assertTrue(play.needsLoad) // a finished session means a fresh entry fetches
    }

    @Test
    fun `a win on an already-recorded puzzle is not a new best`() {
        completions.setRecords(listOf(FakeController.record(ref)))
        val play = loaded()
        ui.latest.win()
        assertEquals(RecordVerdict.NotBest, play.record.value.verdict)
        assertEquals("an equal time shows the best it tied", FakeController.TIME_MS, play.record.value.previousBestMs)
        assertEquals(2, records().size)
    }

    @Test
    fun `a replay faster than the record is a new best`() {
        // The game's best is a slower record of this very puzzle (FirstWinPolicy keeps it
        // the record); the replay beats it, so the verdict is honest about the time.
        val slower = FakeController.TIME_MS + 10_000
        completions.setRecords(listOf(FakeController.record(ref).copy(time = slower, isRecord = true)))
        val play = loaded()
        ui.latest.win()
        assertEquals(RecordVerdict.NewBest, play.record.value.verdict)
        assertEquals(slower, play.record.value.previousBestMs)
    }

    @Test
    fun `another game's faster solve never counts as this game's best`() {
        val other = TestGames.Ref(TestGames.beta, day, 7)
        completions.setRecords(listOf(FakeController.record(other).copy(time = 1_000)))
        val play = loaded()
        ui.latest.win()
        assertEquals(RecordVerdict.FirstSolve, play.record.value.verdict)
        assertNull(play.record.value.previousBestMs)
    }

    @Test
    fun `a solve slower than the game's best on another day is not a new best and shows that best`() {
        val faster = FakeController.TIME_MS - 10_000
        completions.setRecords(listOf(FakeController.record(TestGames.Ref(game, day.minusDays(1), 6)).copy(time = faster)))
        val play = loaded()
        ui.latest.win()
        assertEquals(RecordVerdict.NotBest, play.record.value.verdict)
        assertEquals(faster, play.record.value.previousBestMs)
    }

    @Test
    fun `replay after a win emits again and is not a new best`() {
        val play = loaded()
        ui.latest.win()
        assertEquals(RecordVerdict.FirstSolve, play.record.value.verdict)

        play.replay()
        assertEquals(1, ui.latest.replays)
        assertNull("the verdict resets until the new write lands", play.record.value.verdict)
        assertEquals(PlayBoardStatus.Ready(banner = null), play.board.value.status)

        ui.latest.win()
        assertEquals(2, records().size)
        assertEquals(RecordVerdict.NotBest, play.record.value.verdict)
    }

    @Test
    fun `replay keeps the degrade banner`() {
        val error = FetchError.Throttled()
        daily.setToday(game, DailyToday.Cached(TestGames.puzzle(game), error))
        val play = loaded()
        play.replay()
        assertEquals(PlayBoardStatus.Ready(banner = degradeBannerMessage(error)), play.board.value.status)
    }

    @Test
    fun `a failed record write surfaces retryable, never blocks the win, and retry lands it`() {
        completions.fail = true
        val play = loaded()
        ui.latest.win()

        assertTrue(play.record.value.writeFailed)
        assertNull(play.record.value.verdict)
        assertTrue(records().isEmpty())
        assertTrue(ui.latest.isComplete)

        completions.fail = false
        play.retryRecordWrite()
        assertFalse(play.record.value.writeFailed)
        assertEquals(RecordVerdict.FirstSolve, play.record.value.verdict)
        assertEquals(1, records().size)
    }

    @Test
    fun `result label names the puzzle number when known`() {
        assertEquals("Alpha", play().record.value.label)
        assertEquals("Alpha · #7", loaded().record.value.label)

        daily.setHistory(game, emptyList())
        val unnumbered = loaded()
        assertEquals("Alpha", unnumbered.record.value.label)
    }

    @Test
    fun `a restored in-progress session adopts without fetching and keeps its identity`() {
        val play = play()
        play.adoptRestored(FakeController.envelope(game, moves = 2, day = day).session)
        assertEquals(0, daily.todayCalls(game))
        assertEquals(PlayBoardStatus.Ready(banner = null), play.board.value.status)
        assertEquals(2, ui.latest.moves)
        assertEquals("Alpha · #7", play.record.value.label)
        assertFalse(play.needsLoad)

        ui.latest.win()
        assertEquals(RecordVerdict.FirstSolve, play.record.value.verdict)
        assertEquals(1, records().size)
    }

    @Test
    fun `a restored completed session shows the win without re-emitting`() {
        val play = play()
        play.adoptRestored(FakeController.envelope(game, moves = 5, complete = true, day = day).session)
        assertEquals(PlayBoardStatus.Ready(banner = null), play.board.value.status)
        assertTrue(play.controller.isComplete)
        assertTrue(records().isEmpty())
        assertNull(play.record.value.verdict)
    }

    @Test
    fun `corrupt saved bytes fall back to a normal load`() {
        val play = play()
        val created = ui.controllers.size
        play.adoptRestored("not a session".encodeToByteArray())
        assertEquals(1, daily.todayCalls(game))
        assertEquals(PlayBoardStatus.Ready(banner = null), play.board.value.status)
        assertEquals(ref, ui.latest.ref)
        assertEquals("no throwaway controller for the unreadable blob", created + 1, ui.controllers.size)
    }

    @Test
    fun `a record write in flight across a restore still delivers its verdict`() {
        val gate = CompletableDeferred<Unit>()
        completions.writeGate = gate
        val play = loaded()
        ui.latest.move()
        val lagging = ui.latest.bytes()
        ui.latest.win()
        assertNull(play.record.value.verdict)

        // A restore of the SAME puzzle (a saveable that missed the winning commit) adopts
        // into a new controller while the win's write is still running.
        play.adoptRestored(lagging)
        gate.complete(Unit)

        assertEquals(listOf(FakeController.record(ref)), records())
        assertEquals(RecordVerdict.FirstSolve, play.record.value.verdict)
    }

    @Test
    fun `a failed record write in flight across a restore still surfaces and retries`() {
        val gate = CompletableDeferred<Unit>()
        completions.writeGate = gate
        completions.fail = true
        val play = loaded()
        ui.latest.move()
        val lagging = ui.latest.bytes()
        ui.latest.win()

        play.adoptRestored(lagging)
        gate.complete(Unit)
        assertTrue(play.record.value.writeFailed)

        completions.fail = false
        play.retryRecordWrite()
        assertFalse(play.record.value.writeFailed)
        assertEquals(RecordVerdict.FirstSolve, play.record.value.verdict)
        assertEquals(1, records().size)
    }

    @Test
    fun `every load adopts into a new controller`() {
        val play = loaded()
        val first = play.controller
        val created = ui.controllers.size
        play.load()
        assertTrue(first !== play.controller)
        assertEquals(created + 1, ui.controllers.size)
    }
}
