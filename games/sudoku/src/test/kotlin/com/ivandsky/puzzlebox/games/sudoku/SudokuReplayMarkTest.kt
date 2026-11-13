package com.ivandsky.puzzlebox.games.sudoku

import com.ivandsky.puzzlebox.game.replay.MoveKind
import com.ivandsky.puzzlebox.game.replay.ReplayCodec
import com.ivandsky.puzzlebox.game.replay.ReplayOutcome
import com.ivandsky.puzzlebox.game.replay.ReplayRecord
import com.ivandsky.puzzlebox.game.replay.StampedMove
import com.ivandsky.puzzlebox.game.replay.Timeline
import com.ivandsky.puzzlebox.games.sudoku.SudokuReplayMove.Clear
import com.ivandsky.puzzlebox.games.sudoku.SudokuReplayMove.Erase
import com.ivandsky.puzzlebox.games.sudoku.SudokuReplayMove.Hint
import com.ivandsky.puzzlebox.games.sudoku.SudokuReplayMove.HintReveal
import com.ivandsky.puzzlebox.games.sudoku.SudokuReplayMove.Place
import com.ivandsky.puzzlebox.games.sudoku.SudokuReplayMove.Redo
import com.ivandsky.puzzlebox.games.sudoku.SudokuReplayMove.ToggleNote
import com.ivandsky.puzzlebox.games.sudoku.SudokuReplayMove.Undo
import com.ivandsky.puzzlebox.model.SolveReplay
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/** Sudoku marks and hint targets, read off a real [Timeline] opened through [SudokuReplay]. */
class SudokuReplayMarkTest {

    private val puzzle = KnownSolutions.peanut450
    private val initial = SudokuSession(ref = null, puzzle = puzzle)

    /** Open cells of #450, ascending; the first two (0 and 1) are row peers. */
    private val open = puzzle.givens.indices.filter { puzzle.givens[it] == 0 }
    private val a = open[0]
    private val b = open[1]
    private fun right(idx: Int) = puzzle.solution[idx]

    private fun at(atMs: Long, move: SudokuReplayMove) = StampedMove(atMs, move.toReplayMove())

    private fun timeline(vararg moves: StampedMove, endMs: Long = 9_000): Timeline {
        val stored = checkNotNull(ReplayCodec.encode(ReplayRecord(SudokuGames.MINI.id, ReplayOutcome.GaveUp, endMs, moves.toList())))
        return checkNotNull(Timeline.open(SudokuGames.MINI.id, SudokuReplay, initial, checkNotNull(SolveReplay.of(stored))))
    }

    private fun Timeline.assertKinds(vararg kinds: MoveKind) = assertArrayEquals(kinds, moveKindsAt)

    @Test
    fun `a digit, a note and an erase are plain moves with no hinted cell`() {
        val timeline = timeline(
            at(1_000, Place(a, right(a))),
            at(2_000, ToggleNote(b, right(a))),
            at(3_000, Erase(a)),
        )

        timeline.assertKinds(MoveKind.Normal, MoveKind.Normal, MoveKind.Normal)
        assertEquals(emptySet<Int>(), timeline.at(3_000).hinted)
    }

    @Test
    fun `a digit that clashes with a peer is a Mistake`() {
        val timeline = timeline(at(1_000, Place(a, right(a))), at(2_000, Place(b, right(a))))

        timeline.assertKinds(MoveKind.Normal, MoveKind.Mistake)
    }

    @Test
    fun `a revealing hint is a Hint move that names the cell`() {
        val timeline = timeline(at(1_000, HintReveal(a, right(a))))

        timeline.assertKinds(MoveKind.Hint)
        assertEquals(setOf(a), timeline.at(1_000).hinted)
        assertEquals(emptySet<Int>(), timeline.at(999).hinted)
    }

    @Test
    fun `a hint that reveals no cell is a Hint move that names none`() {
        val timeline = timeline(at(1_000, Hint))

        timeline.assertKinds(MoveKind.Hint)
        assertEquals(emptySet<Int>(), timeline.at(1_000).hinted)
        assertEquals(1, timeline.at(1_000).facts.hints)
    }

    @Test
    fun `undo, redo and clear are Undo moves`() {
        val timeline = timeline(
            at(1_000, Place(a, right(a))),
            at(2_000, Undo),
            at(3_000, Redo),
            at(4_000, Clear),
        )

        timeline.assertKinds(MoveKind.Normal, MoveKind.Undo, MoveKind.Undo, MoveKind.Undo)
    }
}
