package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensSession
import com.ivandsky.puzzlebox.games.queens.QueensPuzzleRef
import com.ivandsky.puzzlebox.games.queens.codec.QueensSessionCodec
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** queens-10 wiring contract: DI-shaped fetch → real session → record write on win,
 *  restore, and no-re-emit — mirroring ZipPlayStateTest's scope conventions. */
class QueensPlayStateTest {

    private val scope = CoroutineScope(Dispatchers.Unconfined)

    /** The injected wall clock the play state reads; tests move it explicitly. */
    private var clockMs = 1_000L

    private fun state(
        daily: FakeDailyRepository,
        completions: FakeCompletionRepository = FakeCompletionRepository(),
    ) = QueensPlayState(
        daily = daily,
        completions = completions,
        scope = scope,
        now = { Instant.ofEpochMilli(clockMs) },
    ) to completions

    /** Loads the fixture synchronously (Unconfined scope) and returns the ready state. */
    private fun readyBoard(
        daily: FakeDailyRepository = QueensPlayFixtures.fresh(history = listOf(QueensPlayFixtures.entry())),
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
        assertEquals(QueensPuzzleRef(QueensPlayFixtures.today(), 42), board!!.session.ref)
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
        assertEquals(QueensPuzzleRef(QueensPlayFixtures.today(), 42), record.puzzleRef)
        assertEquals("queens/n8", variantOf(record))
    }

    @Test
    fun `unavailable fetch is a first-class error state`() {
        val (state, _) = state(QueensPlayFixtures.unavailable())
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

    /** R-4 (fix round 4): every committed session — and the fresh one of a load/replay —
     *  reaches the route's saveable, so a process death mid-play still restores. */
    @Test
    fun `the saver sees the fresh session and every committed move`() {
        val saves = ArrayList<QueensSession?>()
        val state = QueensPlayState(
            daily = QueensPlayFixtures.fresh(history = listOf(QueensPlayFixtures.entry())),
            completions = FakeCompletionRepository(),
            scope = scope,
            now = { Instant.ofEpochMilli(clockMs) },
            onSessionChanged = { saves.add(it) },
        )
        state.load()
        assertEquals("fresh session saved before the first move", listOf(state.board!!.session), saves)

        state.board!!.onTap(1) // MANUAL X
        assertTrue(saves.last()!!.markAt(Cell(1)) != null)

        state.undo()
        assertTrue("undo is persisted too", saves.last()!!.markAt(Cell(1)) == null)
        assertTrue(saves.last()!!.history.canRedo)

        state.replay()
        assertEquals("replay saves a fresh session", state.board!!.session, saves.last())
        assertFalse(saves.last()!!.history.canUndo)
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
        val (restored, freshCompletions) = state(QueensPlayFixtures.fresh())
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
     *  duration (first counted action → win, banked clock) and the presented-hint count. */
    @Test
    fun `the win record carries the real elapsed time and hint count`() {
        val completions = FakeCompletionRepository()
        val (s, _) = readyBoard(completions = completions)
        val board = s.board!!
        clockMs = 1_000L
        board.requestHint() // a presented hint is play: it opens the window
        clockMs = 61_000L
        solve(board)
        val record = runBlocking { completions.observeRecords().first().single() }
        assertEquals("record time is the banked window, not wall-clock since load", 60_000L, record.time)
        assertEquals(1, record.hints)
        assertEquals(60_000L, board.session.elapsedMs)
    }

    @Test
    fun `the clock opens on the first action and background banks the running window`() {
        val (state, _) = readyBoard()
        assertNull("no action yet, no running window", state.clockWindow)
        assertEquals(0L, state.displayedElapsedMs())

        clockMs = 10_000L
        state.board!!.onTap(0)
        assertNotNull(state.clockWindow)
        clockMs = 14_000L
        state.tick()
        assertEquals(4_000L, state.displayedElapsedMs())

        clockMs = 15_000L
        state.onBackground()
        assertNull(state.clockWindow)
        assertEquals(5_000L, state.board!!.session.elapsedMs)
        clockMs = 60_000L
        state.tick()
        assertEquals("a stopped clock does not drift", 5_000L, state.displayedElapsedMs())
    }

    @Test
    fun `foreground reopens the window only for a started unfinished session`() {
        val (state, _) = readyBoard()
        state.onForeground()
        assertNull("nothing played yet", state.clockWindow)

        state.board!!.onTap(0)
        state.onBackground()
        assertNull(state.clockWindow)
        state.onForeground()
        assertNotNull(state.clockWindow)

        // Cell 0 already carries its X from the first tap: one more tap crowns it, the
        // rest take the usual two.
        state.board!!.onTap(QueensPlayFixtures.solution.first().idx)
        for (cell in QueensPlayFixtures.solution.drop(1)) {
            state.board!!.onTap(cell.idx)
            state.board!!.onTap(cell.idx)
        }
        assertTrue(state.board!!.session.isComplete)
        assertNull("the win stops the clock", state.clockWindow)
        state.onForeground()
        assertNull("a finished session never restarts it", state.clockWindow)
    }

    @Test
    fun `a restored in-progress session restarts its window and keeps the banked time`() {
        val (state, _) = readyBoard()
        clockMs = 1_000L
        state.board!!.onTap(0)
        clockMs = 4_000L
        state.onBackground()
        val bytes = QueensSessionCodec.serialize(state.board!!.session)

        val (restored, _) = state(QueensPlayFixtures.fresh())
        restored.adoptRestored(bytes)
        assertNotNull(restored.clockWindow)
        clockMs = 6_000L
        restored.tick()
        assertEquals(3_000L + 2_000L, restored.displayedElapsedMs())
    }

    @Test
    fun `undo and redo go through the state and never touch the clock or the hint count`() {
        val (state, _) = readyBoard()
        clockMs = 1_000L
        state.requestHint() // presented on the empty board: counts and opens the window
        state.board!!.onTap(0)
        clockMs = 5_000L
        state.onBackground()
        val banked = state.board!!.session.elapsedMs
        assertEquals(4_000L, banked)
        assertEquals(1, state.hintsUsed)

        assertTrue(state.canUndo)
        state.undo()
        assertTrue(state.canRedo)
        assertEquals(banked, state.board!!.session.elapsedMs)
        assertEquals(1, state.hintsUsed)
        state.redo()
        assertFalse(state.canRedo)
        assertTrue(state.board!!.session.markAt(Cell(0)) != null)
        assertEquals(banked, state.board!!.session.elapsedMs)
    }

    @Test
    fun `undo is locked once the board is solved`() {
        val (state, _) = readyBoard()
        solve(state.board!!)
        val solved = state.board!!.session

        assertFalse(state.canUndo)
        state.undo()
        assertEquals(solved, state.board!!.session)
    }

    @Test
    fun `hint and load availability follow the screen and the win`() {
        val (state, _) = state(QueensPlayFixtures.unavailable())
        assertTrue(state.needsLoad)
        assertFalse(state.hintEnabled)
        state.load()
        assertFalse("an error screen has no board to hint on", state.hintEnabled)
        assertTrue(state.needsLoad)

        val (ready, _) = readyBoard()
        assertFalse(ready.needsLoad)
        assertTrue(ready.hintEnabled)
        solve(ready.board!!)
        assertFalse(ready.hintEnabled)
        assertTrue("a finished session must fetch fresh", ready.needsLoad)
    }

    @Test
    fun `the result label carries the puzzle number`() {
        val (state, _) = readyBoard()
        assertEquals("Queens · #42", state.resultLabel)
    }

    private fun solve(board: com.ivandsky.puzzlebox.games.queensui.QueensBoardState) {
        // The §5 tap cycle: markless → MANUAL X → queen — two taps per solution cell.
        for (cell in QueensPlayFixtures.solution) {
            board.onTap(cell.idx)
            board.onTap(cell.idx)
        }
    }

    private fun variantOf(record: com.ivandsky.puzzlebox.game.CompletionRecord): String {
        // The stats blob is a flat JSON object built in insertion order: v then variant.
        val blob = record.stats.blob
        return blob.substringAfter("\"variant\":").substringBefore("}").trim('"')
    }
}
