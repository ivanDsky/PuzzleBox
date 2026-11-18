package com.ivandsky.puzzlebox.games.queens

import com.ivandsky.puzzlebox.game.replay.GameReplay
import com.ivandsky.puzzlebox.game.replay.ReplayCodec
import com.ivandsky.puzzlebox.game.replay.ReplayOutcome
import com.ivandsky.puzzlebox.game.replay.ReplayRecord
import com.ivandsky.puzzlebox.game.replay.StampedMove
import com.ivandsky.puzzlebox.game.replay.Timeline
import com.ivandsky.puzzlebox.games.queens.QueensReplayMove.Hint
import com.ivandsky.puzzlebox.games.queens.QueensReplayMove.Place
import com.ivandsky.puzzlebox.games.queens.engine.hints.step.QueensStep
import com.ivandsky.puzzlebox.model.SolveReplay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * A recorded hint press is marked with the cells the live stepper chooses. The 8×8 play fixture of queens-ui opens on
 * two one-cell regions, which a first move passes over for a row the player has to work out.
 */
class QueensReplayFirstMoveTest {

    private val play = QueensPuzzle(
        size = 8,
        regions = listOf(
            0, 4, 1, 1, 1, 1, 2, 2,
            4, 4, 1, 2, 1, 1, 2, 2,
            4, 4, 2, 2, 2, 2, 2, 2,
            4, 4, 4, 4, 4, 3, 4, 4,
            5, 5, 4, 5, 4, 4, 4, 5,
            6, 5, 5, 5, 5, 5, 5, 5,
            6, 6, 6, 6, 6, 6, 6, 6,
            6, 6, 7, 7, 7, 7, 7, 7,
        ),
        queensPerUnit = 1,
        solution = listOf(0, 12, 23, 29, 34, 46, 49, 59).map(::Cell),
    )
    private val initial = QueensSession(ref = null, puzzle = play)

    /** The cells a live press on [session] points at, as the stepper names them. */
    private fun live(session: QueensSession): Set<Int> =
        when (val step = QueensSessionStep.next(session, QueensSessionStep.boundedSolution(session.puzzle))) {
            is QueensStep.Deduction -> when (val result = step.result) {
                is QueensStep.Deduction.Result.Cross -> result.cells.mapTo(HashSet()) { it.idx }
                is QueensStep.Deduction.Result.LastCell -> setOf(result.cell.idx)
            }
            is QueensStep.Mistake -> setOf(step.cell.idx)
            is QueensStep.Reveal -> setOf(step.cell.idx)
            QueensStep.Solved, QueensStep.Unavailable -> emptySet()
        }

    private fun hinted(replay: GameReplay<QueensSession>, vararg before: QueensReplayMove): Set<Int> {
        val moves = (before.toList() + Hint).mapIndexed { i, move -> StampedMove(1_000L * (i + 1), move.toReplayMove()) }
        val stored = checkNotNull(ReplayCodec.encode(ReplayRecord(QueensDefinition.id, ReplayOutcome.GaveUp, 9_000, moves)))
        val timeline = checkNotNull(Timeline.open(QueensDefinition.id, replay, initial, checkNotNull(SolveReplay.of(stored))))
        return timeline.at(1_000L * moves.size).hinted
    }

    @Test
    fun `a recorded hint marks the cells the live stepper chose`() {
        assertNotEquals(setOf(0), live(initial))
        assertEquals(live(initial), hinted(QueensReplay))
    }

    @Test
    fun `the replay the plugin carries marks the same cells`() {
        assertEquals(live(initial), hinted(checkNotNull(queensPlugin.replay)))
    }

    @Test
    fun `a mid-play hint is asked from the board before it`() {
        val moves = arrayOf<QueensReplayMove>(Place(Cell(0)))
        val mid = checkNotNull(QueensReplay.step(initial, moves[0].toReplayMove(), 1_000))

        assertEquals(live(mid), hinted(QueensReplay, *moves))
    }
}
