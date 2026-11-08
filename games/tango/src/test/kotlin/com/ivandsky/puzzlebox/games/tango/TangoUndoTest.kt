package com.ivandsky.puzzlebox.games.tango

import com.ivandsky.puzzlebox.games.tango.engine.TangoMove
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Command-pattern undo/redo over [TangoSession]: one [TangoPlace] per placement, board state only. */
class TangoUndoTest {

    private val now: Instant = Instant.ofEpochMilli(1_746_192_000_000)

    private val fresh = TangoSession(ref = null, puzzle = TangoTestSupport.sample)

    private fun TangoSession.place(idx: Int, value: TangoCell?) = withMove(TangoMove(idx, value), now)

    // ---- the command itself ----

    @Test
    fun `place reverts to the exact previous state for set, change and clear`() {
        val board = mapOf(2 to TangoCell.MOON, 4 to TangoCell.SUN)
        val cases = listOf(
            TangoPlace(idx = 3, value = TangoCell.SUN, previous = null), // set on an empty cell
            TangoPlace(idx = 2, value = TangoCell.SUN, previous = TangoCell.MOON), // change
            TangoPlace(idx = 4, value = null, previous = TangoCell.SUN), // clear
        )
        for (cmd in cases) {
            for (state in listOf(emptyMap(), board)) {
                // Commands are built against states holding `previous`; skip states that do not.
                if (state[cmd.idx] != cmd.previous) continue
                assertEquals(state, cmd.revert(cmd.apply(state)))
            }
        }
    }

    @Test
    fun `place touches only its own cell`() {
        val board = mapOf(2 to TangoCell.MOON, 4 to TangoCell.SUN)

        assertEquals(board + (3 to TangoCell.SUN), TangoPlace(3, TangoCell.SUN, null).apply(board))
        assertEquals(mapOf(2 to TangoCell.MOON), TangoPlace(4, null, TangoCell.SUN).apply(board))
    }

    // ---- session API ----

    @Test
    fun `undo and redo walk set, change and clear back and forth`() {
        val set = fresh.place(2, TangoCell.MOON)
        val changed = set.place(2, TangoCell.SUN)
        val cleared = changed.place(2, null)
        assertEquals(emptyMap<Int, TangoCell>(), cleared.placements)

        val u1 = cleared.withUndo()
        assertEquals(mapOf(2 to TangoCell.SUN), u1.placements)
        val u2 = u1.withUndo()
        assertEquals(mapOf(2 to TangoCell.MOON), u2.placements)
        val u3 = u2.withUndo()
        assertEquals(emptyMap<Int, TangoCell>(), u3.placements)
        assertFalse(u3.history.canUndo)
        assertEquals(u3, u3.withUndo()) // nothing left: identity

        val r3 = u3.withRedo().withRedo().withRedo()
        assertEquals(cleared, r3)
        assertEquals(r3, r3.withRedo())
    }

    @Test
    fun `a no-op placement records nothing and keeps redo`() {
        val s = fresh.place(2, TangoCell.MOON).place(4, TangoCell.MOON).withUndo()
        assertTrue(s.history.canRedo)

        val same = s.place(2, TangoCell.MOON)

        assertEquals(s, same)
        assertTrue(same.history.canRedo)
        assertEquals(1, same.history.done.size)
        assertEquals(fresh, fresh.place(2, null)) // clearing an empty cell: nothing to record
    }

    @Test
    fun `a new placement forks the timeline and drops redo`() {
        val undone = fresh.place(2, TangoCell.MOON).place(4, TangoCell.MOON).withUndo()
        assertTrue(undone.history.canRedo)

        val forked = undone.place(3, TangoCell.SUN)

        assertFalse(forked.history.canRedo)
        assertEquals(
            listOf(TangoPlace(2, TangoCell.MOON, null), TangoPlace(3, TangoCell.SUN, null)),
            forked.history.done,
        )
        assertEquals(forked, forked.withRedo())
    }

    @Test
    fun `undo and redo never touch counters or the clock`() {
        val played = fresh.copy(hintsUsed = 2, mistakeCount = 1, elapsedMs = 5_000)
            .place(2, TangoCell.MOON)
            .place(4, TangoCell.MOON)

        for (moved in listOf(played.withUndo(), played.withUndo().withRedo(), played.withUndo().withUndo())) {
            assertEquals(2, moved.hintsUsed)
            assertEquals(1, moved.mistakeCount)
            assertEquals(5_000L, moved.elapsedMs)
            assertNull(moved.completedAt)
        }
    }

    @Test
    fun `undo and redo are refused once the session is complete`() {
        var s = fresh
        TangoTestSupport.solution.forEachIndexed { idx, cell ->
            if (idx !in TangoTestSupport.sample.givens) s = s.place(idx, cell)
        }
        assertTrue(s.isComplete)

        assertEquals(s, s.withUndo())
        assertEquals(s, s.withRedo())
        assertEquals(now, s.withUndo().completedAt)
    }

    // ---- Clear: every player placement in one undo unit ----

    @Test
    fun `clear wipes every placement, keeps the givens and undoes as one step`() {
        val played = fresh.place(2, TangoCell.MOON).place(4, TangoCell.MOON).place(5, TangoCell.SUN)

        val cleared = played.withCleared()

        assertEquals(emptyMap<Int, TangoCell>(), cleared.placements)
        assertEquals(TangoTestSupport.sample.givens, cleared.grid().withIndex().filter { it.value != null }.associate { it.index to it.value })
        assertEquals(played.history.done.size + 1, cleared.history.done.size)

        val restored = cleared.withUndo()
        assertEquals(played.placements, restored.placements)
        assertEquals(played.history.done, restored.history.done) // the Clear moved to redo
        assertEquals(cleared, restored.withRedo())
    }

    @Test
    fun `clear on an empty board records nothing and keeps redo`() {
        assertEquals(fresh, fresh.withCleared())
        val undone = fresh.place(2, TangoCell.MOON).withUndo()
        assertEquals(undone, undone.withCleared())
        assertTrue(undone.history.canRedo)
        assertFalse(fresh.withCleared().history.canUndo)
    }

    @Test
    fun `clear after a placement forks the timeline and drops redo`() {
        val undone = fresh.place(2, TangoCell.MOON).place(4, TangoCell.MOON).withUndo()

        val cleared = undone.withCleared()

        assertFalse(cleared.history.canRedo)
        assertEquals(emptyMap<Int, TangoCell>(), cleared.placements)
    }

    @Test
    fun `clear is refused once the session is complete`() {
        var s = fresh
        TangoTestSupport.solution.forEachIndexed { idx, cell ->
            if (idx !in TangoTestSupport.sample.givens) s = s.place(idx, cell)
        }
        assertTrue(s.isComplete)

        assertEquals(s, s.withCleared())
    }

    @Test
    fun `clear never touches counters or the clock`() {
        val played = fresh.copy(hintsUsed = 2, mistakeCount = 1, elapsedMs = 5_000).place(2, TangoCell.MOON)

        val cleared = played.withCleared()

        assertEquals(2, cleared.hintsUsed)
        assertEquals(1, cleared.mistakeCount)
        assertEquals(5_000L, cleared.elapsedMs)
    }

    @Test
    fun `history takes part in equality`() {
        val a = fresh.place(2, TangoCell.MOON)
        val sameBoardOtherPath = fresh.place(2, TangoCell.SUN).place(2, TangoCell.MOON)

        assertEquals(a.placements, sameBoardOtherPath.placements)
        assertFalse(a == sameBoardOtherPath)
        assertEquals(a, fresh.place(2, TangoCell.MOON))
        assertEquals(a.hashCode(), fresh.place(2, TangoCell.MOON).hashCode())
    }
}
