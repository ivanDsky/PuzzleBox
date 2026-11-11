package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.repository.DailyDay
import com.ivandsky.puzzlebox.data.repository.DailyEntry
import com.ivandsky.puzzlebox.data.repository.DailyToday
import com.ivandsky.puzzlebox.domain.FinishLeg
import com.ivandsky.puzzlebox.domain.LoadPastDay
import com.ivandsky.puzzlebox.domain.LoadSharedPuzzle
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.SessionBlob
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.RecordVerdict
import com.ivandsky.puzzlebox.gameui.degradeBannerMessage
import com.ivandsky.puzzlebox.gameui.unavailableMessage
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.play.api.GameKey
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
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
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
 * The play feature's game-agnostic half of a play, over a stand-in game: opening → adopt
 * (degrade states first-class, a saved row before the network), the record
 * identity (ADR-0004), the fire-and-observe record write and the verdict from
 * the session-start baseline. The game halves (decode, moves, onWon once per win) are
 * pinned in each games:*-ui controller test.
 */
class ActivePlayTest {

    private val game = TestGames.alpha
    private val day = LocalDate.parse("2026-10-31")
    private val ref = TestGames.Ref(game, PuzzleKey.Daily(day, 7))
    private val key = GameKey(game.value, day.toString())

    private val fakes = FakeGames(listOf(game))
    private val ui get() = fakes[game]
    private val daily = FakeDailyRepository().apply {
        setToday(game, DailyToday.Fresh(TestGames.puzzle(game)))
        setHistory(game, listOf(DailyEntry(game, day, 7, "alpha/v1", 0L)))
    }
    private val completions = FakeCompletionRepository()
    private val dispatcher = UnconfinedTestDispatcher()
    private val calendar = FakeGameCalendar(day)
    private val saved = FakeSavedPlayRepository()
    private val lane = WriteLane()

    private val store = PlaySessionStore(
        games = fakes.games,
        ui = fakes.registry,
        opening = SlotOpening(
            daily, LoadPastDay(daily, fakes.games), saved, calendar, fakes.games, lane, FakeReceivedPuzzleRepository(),
            FakeMatchRepository(),
            LoadSharedPuzzle(fakes.games, FakeSharedPuzzleRepository(), FakeReceivedPuzzleRepository(), { Budget.Unlimited }, { Instant.EPOCH }),
            testDispatchers(dispatcher),
        ),
        writers = { SlotWriter(it, saved, lane, CoroutineScope(dispatcher)) },
        completions = completions,
        finishLeg = FinishLeg(FakeMatchResultOutbox(), FakeIdentityService()) { Instant.EPOCH },
        calendar = calendar,
        dispatchers = object : DispatcherProvider {
            override val main = dispatcher
            override val io = dispatcher
            override val default = dispatcher
        },
    )

    /** Today's slot opened (and loaded) through the store. */
    private fun loaded(): ActivePlay = store.open(key)!!

    /** The slot's saved row holds [session], as a released or app-stopped play left it. */
    private fun savedRow(session: ByteArray) = runBlocking { saved.save(PlaySlot.Daily(game, day), SessionBlob.encode(game, session), elapsedMs = 0L) }

    private fun records() = runBlocking { completions.observeRecords().first() }

    @Test
    fun `fresh fetch adopts the ready board with today's identity`() {
        val play = loaded()
        assertEquals(PlayBoardStatus.Ready(banner = null), play.board.value.status)
        assertSame(ui.latest, play.controller)
        assertEquals(ref, ui.latest.ref)
        assertEquals(TestGames.puzzle(game).payload.toList(), ui.latest.puzzle!!.payload.toList())
    }

    @Test
    fun `today without a captured row adopts an unnumbered identity`() {
        daily.setHistory(game, emptyList())
        loaded()
        assertEquals(TestGames.Ref(game, PuzzleKey.Daily(day, null)), ui.latest.ref)
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

        daily.setToday(game, DailyToday.Fresh(TestGames.puzzle(game)))
        play.load()
        assertEquals(2, daily.todayCalls(game))
        assertEquals(PlayBoardStatus.Ready(banner = null), play.board.value.status)
    }

    @Test
    fun `a past day the source says doesn't exist is an error without retry`() {
        val past = day.minusDays(1)
        daily.setDay(game, past, DailyDay.Failed(FetchError.NotFound("404")))
        val play = store.open(GameKey(game.value, past.toString()))!!
        assertEquals(PlayBoardStatus.Unavailable(PAST_DAY_OUT_OF_RANGE, retryable = false), play.board.value.status)
    }

    @Test
    fun `an envelope the game rejects is never playable`() {
        ui.rejectWith = "untrusted"
        val play = loaded()
        assertEquals(PlayBoardStatus.Unavailable("untrusted"), play.board.value.status)
    }

    @Test
    fun `a fresh win writes exactly one record and the verdict is a first solve`() {
        val play = loaded()
        assertNull(play.record.value.verdict)

        ui.latest.win()

        assertEquals(listOf(FakeController.record(ref)), records())
        assertEquals(RecordVerdict.FirstSolve, play.record.value.verdict)
        assertFalse(play.record.value.writeFailed)
    }

    @Test
    fun `a practice win (no edition number) still writes its record but never gets a best verdict`() {
        daily.setHistory(game, emptyList())
        val play = loaded()
        val practice = TestGames.Ref(game, PuzzleKey.Daily(day, null))

        ui.latest.win()

        assertEquals(listOf(FakeController.record(practice)), records())
        assertNull(play.record.value.verdict)
        assertFalse(play.record.value.writeFailed)
    }

    @Test
    fun `a win on an already-solved puzzle is a replay beside the record's best`() {
        completions.setRecords(listOf(FakeController.record(ref)))
        val play = loaded()
        ui.latest.win()
        assertEquals(RecordVerdict.PlayAgain, play.record.value.verdict)
        assertEquals("a replay shows the best it plays against", FakeController.TIME_MS, play.record.value.previousBest?.timeMs)
        assertEquals(2, records().size)
    }

    @Test
    fun `a replay faster than the record is still a replay and never ranks`() {
        // The game's best is a slower record of this very puzzle (FirstWinPolicy keeps it
        // the record); the replay beats it on a known board, so it earns no best.
        val slower = FakeController.TIME_MS + 10_000
        completions.setRecords(listOf(FakeController.record(ref).copy(time = slower, isRecord = true)))
        val play = loaded()
        ui.latest.win()
        assertEquals(RecordVerdict.PlayAgain, play.record.value.verdict)
        assertEquals(slower, play.record.value.previousBest?.timeMs)
    }

    @Test
    fun `another game's faster solve never counts as this game's best`() {
        val other = TestGames.Ref(TestGames.beta, PuzzleKey.Daily(day, 7))
        completions.setRecords(listOf(FakeController.record(other).copy(time = 1_000)))
        val play = loaded()
        ui.latest.win()
        assertEquals(RecordVerdict.FirstSolve, play.record.value.verdict)
        assertNull(play.record.value.previousBest)
    }

    @Test
    fun `a solve slower than the game's best on another day is not a new best and shows that best`() {
        val faster = FakeController.TIME_MS - 10_000
        completions.setRecords(listOf(FakeController.record(TestGames.Ref(game, PuzzleKey.Daily(day.minusDays(1), 6))).copy(time = faster)))
        val play = loaded()
        ui.latest.win()
        assertEquals(RecordVerdict.NotBest, play.record.value.verdict)
        assertEquals(faster, play.record.value.previousBest?.timeMs)
    }

    @Test
    fun `replay after a win emits again and is a replay`() {
        val play = loaded()
        ui.latest.win()
        assertEquals(RecordVerdict.FirstSolve, play.record.value.verdict)

        play.playAgain()
        assertEquals(1, ui.latest.replays)
        assertNull("the verdict resets until the new write lands", play.record.value.verdict)
        assertEquals(PlayBoardStatus.Ready(banner = null), play.board.value.status)

        ui.latest.win()
        assertEquals(2, records().size)
        assertEquals(RecordVerdict.PlayAgain, play.record.value.verdict)
    }

    @Test
    fun `replay keeps the degrade banner`() {
        val error = FetchError.Throttled()
        daily.setToday(game, DailyToday.Cached(TestGames.puzzle(game), error))
        val play = loaded()
        play.playAgain()
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
        val play = loaded()
        assertEquals("Alpha · #7", play.record.value.label)

        daily.setHistory(game, emptyList())
        play.load()
        val practice = play.record.value.label
        assertTrue(practice, practice.startsWith("Alpha") && "#" !in practice)
    }

    @Test
    fun `a saved in-progress session resumes without fetching and keeps its identity`() {
        savedRow(FakeController.envelope(game, moves = 2, day = day).session)
        val play = loaded()
        assertEquals(0, daily.todayCalls(game))
        assertEquals(PlayBoardStatus.Ready(banner = null), play.board.value.status)
        assertEquals(2, ui.latest.moves)
        assertEquals("Alpha · #7", play.record.value.label)

        ui.latest.win()
        assertEquals(RecordVerdict.FirstSolve, play.record.value.verdict)
        assertEquals(1, records().size)
    }

    @Test
    fun `a saved completed session shows the win without re-emitting`() {
        savedRow(FakeController.envelope(game, moves = 5, complete = true, day = day).session)
        val play = loaded()
        assertEquals(PlayBoardStatus.Ready(banner = null), play.board.value.status)
        assertTrue(play.controller.isComplete)
        assertTrue(records().isEmpty())
        assertNull(play.record.value.verdict)
    }

    @Test
    fun `a saved session the game cannot read is unavailable, never a loop`() {
        savedRow("not a session".encodeToByteArray())
        val play = loaded()
        assertEquals(PlayBoardStatus.Unavailable("This saved game couldn't be read."), play.board.value.status)
        assertEquals(0, daily.todayCalls(game))
    }

    @Test
    fun `a superseded controller's late win writes nothing`() {
        val play = loaded()
        val superseded = ui.latest
        play.load() // retry → a new controller

        superseded.win()
        assertTrue(records().isEmpty())
        assertNull(play.record.value.verdict)
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
