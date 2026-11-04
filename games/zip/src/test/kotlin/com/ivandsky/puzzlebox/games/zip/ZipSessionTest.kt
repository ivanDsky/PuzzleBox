package com.ivandsky.puzzlebox.games.zip

import com.ivandsky.puzzlebox.games.zip.engine.ZipMove
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Session transitions: timer starts on first Extend, pauses off-foreground, completes exactly once (A4). */
class ZipSessionTest {

    private val t0: Instant = Instant.ofEpochMilli(1_000_000)
    private val puzzle = ZipTestSupport.n5Puzzle()

    private fun session(path: List<Cell> = emptyList()) = ZipSession(
        ref = ZipPuzzleRef(java.time.LocalDate.of(2026, 10, 30), 592),
        puzzle = puzzle,
        path = path,
    )

    @Test
    fun `first extend starts the timer exactly once`() {
        val s1 = session().withMove(ZipMove.Extend(Cell(0)), t0)
        assertTrue(s1.timerStarted)
        assertEquals(t0, s1.runningSince)
        val s2 = s1.withMove(ZipMove.Extend(Cell(1)), t0.plusSeconds(5))
        assertEquals(t0, s2.runningSince) // anchor unchanged by later moves
        assertTrue(s1.path.last() == Cell(0))
    }

    @Test
    fun `extend appends and retract trims with a backtrack counted`() {
        val s = session()
            .withMove(ZipMove.Extend(Cell(0)), t0)
            .withMove(ZipMove.Extend(Cell(1)), t0)
            .withMove(ZipMove.Extend(Cell(2)), t0)
        val trimmed = s.withMove(ZipMove.RetractTo(Cell(1)), t0)
        assertEquals(listOf(Cell(0), Cell(1)), trimmed.path)
        assertEquals(1, trimmed.backtrackCount)
        // retract to the tail is a no-op, off-trace is refused
        assertEquals(s, s.withMove(ZipMove.RetractTo(Cell(2)), t0))
        assertEquals(s, s.withMove(ZipMove.RetractTo(Cell(4)), t0))
    }

    @Test
    fun `reset clears the trace and counts one backtrack`() {
        val s = session().withMove(ZipMove.Extend(Cell(0)), t0).withMove(ZipMove.Extend(Cell(1)), t0)
        val reset = s.withMove(ZipMove.Reset, t0)
        assertTrue(reset.path.isEmpty())
        assertEquals(1, reset.backtrackCount)
        assertTrue(reset.timerStarted) // the clock keeps running across a reset
        // reset on an already-empty trace is a no-op
        assertEquals(session(), session().withMove(ZipMove.Reset, t0))
    }

    @Test
    fun `rejections count mistakes without touching the timer`() {
        val s = session().withRejection()
        assertEquals(1, s.mistakeCount)
        assertFalse(s.timerStarted)
        assertNull(s.runningSince)
    }

    @Test
    fun `hints count one per user action`() {
        val s = session().withHintApplied().withHintApplied()
        assertEquals(2, s.hintsUsed)
    }

    @Test
    fun `completion folds the timer and stamps completedAt once`() {
        var s = session().withMove(ZipMove.Extend(Cell(0)), t0)
        // walk the forced solution; pretend 2s elapsed between moves
        var now = t0
        ZipTestSupport.n5Solution.drop(1).forEach { cell ->
            now = now.plusSeconds(2)
            s = s.withMove(ZipMove.Extend(cell), now)
        }
        assertEquals(now, s.completedAt)
        assertNull(s.runningSince)
        assertEquals(48_000, s.elapsedMs) // anchor folded at the winning move: 24 x 2s since t0
        assertEquals(25, s.path.size) // full path present
        // one play-through: further moves are ignored, completedAt unchanged
        val after = s.withMove(ZipMove.Extend(Cell(1)), now.plusSeconds(9))
        assertEquals(s, after)
    }

    @Test
    fun `background gaps never count toward elapsed`() {
        var s = session().withMove(ZipMove.Extend(Cell(0)), t0) // timer starts
        s = s.withMove(ZipMove.Extend(Cell(1)), t0.plusSeconds(10))
        s = s.onBackground(t0.plusSeconds(60)) // banks the full running window (anchor = t0)
        assertEquals(60_000, s.elapsedMs)
        assertNull(s.runningSince)
        s = s.onForeground(t0.plusSeconds(3600)) // the gap stays unbanked
        assertEquals(t0.plusSeconds(3600), s.runningSince)
        assertEquals(61_000, s.displayedElapsedMs(t0.plusSeconds(3601)))
    }

    @Test
    fun `foreground does not start an untouched timer`() {
        val s = session().onForeground(t0)
        assertFalse(s.timerStarted)
        assertNull(s.runningSince)
        assertEquals(0, s.displayedElapsedMs(t0.plusSeconds(100)))
    }

    @Test
    fun `background without a running anchor is a no-op`() {
        val s = session().onBackground(t0)
        assertEquals(0, s.elapsedMs)
        assertNull(s.runningSince)
    }

    private fun played(vararg cells: Int, at: Instant = t0): ZipSession =
        cells.fold(session()) { s, idx -> s.withMove(ZipMove.Extend(Cell(idx)), at) }

    @Test
    fun `undo and redo walk the trace one command at a time`() {
        val s = played(0, 1, 2, 3).withMove(ZipMove.RetractTo(Cell(1)), t0)
        assertEquals(path(0, 1), s.path)

        val undoneRetract = s.withUndo()
        assertEquals(path(0, 1, 2, 3), undoneRetract.path) // one drag-back = one undo
        assertEquals(path(0, 1, 2), undoneRetract.withUndo().path) // extends undo per cell
        assertEquals(path(0, 1), undoneRetract.withRedo().path)

        val reset = s.withMove(ZipMove.Reset, t0)
        assertTrue(reset.path.isEmpty())
        assertEquals(path(0, 1), reset.withUndo().path)
        assertTrue(reset.withUndo().withRedo().path.isEmpty())
    }

    @Test
    fun `undoing everything and redoing everything returns to the same session`() {
        val s = played(0, 1, 2).withMove(ZipMove.RetractTo(Cell(0)), t0).withMove(ZipMove.Extend(Cell(1)), t0)
        var undone = s
        while (undone.history.canUndo) undone = undone.withUndo()
        assertTrue(undone.path.isEmpty())
        var redone = undone
        while (redone.history.canRedo) redone = redone.withRedo()
        assertEquals(s, redone)
    }

    @Test
    fun `a new move clears the redo stack but a no-op move does not`() {
        val undone = played(0, 1, 2).withUndo()
        assertTrue(undone.history.canRedo)

        // retract to the tail and reset-on-nothing record nothing; redo survives
        assertEquals(undone, undone.withMove(ZipMove.RetractTo(Cell(1)), t0))
        assertEquals(undone, undone.withMove(ZipMove.RetractTo(Cell(24)), t0))

        val forked = undone.withMove(ZipMove.Extend(Cell(6)), t0)
        assertFalse(forked.history.canRedo)
        assertEquals(forked, forked.withRedo())
        assertEquals(path(0, 1, 6), forked.path)
    }

    @Test
    fun `undo and redo with nothing to move are no-ops`() {
        assertEquals(session(), session().withUndo())
        assertEquals(session(), session().withRedo())
    }

    @Test
    fun `undo never rewinds the timer, hints, mistakes or backtracks`() {
        val base = played(0, 1, 2, 3)
            .withMove(ZipMove.RetractTo(Cell(1)), t0.plusSeconds(4))
            .withRejection()
            .withHintApplied()
            .onBackground(t0.plusSeconds(9))
        fun assertCountersKept(after: ZipSession) {
            assertEquals(base.hintsUsed, after.hintsUsed)
            assertEquals(base.mistakeCount, after.mistakeCount)
            assertEquals(base.backtrackCount, after.backtrackCount)
            assertEquals(base.elapsedMs, after.elapsedMs)
            assertEquals(base.timerStarted, after.timerStarted)
            assertEquals(base.runningSince, after.runningSince)
        }
        val undone = base.withUndo()
        assertEquals(path(0, 1, 2, 3), undone.path)
        assertCountersKept(undone)
        assertCountersKept(undone.withRedo())
        // even undoing back to the empty trace keeps the started clock
        var all = base
        while (all.history.canUndo) all = all.withUndo()
        assertTrue(all.path.isEmpty())
        assertCountersKept(all)
    }

    @Test
    fun `a hint-placed step is an ordinary extend that undoes`() {
        val s = played(0, 1).withHintApplied().withMove(ZipMove.Extend(Cell(2)), t0) // hint auto-places cell 2
        val undone = s.withUndo()
        assertEquals(path(0, 1), undone.path)
        assertEquals(1, undone.hintsUsed) // the hint stays counted
    }

    @Test
    fun `a completed session is frozen against undo and redo`() {
        var s = session()
        ZipTestSupport.n5Solution.forEach { s = s.withMove(ZipMove.Extend(it), t0.plusSeconds(1)) }
        assertTrue(s.isComplete)
        assertTrue(s.history.canUndo)
        assertEquals(s, s.withUndo())
        assertEquals(s, s.withRedo())
    }

    @Test
    fun `history keeps only the newest MAX_HISTORY commands`() {
        // reset <-> extend 0 cycles: two recorded commands each, far past the cap
        var s = played(0)
        repeat(ZipSession.MAX_HISTORY) {
            s = s.withMove(ZipMove.Reset, t0).withMove(ZipMove.Extend(Cell(0)), t0)
        }
        assertEquals(ZipSession.MAX_HISTORY, s.history.done.size)
        assertEquals(path(0), s.path)
        assertEquals(emptyList<Cell>(), s.withUndo().path) // the newest command is still undoable
    }
}
