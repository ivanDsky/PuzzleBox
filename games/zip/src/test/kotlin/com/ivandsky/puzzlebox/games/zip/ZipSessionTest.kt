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
}
