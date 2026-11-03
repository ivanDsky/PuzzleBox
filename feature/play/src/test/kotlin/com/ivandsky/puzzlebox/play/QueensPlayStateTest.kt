package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.data.repository.QueensDailyEntry
import com.ivandsky.puzzlebox.data.repository.QueensToday
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensSession
import com.ivandsky.puzzlebox.games.queens.QueensPuzzleRef
import com.ivandsky.puzzlebox.games.queens.codec.QueensSessionCodec
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** queens-10 wiring contract: DI-shaped fetch → real session → record write on win,
 *  restore, and no-re-emit — mirroring ZipPlayStateTest's scope conventions. */
class QueensPlayStateTest {

    private val scope = CoroutineScope(Dispatchers.Unconfined)

    // QueensPlayFlow owns a Dispatchers.Main.immediate scope — a test Main is required.
    @Before
    fun setUpMain() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @After
    fun tearDownMain() {
        Dispatchers.resetMain()
    }

    private fun state(
        daily: FakeQueensDailyRepository,
        completions: FakeCompletionRepository = FakeCompletionRepository(),
    ) = QueensPlayState(
        daily = daily,
        completions = completions,
        scope = scope,
        now = { Instant.ofEpochMilli(1_000L) },
    ) to completions

    /** Loads the fixture synchronously (Unconfined scope) and returns the ready state. */
    private fun readyBoard(
        daily: FakeQueensDailyRepository = FakeQueensDailyRepository(
            todayResult = QueensToday.Fresh(QueensPlayFixtures.envelope()),
            history = listOf(QueensDailyEntry(FakeQueensDailyRepository.today(), 42, "queens/n8", 0L)),
        ),
        completions: FakeCompletionRepository = FakeCompletionRepository(),
    ): Pair<QueensPlayState, FakeCompletionRepository> {
        val (state, fake) = state(daily, completions)
        state.load()
        return state to fake
    }

    @Test
    fun `load fetches today and binds a real session with the day's ref`() {
        val (state, _) = readyBoard()

        val ready = state.screen as? QueensScreen.Ready
        assertNotNull(ready)
        assertEquals(QueensPlayFixtures.puzzle(), ready!!.puzzle)
        val board = state.board
        assertNotNull(board)
        assertEquals(QueensPuzzleRef(FakeQueensDailyRepository.today(), 42), board!!.session.ref)
        assertFalse(board.session.isComplete)
    }

    @Test
    fun `completing the board records one history entry exactly as the zip path does`() {
        val (state, completions) = readyBoard()
        val board = state.board!!

        // The §5 tap cycle: markless → MANUAL X → queen — two taps per solution cell.
        for (cell in QueensPlayFixtures.solution) {
            board.onTap(cell.idx)
            board.onTap(cell.idx)
        }

        assertTrue(board.session.isComplete)
        assertTrue(board.winEmitted)
        val records = runBlocking { completions.observeRecords().first() }
        assertEquals(1, records.size)
        val record = records.single()
        assertEquals(QueensPuzzleRef(FakeQueensDailyRepository.today(), 42), record.puzzleRef)
        assertEquals("queens/n8", variantOf(record))
    }

    @Test
    fun `unavailable fetch is a first-class error state`() {
        val (state, _) = state(FakeQueensDailyRepository.unavailable())
        state.load()
        assertTrue(state.screen is QueensScreen.Unavailable)
        assertNull(state.board)
    }

    @Test
    fun `a corrupt restored blob falls back to a fresh load`() {
        val (state, _) = readyBoard()
        state.adoptRestored(byteArrayOf(9, 9, 9))
        // The fixture fetch succeeds, so the fallback re-fetches and binds a new board.
        assertTrue(state.screen is QueensScreen.Ready)
        assertNotNull(state.board)
    }

    /** R-4 (fix round 4): a re-entered entry rebinds the saver and backfills it with
     *  the LIVE session — the old entry's rememberSaveable is dead after rotation. */
    @Test
    fun `re-entering the flow rebinds the saver and backfills the live session`() {
        val flow = QueensPlayFlow(
            daily = FakeQueensDailyRepository(
                todayResult = QueensToday.Fresh(QueensPlayFixtures.envelope()),
                history = listOf(QueensDailyEntry(FakeQueensDailyRepository.today(), 42, "queens/n8", 0L)),
            ),
            completions = FakeCompletionRepository(),
        )
        val entry1Saves = ArrayList<QueensSession?>()
        val s1 = flow.start { entry1Saves.add(it) }
        s1.load() // the route's LaunchedEffect does this on a real entry
        assertNotNull(s1.board) // Unconfined Main: the fetch completed synchronously
        s1.board!!.onTap(0) // MANUAL X in entry 1
        val live = s1.board!!.session

        // Entry 2 (rotation): flow.start must rebind to the NEW saver and backfill it
        // with the live session; subsequent commits serialize into the new entry only.
        val entry2Saves = ArrayList<QueensSession?>()
        val s2 = flow.start { entry2Saves.add(it) }
        assertEquals("same flow state", s1, s2)
        assertEquals("live session backfilled into the new saver", listOf(live), entry2Saves)
        s2.board!!.onTap(1) // MANUAL X in entry 2
        assertTrue("post-rebind commit lands in the new saver", entry2Saves.last()!!.markAt(Cell(1)) != null)
    }

    @Test
    fun `a restored completed session shows the win without re-emitting`() {
        // Play to completion, capture the session bytes.
        val (state, completions) = readyBoard()
        val board = state.board!!
        for (cell in QueensPlayFixtures.solution) {
            board.onTap(cell.idx)
            board.onTap(cell.idx)
        }
        val bytes = QueensSessionCodec.serialize(board.session)

        // A fresh entry adopts the same completed session — no second write (A4).
        val (restored, freshCompletions) = state(
            FakeQueensDailyRepository(QueensToday.Fresh(QueensPlayFixtures.envelope())),
        )
        restored.adoptRestored(bytes)
        assertTrue(restored.board!!.session.isComplete)
        assertFalse(restored.board!!.winEmitted)
        assertEquals(0, runBlocking { freshCompletions.observeRecords().first().size })
    }

    // --------------------------------- fix round 6: result-route badge/retry plumbing

    /** A4 badge: derived from the session-start baseline — TRUE for a first win, FALSE
     *  for a replay of an already-recorded puzzle; reset to null while re-baselining. */
    @Test
    fun `win derives the record badge from the session-start baseline`() {
        val (state, completions) = readyBoard()
        val board = state.board!!
        for (cell in QueensPlayFixtures.solution) {
            board.onTap(cell.idx)
            board.onTap(cell.idx)
        }
        assertEquals("first win, no baseline record → badge TRUE", true, state.recordBadge)
        assertFalse(state.recordWriteFailed)

        state.replay()
        assertNull("badge resets until the next write lands", state.recordBadge)
        val board2 = state.board!!
        for (cell in QueensPlayFixtures.solution) {
            board2.onTap(cell.idx)
            board2.onTap(cell.idx)
        }
        assertEquals("replay of a recorded puzzle → badge FALSE (A4)", false, state.recordBadge)
        assertEquals(2, runBlocking { completions.observeRecords().first().size })
    }

    @Test
    fun `a failed record write surfaces retryable - retry lands it`() {
        val flaky = FakeCompletionRepository().apply { fail = true }
        val (state, _) = readyBoard(completions = flaky)
        val board = state.board!!
        for (cell in QueensPlayFixtures.solution) {
            board.onTap(cell.idx)
            board.onTap(cell.idx)
        }
        assertTrue("the win stands regardless of the write", board.session.isComplete)
        assertTrue(state.recordWriteFailed)
        assertNull(state.recordBadge)
        assertEquals(0, runBlocking { flaky.observeRecords().first().size })

        flaky.fail = false
        state.retryRecordWrite()
        assertEquals(1, runBlocking { flaky.observeRecords().first().size })
        assertFalse(state.recordWriteFailed)
        assertEquals(true, state.recordBadge)
    }

    /** Fix round 6 (F-3): the emitted record carries the REAL frozen facts — the play
     *  duration (first action → win) and the presented-hint count. */
    @Test
    fun `the win record carries the real elapsed time and hint count`() {
        val tick = AtomicLong(0)
        val completions = FakeCompletionRepository()
        val s = QueensPlayState(
            daily = FakeQueensDailyRepository(
                todayResult = QueensToday.Fresh(QueensPlayFixtures.envelope()),
                history = listOf(QueensDailyEntry(FakeQueensDailyRepository.today(), 42, "queens/n8", 0L)),
            ),
            completions = completions,
            scope = scope,
            now = { Instant.ofEpochMilli(tick.getAndAdd(100)) },
        )
        s.load()
        val board = s.board!!
        board.requestHint() // one presented hint on the empty board (no board action, no clock)
        for (cell in QueensPlayFixtures.solution) {
            board.onTap(cell.idx)
            board.onTap(cell.idx)
        }
        val record = runBlocking { completions.observeRecords().first().single() }
        assertTrue("elapsed is the real play duration, not 0", record.time > 0)
        assertEquals(1, record.hints)
    }

    private fun variantOf(record: com.ivandsky.puzzlebox.game.CompletionRecord): String {
        // The stats blob is a flat JSON object built in insertion order: v then variant.
        val blob = record.stats.blob
        return blob.substringAfter("\"variant\":").substringBefore("}").trim('"')
    }
}
