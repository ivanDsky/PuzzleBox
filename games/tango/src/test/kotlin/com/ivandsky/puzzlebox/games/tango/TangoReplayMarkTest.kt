package com.ivandsky.puzzlebox.games.tango

import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.replay.MoveKind
import com.ivandsky.puzzlebox.game.replay.ReplayCodec
import com.ivandsky.puzzlebox.game.replay.ReplayOutcome
import com.ivandsky.puzzlebox.game.replay.ReplayRecord
import com.ivandsky.puzzlebox.game.replay.StampedMove
import com.ivandsky.puzzlebox.game.replay.Timeline
import com.ivandsky.puzzlebox.games.tango.TangoReplayMove.Clear
import com.ivandsky.puzzlebox.games.tango.TangoReplayMove.Hint
import com.ivandsky.puzzlebox.games.tango.TangoReplayMove.Place
import com.ivandsky.puzzlebox.games.tango.TangoReplayMove.Redo
import com.ivandsky.puzzlebox.games.tango.TangoReplayMove.Rejection
import com.ivandsky.puzzlebox.games.tango.TangoReplayMove.Undo
import com.ivandsky.puzzlebox.games.tango.engine.TangoBoundedSolution
import com.ivandsky.puzzlebox.games.tango.engine.step.TangoStep
import com.ivandsky.puzzlebox.games.tango.engine.step.TangoStepper
import com.ivandsky.puzzlebox.model.SolveReplay
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Tango marks, hint targets, transient mistakes and the tap merge, read off a real [Timeline]
 * opened through [TangoReplay]: what the timeline strip and the replay board draw.
 *
 * On [TangoTestSupport.sample] row 0 starts with two given suns, so a sun on cell 2 is a
 * three-in-a-row mistake and the hint on an empty board proves cell 2 is a moon.
 */
class TangoReplayMarkTest {

    private val puzzle = TangoTestSupport.sample
    private val initial = TangoSession(ref = null, puzzle = puzzle)

    private fun at(atMs: Long, move: TangoReplayMove) = StampedMove(atMs, move.toReplayMove())

    private fun timeline(vararg moves: StampedMove, endMs: Long = 9_000): Timeline {
        val stored = checkNotNull(ReplayCodec.encode(ReplayRecord(TangoDefinition.id, ReplayOutcome.GaveUp, endMs, moves.toList())))
        return checkNotNull(Timeline.open(TangoDefinition.id, TangoReplay, initial, checkNotNull(SolveReplay.of(stored))))
    }

    private fun Timeline.session(tMs: Long) = at(tMs).session as TangoSession

    private fun Timeline.assertKinds(vararg kinds: MoveKind) = assertArrayEquals(kinds, moveKindsAt)

    private fun Timeline.assertTicks(vararg times: Long) = assertArrayEquals(times, moveTimesMs)

    // ------------------------------------------------------------------ marks (F4, F5, F6)

    @Test
    fun `a placement is a plain move with no hinted cell`() {
        val timeline = timeline(at(1_000, Place(8, TangoCell.MOON)))

        timeline.assertKinds(MoveKind.Normal)
        assertEquals(emptySet<Int>(), timeline.at(1_000).hinted)
    }

    @Test
    fun `undo, redo and clear are Undo moves`() {
        val timeline = timeline(
            at(1_000, Place(8, TangoCell.MOON)),
            at(2_000, Undo),
            at(3_000, Redo),
            at(4_000, Clear),
        )

        timeline.assertKinds(MoveKind.Normal, MoveKind.Undo, MoveKind.Undo, MoveKind.Undo)
    }

    @Test
    fun `a rejection is a Mistake move`() {
        val timeline = timeline(at(1_000, Place(2, TangoCell.SUN)), at(1_000, Rejection), at(4_000, Place(8, TangoCell.MOON)))

        timeline.assertKinds(MoveKind.Mistake, MoveKind.Normal)
    }

    @Test
    fun `a hint is a Hint move and the strongest kind of its stamp`() {
        val timeline = timeline(at(1_000, Place(8, TangoCell.MOON)), at(2_000, Hint))

        timeline.assertKinds(MoveKind.Normal, MoveKind.Hint)
        assertEquals(1, timeline.at(2_000).facts.hints)
    }

    // ------------------------------------------------------------------ hint targets (F9)

    @Test
    fun `a hint on a board with a proof targets the proven cell`() {
        val timeline = timeline(at(1_000, Hint))

        assertEquals(setOf(2), timeline.at(1_000).hinted)
        assertEquals(emptySet<Int>(), timeline.at(999).hinted)
    }

    @Test
    fun `a hint on a board no rule solves targets the cell the reveal names`() {
        // The stepper's reveal board (TangoStepperTest d9): no rung fires, the what-if closure proves cell 1.
        val revealPuzzle = TangoPuzzle(
            VariantId("tango/n6"), 6,
            "......S................M............".withIndex()
                .mapNotNull { (i, c) -> if (c == 'S') i to TangoCell.SUN else if (c == 'M') i to TangoCell.MOON else null }
                .toMap(),
            "1=7 5x11 9x15 10x16 13x19 21x27 22=28 25x26 26=27 28x34".split(' ').map { sign ->
                val (a, k, b) = Regex("""(\d+)([=x])(\d+)""").matchEntire(sign)!!.destructured
                TangoMarker(
                    a.toInt(),
                    if (b.toInt() == a.toInt() + 1) TangoEdgeSide.RIGHT else TangoEdgeSide.DOWN,
                    if (k == "=") TangoMarkerKind.SAME else TangoMarkerKind.OPPOSITE,
                )
            },
        )
        val start = TangoSession(ref = null, puzzle = revealPuzzle)
        val stored = checkNotNull(ReplayCodec.encode(ReplayRecord(TangoDefinition.id, ReplayOutcome.GaveUp, 9_000, listOf(at(1_000, Hint)))))
        val timeline = checkNotNull(Timeline.open(TangoDefinition.id, TangoReplay, start, checkNotNull(SolveReplay.of(stored))))

        assertEquals(TangoStep.Reveal(1, TangoCell.MOON), TangoStepper.next(revealPuzzle, start.grid(), null, TangoBoundedSolution.of(revealPuzzle)))
        assertEquals(setOf(1), timeline.at(1_000).hinted)
    }

    @Test
    fun `a hint after a rule break targets the wrong entry`() {
        val timeline = timeline(at(1_000, Place(2, TangoCell.SUN)), at(1_000, Rejection), at(3_000, Hint))

        assertEquals(setOf(2), timeline.at(3_000).hinted)
        assertEquals(emptySet<Int>(), timeline.at(2_999).hinted)
    }

    @Test
    fun `the hinted cell is the one the live stepper names on the board before the hint`() {
        val timeline = timeline(at(1_000, Place(8, TangoCell.MOON)), at(2_000, Hint))
        val before = timeline.session(1_999)

        val step = TangoStepper.next(puzzle, before.grid(), 8, TangoBoundedSolution.of(puzzle))

        assertEquals(setOf((step as TangoStep.Deduction).idx), timeline.at(2_000).hinted)
    }

    // ------------------------------------------------------------------ transient mistake (F11)

    @Test
    fun `a mistake undone within the settle window was never shown, so it is no Mistake`() {
        val timeline = timeline(
            at(1_000, Place(2, TangoCell.SUN)),
            at(1_000, Rejection),
            at(1_499, Undo),
        )

        timeline.assertTicks(1_000, 1_499)
        timeline.assertKinds(MoveKind.Normal, MoveKind.Undo)
    }

    @Test
    fun `a mistake undone exactly at the settle window stays a Mistake`() {
        val timeline = timeline(
            at(1_000, Place(2, TangoCell.SUN)),
            at(1_000, Rejection),
            at(1_500, Undo),
        )

        timeline.assertKinds(MoveKind.Mistake, MoveKind.Undo)
    }

    @Test
    fun `a mistake whose violation is still there at the next move stays a Mistake`() {
        val timeline = timeline(
            at(1_000, Place(2, TangoCell.SUN)),
            at(1_000, Rejection),
            at(1_300, Place(8, TangoCell.MOON)),
        )

        timeline.assertKinds(MoveKind.Mistake, MoveKind.Normal)
    }

    // ------------------------------------------------------------------ tap merge (F12)

    @Test
    fun `a double tap inside the settle window is one tick at the later stamp`() {
        val timeline = timeline(at(1_000, Place(2, TangoCell.SUN)), at(1_300, Place(2, TangoCell.MOON)))

        timeline.assertTicks(1_300)
        timeline.assertKinds(MoveKind.Normal)
        assertSame(initial, timeline.at(1_299).session)
        assertEquals(TangoCell.MOON, timeline.session(1_300).placements[2])
    }

    @Test
    fun `a rejection between the two taps of a cell still merges, and the shown violation never existed`() {
        val timeline = timeline(
            at(1_000, Place(2, TangoCell.SUN)),
            at(1_000, Rejection),
            at(1_300, Place(2, TangoCell.MOON)),
        )

        timeline.assertTicks(1_300)
        timeline.assertKinds(MoveKind.Normal)
        assertSame(initial, timeline.at(1_299).session)
    }

    @Test
    fun `a three-tap cycle on one cell is one tick at the last stamp`() {
        val timeline = timeline(
            at(1_000, Place(2, TangoCell.SUN)),
            at(1_300, Place(2, TangoCell.MOON)),
            at(1_600, Place(2, null)),
        )

        timeline.assertTicks(1_600)
        assertSame(initial, timeline.at(1_599).session)
        assertEquals(null, timeline.session(1_600).placements[2])
    }

    @Test
    fun `two taps a settle window apart are two ticks`() {
        val timeline = timeline(at(1_000, Place(2, TangoCell.SUN)), at(1_500, Place(2, TangoCell.MOON)))

        timeline.assertTicks(1_000, 1_500)
        assertEquals(TangoCell.SUN, timeline.session(1_000).placements[2])
    }

    @Test
    fun `taps on two cells never merge`() {
        val timeline = timeline(at(1_000, Place(8, TangoCell.MOON)), at(1_300, Place(9, TangoCell.MOON)))

        timeline.assertTicks(1_000, 1_300)
    }
}
