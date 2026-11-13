package com.ivandsky.puzzlebox.games.queens

import com.ivandsky.puzzlebox.game.replay.MoveKind
import com.ivandsky.puzzlebox.game.replay.ReplayCodec
import com.ivandsky.puzzlebox.game.replay.ReplayOutcome
import com.ivandsky.puzzlebox.game.replay.ReplayRecord
import com.ivandsky.puzzlebox.game.replay.StampedMove
import com.ivandsky.puzzlebox.game.replay.Timeline
import com.ivandsky.puzzlebox.games.queens.QueensReplayMove.Clear
import com.ivandsky.puzzlebox.games.queens.QueensReplayMove.Cross
import com.ivandsky.puzzlebox.games.queens.QueensReplayMove.Hint
import com.ivandsky.puzzlebox.games.queens.QueensReplayMove.Place
import com.ivandsky.puzzlebox.games.queens.QueensReplayMove.Redo
import com.ivandsky.puzzlebox.games.queens.QueensReplayMove.Remove
import com.ivandsky.puzzlebox.games.queens.QueensReplayMove.Undo
import com.ivandsky.puzzlebox.model.SolveReplay
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Queens marks, hint targets, transient mistakes and the tap merge, read off a real [Timeline]
 * opened through [QueensReplay]: what the timeline strip and the replay board draw.
 *
 * [QueensTestSupport.n4Puzzle] is 4×4 with its regions the rows and the solution on cells 1, 7, 8, 14,
 * so two queens in row 0 break a rule and the hint on an empty board is the last cell, 14.
 */
class QueensReplayMarkTest {

    private val puzzle = QueensTestSupport.n4Puzzle()
    private val initial = QueensSession(ref = null, puzzle = puzzle)

    private fun at(atMs: Long, move: QueensReplayMove) = StampedMove(atMs, move.toReplayMove())

    private fun timeline(vararg moves: StampedMove, from: QueensSession = initial, endMs: Long = 9_000): Timeline {
        val stored = checkNotNull(ReplayCodec.encode(ReplayRecord(QueensDefinition.id, ReplayOutcome.GaveUp, endMs, moves.toList())))
        return checkNotNull(Timeline.open(QueensDefinition.id, QueensReplay, from, checkNotNull(SolveReplay.of(stored))))
    }

    private fun Timeline.session(tMs: Long) = at(tMs).session as QueensSession

    private fun Timeline.assertKinds(vararg kinds: MoveKind) = assertArrayEquals(kinds, moveKindsAt)

    private fun Timeline.assertTicks(vararg times: Long) = assertArrayEquals(times, moveTimesMs)

    private fun cells(timeline: Timeline, tMs: Long) = timeline.session(tMs).queens.map { it.idx }

    // ------------------------------------------------------------------ marks (F4, F5, F6)

    @Test
    fun `a placement that breaks no rule is plain`() {
        val timeline = timeline(at(1_000, Place(Cell(1))), at(2_000, Cross(Cell(5))))

        timeline.assertKinds(MoveKind.Normal, MoveKind.Normal)
    }

    @Test
    fun `a placement that adds a rule violation is a Mistake`() {
        val timeline = timeline(at(1_000, Place(Cell(1))), at(2_000, Place(Cell(2))), at(4_000, Place(Cell(8))))

        timeline.assertKinds(MoveKind.Normal, MoveKind.Mistake, MoveKind.Normal)
    }

    @Test
    fun `undo, redo and clear are Undo moves`() {
        val timeline = timeline(
            at(1_000, Place(Cell(1))),
            at(2_000, Undo),
            at(3_000, Redo),
            at(4_000, Clear),
        )

        timeline.assertKinds(MoveKind.Normal, MoveKind.Undo, MoveKind.Undo, MoveKind.Undo)
    }

    @Test
    fun `a hint is a Hint move and counts`() {
        val timeline = timeline(at(1_000, Place(Cell(1))), at(2_000, Hint))

        timeline.assertKinds(MoveKind.Normal, MoveKind.Hint)
        assertEquals(1, timeline.at(2_000).facts.hints)
    }

    // ------------------------------------------------------------------ hint targets (F9)

    @Test
    fun `a last-cell deduction targets the cell it fills`() {
        val timeline = timeline(at(1_000, Hint))

        assertEquals(setOf(14), timeline.at(1_000).hinted)
        assertEquals(emptySet<Int>(), timeline.at(999).hinted)
    }

    @Test
    fun `a cross deduction targets every cell it crosses`() {
        val eight = QueensSession(ref = null, puzzle = QueensTestSupport.n8N2Puzzle())

        val timeline = timeline(at(1_000, Hint), from = eight)

        assertEquals(setOf(40, 41), timeline.at(1_000).hinted)
    }

    @Test
    fun `an off-solution queen is the Mistake target`() {
        val timeline = timeline(at(1_000, Place(Cell(0))), at(3_000, Hint))

        assertEquals(setOf(0), timeline.at(3_000).hinted)
    }

    @Test
    fun `a rule break names the cell that was placed last`() {
        val timeline = timeline(at(1_000, Place(Cell(1))), at(2_000, Place(Cell(2))), at(4_000, Hint))

        assertEquals(setOf(2), timeline.at(4_000).hinted)
    }

    @Test
    fun `a wrong cross is the Mistake target`() {
        val timeline = timeline(at(1_000, Cross(Cell(7))), at(3_000, Hint))

        assertEquals(setOf(7), timeline.at(3_000).hinted)
    }

    // ------------------------------------------------------------------ transient mistake (F11)

    @Test
    fun `a mistake undone within the settle window was never shown, so it is no Mistake`() {
        val timeline = timeline(at(1_000, Place(Cell(1))), at(2_000, Place(Cell(2))), at(2_499, Undo))

        timeline.assertTicks(1_000, 2_000, 2_499)
        timeline.assertKinds(MoveKind.Normal, MoveKind.Normal, MoveKind.Undo)
    }

    @Test
    fun `a mistake removed by taking the other queen off within the settle window is no Mistake`() {
        val timeline = timeline(at(1_000, Place(Cell(1))), at(2_000, Place(Cell(2))), at(2_300, Remove(Cell(1))))

        timeline.assertKinds(MoveKind.Normal, MoveKind.Normal, MoveKind.Normal)
    }

    @Test
    fun `a mistake undone exactly at the settle window stays a Mistake`() {
        val timeline = timeline(at(1_000, Place(Cell(1))), at(2_000, Place(Cell(2))), at(2_500, Undo))

        timeline.assertKinds(MoveKind.Normal, MoveKind.Mistake, MoveKind.Undo)
    }

    @Test
    fun `a mistake whose violation is still there at the next move stays a Mistake`() {
        val timeline = timeline(at(1_000, Place(Cell(1))), at(2_000, Place(Cell(2))), at(2_300, Place(Cell(8))))

        timeline.assertKinds(MoveKind.Normal, MoveKind.Mistake, MoveKind.Normal)
    }

    @Test
    fun `a step that introduced no violation is never transient, as in Tango`() {
        val now = java.time.Instant.EPOCH
        val before = initial.withQueenPlaced(Cell(1), now)
        val after = before.withCrossToggled(Cell(5), now)

        assertEquals(false, QueensReplay.isTransientMistake(before, after, before))
    }

    // ------------------------------------------------------------------ tap merge (F12)

    @Test
    fun `cross then queen on one cell inside the settle window is one tick at the later stamp`() {
        val timeline = timeline(at(1_000, Cross(Cell(5))), at(1_300, Place(Cell(5))))

        timeline.assertTicks(1_300)
        assertSame(initial, timeline.at(1_299).session)
        assertEquals(listOf(5), cells(timeline, 1_300))
    }

    @Test
    fun `queen then remove on one cell inside the settle window is one tick at the later stamp`() {
        val timeline = timeline(at(1_000, Place(Cell(5))), at(1_300, Remove(Cell(5))))

        timeline.assertTicks(1_300)
        assertSame(initial, timeline.at(1_299).session)
        assertEquals(emptyList<Int>(), cells(timeline, 1_300))
    }

    @Test
    fun `a full cross, queen, empty cycle is one tick at the last stamp`() {
        val timeline = timeline(at(1_000, Cross(Cell(5))), at(1_300, Place(Cell(5))), at(1_600, Remove(Cell(5))))

        timeline.assertTicks(1_600)
        assertSame(initial, timeline.at(1_599).session)
    }

    @Test
    fun `a mistake queen the player removes in the same tap is merged and never a Mistake`() {
        val timeline = timeline(at(1_000, Place(Cell(1))), at(2_000, Place(Cell(2))), at(2_300, Remove(Cell(2))))

        timeline.assertTicks(1_000, 2_300)
        assertEquals(listOf(1), cells(timeline, 2_299))
        timeline.assertKinds(MoveKind.Normal, MoveKind.Normal)
    }

    @Test
    fun `the same two taps a settle window apart are two ticks`() {
        val timeline = timeline(at(1_000, Cross(Cell(5))), at(1_500, Place(Cell(5))))

        timeline.assertTicks(1_000, 1_500)
        assertEquals(emptyList<Int>(), cells(timeline, 1_000))
    }

    @Test
    fun `taps on two cells never merge`() {
        val timeline = timeline(at(1_000, Cross(Cell(5))), at(1_300, Place(Cell(6))))

        timeline.assertTicks(1_000, 1_300)
    }
}
