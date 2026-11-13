package com.ivandsky.puzzlebox.games.zip

import com.ivandsky.puzzlebox.game.replay.MoveKind
import com.ivandsky.puzzlebox.game.replay.ReplayCodec
import com.ivandsky.puzzlebox.game.replay.ReplayOutcome
import com.ivandsky.puzzlebox.game.replay.ReplayRecord
import com.ivandsky.puzzlebox.game.replay.StampedMove
import com.ivandsky.puzzlebox.game.replay.Timeline
import com.ivandsky.puzzlebox.games.zip.ZipReplayMove.Extend
import com.ivandsky.puzzlebox.games.zip.ZipReplayMove.Hint
import com.ivandsky.puzzlebox.games.zip.ZipReplayMove.Redo
import com.ivandsky.puzzlebox.games.zip.ZipReplayMove.Rejection
import com.ivandsky.puzzlebox.games.zip.ZipReplayMove.Reset
import com.ivandsky.puzzlebox.games.zip.ZipReplayMove.Undo
import com.ivandsky.puzzlebox.model.SolveReplay
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Zip marks and hint targets, read off a real [Timeline] opened through [ZipReplay]. A Zip hint is
 * recorded as [Hint] then the [Extend] it placed, so the hinted cell is the Extend that follows a Hint.
 */
class ZipReplayMarkTest {

    private val initial = ZipSession(ref = null, puzzle = ZipTestSupport.n5Puzzle())

    private fun at(atMs: Long, move: ZipReplayMove) = StampedMove(atMs, move.toReplayMove())

    private fun timeline(vararg moves: StampedMove, endMs: Long = 9_000): Timeline {
        val stored = checkNotNull(ReplayCodec.encode(ReplayRecord(ZipDefinition.id, ReplayOutcome.GaveUp, endMs, moves.toList())))
        return checkNotNull(Timeline.open(ZipDefinition.id, ZipReplay, initial, checkNotNull(SolveReplay.of(stored))))
    }

    private fun Timeline.assertKinds(vararg kinds: MoveKind) = assertArrayEquals(kinds, moveKindsAt)

    @Test
    fun `an extend is a plain move with no hinted cell`() {
        val timeline = timeline(at(1_000, Extend(Cell(0))), at(2_000, Extend(Cell(1))))

        timeline.assertKinds(MoveKind.Normal, MoveKind.Normal)
        assertEquals(emptySet<Int>(), timeline.at(2_000).hinted)
    }

    @Test
    fun `a hint alone is a Hint move that names no cell`() {
        val timeline = timeline(at(1_000, Extend(Cell(0))), at(2_000, Hint))

        timeline.assertKinds(MoveKind.Normal, MoveKind.Hint)
        assertEquals(emptySet<Int>(), timeline.at(2_000).hinted)
        assertEquals(1, timeline.at(2_000).facts.hints)
    }

    @Test
    fun `the extend that shares a stamp with its hint is the hinted cell`() {
        val timeline = timeline(at(1_000, Extend(Cell(0))), at(2_000, Hint), at(2_000, Extend(Cell(1))))

        timeline.assertKinds(MoveKind.Normal, MoveKind.Hint)
        assertEquals(setOf(1), timeline.at(2_000).hinted)
        assertEquals(emptySet<Int>(), timeline.at(1_999).hinted)
    }

    @Test
    fun `an extend with no hint before it names no cell`() {
        val timeline = timeline(at(1_000, Extend(Cell(0))), at(2_000, Extend(Cell(1))), at(3_000, Extend(Cell(2))))

        timeline.assertKinds(MoveKind.Normal, MoveKind.Normal, MoveKind.Normal)
        assertEquals(emptySet<Int>(), timeline.at(3_000).hinted)
    }

    @Test
    fun `a rejection is a Mistake move`() {
        val timeline = timeline(at(1_000, Extend(Cell(0))), at(2_000, Rejection))

        timeline.assertKinds(MoveKind.Normal, MoveKind.Mistake)
    }

    @Test
    fun `undo, redo and reset are Undo moves`() {
        val timeline = timeline(
            at(1_000, Extend(Cell(0))),
            at(2_000, Extend(Cell(1))),
            at(3_000, Undo),
            at(4_000, Redo),
            at(5_000, Reset),
        )

        timeline.assertKinds(MoveKind.Normal, MoveKind.Normal, MoveKind.Undo, MoveKind.Undo, MoveKind.Undo)
    }
}
