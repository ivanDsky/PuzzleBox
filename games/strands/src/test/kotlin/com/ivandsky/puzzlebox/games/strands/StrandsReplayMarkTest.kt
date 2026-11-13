package com.ivandsky.puzzlebox.games.strands

import com.ivandsky.puzzlebox.game.replay.MoveKind
import com.ivandsky.puzzlebox.game.replay.ReplayCodec
import com.ivandsky.puzzlebox.game.replay.ReplayOutcome
import com.ivandsky.puzzlebox.game.replay.ReplayRecord
import com.ivandsky.puzzlebox.game.replay.StampedMove
import com.ivandsky.puzzlebox.game.replay.Timeline
import com.ivandsky.puzzlebox.model.SolveReplay
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Strands marks and hint targets, read off a real [Timeline] opened through [StrandsReplay]. A hint
 * marks every cell of the word it names, packed `row * cols + col`.
 */
class StrandsReplayMarkTest {

    private val pies = StrandsWord("PIES", (0..3).map { Coord(0, it) })
    private val bake = StrandsWord("BAKE", (0..3).map { Coord(1, it) })

    /** 2 × 4: theme PIES on the top row, spangram BAKE on the bottom. */
    private val puzzle = StrandsPuzzle(
        rows = 2,
        cols = 4,
        letters = "PIESBAKE",
        clue = "Bake sale",
        spangram = bake,
        themeWords = listOf(pies),
        validWords = setOf("PIES", "BAKE", "PIKE"),
        constructors = "Test Setter",
    )
    private val initial = StrandsSession(ref = null, state = StrandsState(puzzle), elapsedMs = 7L)

    private fun at(atMs: Long, move: StrandsReplayMove) = StampedMove(atMs, move.toReplayMove(puzzle.cols))

    private fun timeline(vararg moves: StampedMove, endMs: Long = 9_000): Timeline {
        val stored = checkNotNull(ReplayCodec.encode(ReplayRecord(StrandsDefinition.id, ReplayOutcome.GaveUp, endMs, moves.toList())))
        return checkNotNull(Timeline.open(StrandsDefinition.id, StrandsReplay, initial, checkNotNull(SolveReplay.of(stored))))
    }

    private fun Timeline.assertKinds(vararg kinds: MoveKind) = assertArrayEquals(kinds, moveKindsAt)

    @Test
    fun `a submitted word is a plain move with no hinted cell`() {
        val timeline = timeline(at(1_000, StrandsReplayMove.Submit(pies.cells)))

        timeline.assertKinds(MoveKind.Normal)
        assertEquals(emptySet<Int>(), timeline.at(1_000).hinted)
    }

    @Test
    fun `a theme word hint is a Hint move that marks every cell of the word`() {
        val timeline = timeline(at(1_000, StrandsReplayMove.Hint(0, HintStage.Outlined)))

        timeline.assertKinds(MoveKind.Hint)
        assertEquals(setOf(0, 1, 2, 3), timeline.at(1_000).hinted)
        assertEquals(emptySet<Int>(), timeline.at(999).hinted)
        assertEquals(1, timeline.at(1_000).facts.hints)
    }

    @Test
    fun `a spangram hint marks the spangram's cells by row-major index`() {
        val timeline = timeline(at(1_000, StrandsReplayMove.Hint(1, HintStage.Outlined)))

        assertEquals(setOf(4, 5, 6, 7), timeline.at(1_000).hinted)
    }

    @Test
    fun `the order hint after the outline is a second Hint move on the same cells`() {
        val timeline = timeline(
            at(1_000, StrandsReplayMove.Hint(0, HintStage.Outlined)),
            at(2_000, StrandsReplayMove.Hint(0, HintStage.OrderRevealed)),
        )

        timeline.assertKinds(MoveKind.Hint, MoveKind.Hint)
        assertEquals(setOf(0, 1, 2, 3), timeline.at(2_000).hinted)
    }
}
