package com.ivandsky.puzzlebox.games.tango

import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.replay.GameReplay
import com.ivandsky.puzzlebox.game.replay.ReplayCodec
import com.ivandsky.puzzlebox.game.replay.ReplayOutcome
import com.ivandsky.puzzlebox.game.replay.ReplayRecord
import com.ivandsky.puzzlebox.game.replay.StampedMove
import com.ivandsky.puzzlebox.game.replay.Timeline
import com.ivandsky.puzzlebox.games.tango.TangoReplayMove.Hint
import com.ivandsky.puzzlebox.games.tango.TangoReplayMove.Place
import com.ivandsky.puzzlebox.games.tango.engine.TangoBoundedSolution
import com.ivandsky.puzzlebox.games.tango.engine.step.TangoStep
import com.ivandsky.puzzlebox.games.tango.engine.step.TangoStepper
import com.ivandsky.puzzlebox.model.SolveReplay
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A recorded hint press is marked with the cell the live stepper chooses. On the board below row 0 `SS....`
 * shows a pair at a glance (cell 2) while row 3 `SMSMS.` forces a moon at 23 only to a player who counts the line,
 * so the first-move rule points at 23.
 */
class TangoReplayFirstMoveTest {

    private val bare = TangoPuzzle(VariantId("tango/n6"), 6, emptyMap(), emptyList())
    private val initial = TangoSession(ref = null, puzzle = bare)

    private val entries = listOf(
        Place(0, TangoCell.SUN), Place(1, TangoCell.SUN),
        Place(18, TangoCell.SUN), Place(19, TangoCell.MOON), Place(20, TangoCell.SUN), Place(21, TangoCell.MOON), Place(22, TangoCell.SUN),
    )

    private fun played(vararg moves: TangoReplayMove): TangoSession =
        moves.foldIndexed(initial) { i, session, move -> checkNotNull(TangoReplay.step(session, move.toReplayMove(), 1_000L * (i + 1))) }

    /** The cell a live press on [session] points at as TangoStepGame asks the stepper. */
    private fun live(session: TangoSession): Set<Int> {
        val lastMove = (session.history.done.lastOrNull() as? TangoPlace)?.idx
        return when (val step = TangoStepper.next(bare, session.grid(), lastMove, TangoBoundedSolution.of(bare))) {
            is TangoStep.Deduction -> setOf(step.idx)
            is TangoStep.Reveal -> setOf(step.idx)
            is TangoStep.Mistake -> setOf(step.idx)
            TangoStep.Solved, TangoStep.Unavailable -> emptySet()
        }
    }

    private fun hinted(replay: GameReplay<TangoSession> = TangoReplay, moves: List<TangoReplayMove> = entries): Set<Int> {
        val stamped = (moves + Hint).mapIndexed { i, move -> StampedMove(1_000L * (i + 1), move.toReplayMove()) }
        val stored = checkNotNull(ReplayCodec.encode(ReplayRecord(TangoDefinition.id, ReplayOutcome.GaveUp, 20_000, stamped)))
        val timeline = checkNotNull(Timeline.open(TangoDefinition.id, replay, initial, checkNotNull(SolveReplay.of(stored))))
        return timeline.at(1_000L * stamped.size).hinted
    }

    @Test
    fun `the board hints the cell a player has to count to, not the pair at a glance`() {
        assertEquals(setOf(23), live(played(*entries.toTypedArray())))
    }

    @Test
    fun `a recorded hint marks the cell the first-move stepper chose`() {
        assertEquals(live(played(*entries.toTypedArray())), hinted())
    }

    @Test
    fun `the plugin carries the replay that marks the first-move cell`() {
        assertEquals(setOf(23), hinted(checkNotNull(tangoPlugin.replay)))
    }
}
