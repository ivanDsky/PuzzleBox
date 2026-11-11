package com.ivandsky.puzzlebox.games.queensui

import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.MarkKind
import com.ivandsky.puzzlebox.games.queens.QueensSession
import com.ivandsky.puzzlebox.games.queens.codec.QueensSessionCodec
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.Restored
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** queens-10 wiring contract at the controller seam: adopt → real session → one win
 *  record handed to the environment, restore, and no-re-emit. */
class QueensPlayControllerTest {

    /** The injected wall clock the controller reads; tests move it explicitly. */
    private var clockMs = 1_000L

    private val saved = ArrayList<ByteArray>()
    private val won = ArrayList<CompletionRecord>()

    private val settings = InMemoryQueensSettings()

    private fun controller() = QueensPlayController(
        env = PlayEnvironment(CoroutineScope(Dispatchers.Unconfined), { saved += it }, { won += it }),
        settings = settings,
        hintMode = QueensPlayFixtures.hintMode,
        now = { Instant.ofEpochMilli(clockMs) },
    )

    private fun readyBoard(): QueensPlayController = controller().also {
        assertEquals(Adopt.Adopted, it.adoptToday(QueensPlayFixtures.envelope(), QueensPlayFixtures.ref()))
    }

    private fun savedSession(bytes: ByteArray = saved.last()): QueensSession =
        (QueensSessionCodec.deserialize(bytes) as DecodeResult.Ok).value

    @Test
    fun `adoptToday binds a real session with the day's ref`() {
        val state = readyBoard()
        val board = state.board
        assertNotNull(board)
        assertEquals(QueensPlayFixtures.puzzle(), board!!.session.puzzle)
        assertEquals(QueensPlayFixtures.ref(), board.session.ref)
        assertFalse(board.session.isComplete)
    }

    @Test
    fun `completing the board emits one win record with the day's identity`() {
        val state = readyBoard()
        val board = state.board!!
        solve(board)

        assertTrue(board.session.isComplete)
        assertTrue(board.winEmitted)
        assertEquals(1, won.size)
        val record = won.single()
        assertEquals(QueensPlayFixtures.ref(), record.puzzleRef)
        assertEquals("queens/n8", variantOf(record))
    }

    @Test
    fun `an untrustable payload is rejected and binds no board`() {
        val state = controller()
        val good = QueensPlayFixtures.envelope()
        val envelope = com.ivandsky.puzzlebox.game.Puzzle(good.gameId, good.variant, good.formatVersion, byteArrayOf(9, 9, 9))
        assertTrue(state.adoptToday(envelope, QueensPlayFixtures.ref()) is Adopt.Rejected)
        assertNull(state.board)
    }

    @Test
    fun `a corrupt restored blob is unreadable`() {
        val state = controller()
        assertNull(state.adoptRestored(byteArrayOf(9, 9, 9)))
        assertNull(state.board)
    }

    /** R-4 (fix round 4): every committed session — and the fresh one of an adopt/replay —
     *  reaches the environment, so a process death mid-play still restores. */
    @Test
    fun `the saver sees the fresh session and every committed move`() {
        val state = readyBoard()
        assertEquals("fresh session saved before the first move", 1, saved.size)
        assertEquals(state.board!!.session, savedSession())

        state.board!!.onTap(1) // MANUAL X
        assertTrue(savedSession().markAt(Cell(1)) != null)

        state.undo()
        assertTrue("undo is persisted too", savedSession().markAt(Cell(1)) == null)
        assertTrue(savedSession().history.canRedo)

        state.playAgain()
        assertEquals("replay saves a fresh session", state.board!!.session, savedSession())
        assertFalse(savedSession().history.canUndo)
    }

    @Test
    fun `a restored completed session shows the win without re-emitting`() {
        val state = readyBoard()
        solve(state.board!!)
        val bytes = QueensSessionCodec.serialize(state.board!!.session)
        won.clear()

        val restored = controller()
        assertEquals(Restored(QueensPlayFixtures.ref()), restored.adoptRestored(bytes))
        assertTrue(restored.board!!.session.isComplete)
        assertFalse(restored.board!!.winEmitted)
        assertTrue(restored.isComplete)
        assertEquals(0, won.size)
    }

    /** A4 controller half: a replayed win is a new transition and emits again. */
    @Test
    fun `replay after a win emits a second record on the next win`() {
        val state = readyBoard()
        solve(state.board!!)
        assertEquals(1, won.size)

        state.playAgain()
        assertFalse(state.isComplete)
        solve(state.board!!)
        assertEquals(2, won.size)
        assertEquals(QueensPlayFixtures.ref(), won.last().puzzleRef)
    }

    /** Fix round 6 (F-3): the emitted record carries the REAL frozen facts — the play
     *  duration (first counted action → win, banked clock) and the presented-hint count. */
    @Test
    fun `the win record carries the real elapsed time and hint count`() {
        val s = readyBoard()
        val board = s.board!!
        clockMs = 1_000L
        board.requestHint() // a presented hint is play: it opens the window
        clockMs = 61_000L
        solve(board)
        val record = won.single()
        assertEquals("record time is the banked window, not wall-clock since load", 60_000L, record.time)
        assertEquals(1, record.hints)
        assertEquals(60_000L, board.session.elapsedMs)
    }

    /** What the timer bar reads after the loop's next tick. */
    private fun QueensPlayController.shown(): Long {
        tick()
        return displayedElapsedMs()
    }

    @Test
    fun `a fresh puzzle's timer runs with no move made`() {
        val state = readyBoard()
        assertNotNull("the window opens with the puzzle", state.clockWindow)

        clockMs += 4_000L

        assertFalse(state.board!!.session.history.canUndo)
        assertEquals(4_000L, state.shown())
    }

    @Test
    fun `background banks the running window and foreground reopens it without a move`() {
        val state = readyBoard()
        clockMs += 5_000L

        state.onBackground()
        assertNull(state.clockWindow)
        assertEquals(5_000L, state.board!!.session.elapsedMs)
        clockMs += 45_000L
        assertEquals("a stopped clock does not drift", 5_000L, state.shown())

        state.onForeground()
        assertNotNull(state.clockWindow)
        clockMs += 1_000L
        assertEquals(6_000L, state.shown())
    }

    @Test
    fun `a finished session stops the clock and never restarts it`() {
        val state = readyBoard()
        clockMs += 8_000L
        solve(state.board!!)

        assertTrue(state.board!!.session.isComplete)
        assertNull("the win stops the clock", state.clockWindow)
        assertEquals(8_000L, state.board!!.session.elapsedMs)
        state.onForeground()
        assertNull("a finished session never restarts it", state.clockWindow)
    }

    @Test
    fun `a restored untouched session's timer runs too`() {
        val state = readyBoard()
        state.onBackground()
        val bytes = QueensSessionCodec.serialize(state.board!!.session)

        val restored = controller()
        assertNotNull(restored.adoptRestored(bytes))
        clockMs += 1_500L

        assertEquals(1_500L, restored.shown())
    }

    @Test
    fun `replay restarts from zero and advances at once`() {
        val state = readyBoard()
        clockMs += 8_000L
        solve(state.board!!)

        state.playAgain()
        assertNotNull(state.clockWindow)
        assertEquals(0L, state.shown())
        clockMs += 700L

        assertEquals(700L, state.shown())
    }

    @Test
    fun `a restored in-progress session restarts its window and keeps the banked time`() {
        val state = readyBoard()
        clockMs = 1_000L
        state.board!!.onTap(0)
        clockMs = 4_000L
        state.onBackground()
        val bytes = QueensSessionCodec.serialize(state.board!!.session)

        val restored = controller()
        assertNotNull(restored.adoptRestored(bytes))
        assertNotNull(restored.clockWindow)
        clockMs = 6_000L
        restored.tick()
        assertEquals(3_000L + 2_000L, restored.displayedElapsedMs())
    }

    @Test
    fun `undo and redo go through the controller and never touch the clock or the hint count`() {
        val state = readyBoard()
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
        val state = readyBoard()
        solve(state.board!!)
        val solved = state.board!!.session

        assertFalse(state.canUndo)
        state.undo()
        assertEquals(solved, state.board!!.session)
    }

    @Test
    fun `hint availability and completion follow the board and the win`() {
        val empty = controller()
        assertFalse("no board to hint on", empty.hintEnabled)
        assertFalse(empty.isComplete)

        val ready = readyBoard()
        assertFalse(ready.isComplete)
        assertTrue(ready.hintEnabled)
        solve(ready.board!!)
        assertFalse(ready.hintEnabled)
        assertTrue("a finished session must fetch fresh", ready.isComplete)
    }

    /** Ticket 08: the conflict line is the controller's PlayFeedback.violation — the
     *  shared caption slot shows it; the controls no longer grow a conditional row. */
    @Test
    fun `a live conflict surfaces as the feedback violation and clears when resolved`() {
        val state = readyBoard()
        val board = state.board!!
        assertNull(state.violation)
        board.onTap(1); board.onTap(1) // X then queen at (0,1)
        assertNull(state.violation)
        board.onTap(2) // AUTO X → queen at (0,2): same row → conflict
        assertEquals(
            "Queens can't touch or share a row, column, or color.",
            state.violation,
        )
        assertNull(state.rejection)
        state.undo()
        assertNull(state.violation)
    }

    // ------------------------------------------------------------ Auto-cross is a per-game setting

    private fun QueensBoardState.hasAutoMarks() = session.marks.values.any { it.kind == MarkKind.AUTO }

    private fun QueensBoardState.placeQueen(idx: Int) {
        onTap(idx) // X
        onTap(idx) // queen
    }

    /** The ⋮ menu's Auto-cross item as the screen reads it. */
    private fun QueensPlayController.autoCrossItem() = QueensPlayFixtures.playUi(settings).overflowFor(this).single { it.label == "Auto-cross" }

    @Test
    fun `flipping Auto-cross writes the setting and re-derives the open board at once`() {
        val state = readyBoard()
        val board = state.board!!
        board.placeQueen(0)
        assertTrue(board.hasAutoMarks())
        assertTrue(state.autoCrossItem().checked == true)

        val savesBefore = saved.size
        state.autoCrossItem().onClick()

        assertEquals("one write, the new value", listOf(false), settings.writes)
        assertFalse(settings.stored.value)
        assertFalse(state.autoCross)
        assertFalse("the open board lost its AUTO marks", board.hasAutoMarks())
        assertTrue(Cell(0) in board.session.queens)
        assertTrue(state.autoCrossItem().checked == false)
        assertTrue("the flip is saved", saved.size > savesBefore)

        state.autoCrossItem().onClick()

        assertEquals(listOf(false, true), settings.writes)
        assertTrue("flipping back re-derives them", board.hasAutoMarks())
    }

    @Test
    fun `a setting changed elsewhere re-derives the open board`() {
        val state = readyBoard()
        val board = state.board!!
        board.placeQueen(0)

        settings.stored.value = false

        assertFalse(state.autoCross)
        assertFalse(board.hasAutoMarks())
    }

    @Test
    fun `a flip is not an undo unit and undo never brings AUTO marks back`() {
        val state = readyBoard()
        val board = state.board!!
        board.placeQueen(0)
        state.autoCrossItem().onClick()

        state.undo() // reverts the queen's placement, not the flip

        assertFalse(state.autoCross)
        assertFalse(board.hasAutoMarks())
        state.redo()
        assertTrue(Cell(0) in board.session.queens)
        assertFalse("redo under OFF stays bare", board.hasAutoMarks())
    }

    @Test
    fun `a new session starts under the stored value`() {
        settings.stored.value = false
        val state = readyBoard()
        val board = state.board!!
        assertFalse(board.session.autoCross)

        board.placeQueen(0)
        assertFalse("OFF places queens bare", board.hasAutoMarks())

        state.playAgain()
        assertFalse(state.board!!.session.autoCross)
        state.board!!.placeQueen(0)
        assertFalse(state.board!!.hasAutoMarks())
    }

    @Test
    fun `a restored save follows the setting, not the one it was played under`() {
        val played = readyBoard()
        played.board!!.placeQueen(0)
        assertTrue(played.board!!.hasAutoMarks())
        val bytes = savedSession().let(QueensSessionCodec::serialize)

        settings.stored.value = false
        val restored = controller()
        assertEquals(Restored(QueensPlayFixtures.ref()), restored.adoptRestored(bytes))

        assertFalse(restored.board!!.session.autoCross)
        assertFalse(restored.board!!.hasAutoMarks())
        assertTrue(Cell(0) in restored.board!!.session.queens)
    }

    /** The stored value is read at creation: nothing the settings flow delivers later is needed for frame 0. */
    @Test
    fun `a restored board with Auto-cross OFF shows no AUTO marks at frame 0`() {
        val played = readyBoard()
        played.board!!.placeQueen(0)
        assertTrue(played.board!!.hasAutoMarks())
        val bytes = savedSession().let(QueensSessionCodec::serialize)

        settings.stored.value = false
        val neverRuns = CoroutineScope(StandardTestDispatcher()) // the collector never delivers
        val cold = QueensPlayController(PlayEnvironment(neverRuns, { saved += it }, { won += it }), settings, hintMode = QueensPlayFixtures.hintMode)
        assertEquals(Restored(QueensPlayFixtures.ref()), cold.adoptRestored(bytes))

        assertFalse(cold.autoCross)
        assertFalse(cold.board!!.hasAutoMarks())
        assertTrue(Cell(0) in cold.board!!.session.queens)
    }

    private fun solve(board: QueensBoardState) {
        // The §5 tap cycle: markless → MANUAL X → queen — two taps per solution cell.
        for (cell in QueensPlayFixtures.solution) {
            board.onTap(cell.idx)
            board.onTap(cell.idx)
        }
    }

    private fun variantOf(record: CompletionRecord): String {
        // The stats blob is a flat JSON object built in insertion order: v then variant.
        val blob = record.stats.blob
        return blob.substringAfter("\"variant\":").substringBefore("}").trim('"')
    }
}
