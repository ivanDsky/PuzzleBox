package com.ivandsky.puzzlebox.games.tango

import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.replay.GameReplay
import com.ivandsky.puzzlebox.game.replay.ReplayCodec
import com.ivandsky.puzzlebox.game.replay.ReplayFirstMove
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Ticket 47b: a recorded hint press is marked with the cell the live stepper chooses under the option the replay
 * is asked with. On the board below row 0 `SS....` shows a pair at a glance (cell 2) while row 3 `SMSMS.` forces
 * a moon at 23 only to a player who counts the line, so the two orders point at different cells.
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

    /** The cell a live press on [session] points at under [firstMove], as TangoStepGame asks the stepper. */
    private fun live(session: TangoSession, firstMove: Boolean): Set<Int> {
        val lastMove = (session.history.done.lastOrNull() as? TangoPlace)?.idx
        return when (val step = TangoStepper.next(bare, session.grid(), lastMove, TangoBoundedSolution.of(bare), firstMove)) {
            is TangoStep.Deduction -> setOf(step.idx)
            is TangoStep.Reveal -> setOf(step.idx)
            is TangoStep.Mistake -> setOf(step.idx)
            TangoStep.Solved, TangoStep.Unavailable -> emptySet()
        }
    }

    private fun hinted(replay: GameReplay<TangoSession>, moves: List<TangoReplayMove> = entries): Set<Int> {
        val stamped = (moves + Hint).mapIndexed { i, move -> StampedMove(1_000L * (i + 1), move.toReplayMove()) }
        val stored = checkNotNull(ReplayCodec.encode(ReplayRecord(TangoDefinition.id, ReplayOutcome.GaveUp, 20_000, stamped)))
        val timeline = checkNotNull(Timeline.open(TangoDefinition.id, replay, initial, checkNotNull(SolveReplay.of(stored))))
        return timeline.at(1_000L * stamped.size).hinted
    }

    @Test
    fun `the board tells the two orders apart`() {
        val board = played(*entries.toTypedArray())

        assertEquals(setOf(2), live(board, firstMove = false))
        assertEquals(setOf(23), live(board, firstMove = true))
    }

    @Test
    fun `with the option on a recorded hint marks the cell the first-move stepper chose`() {
        assertEquals(live(played(*entries.toTypedArray()), firstMove = true), hinted(TangoReplay { true }))
    }

    @Test
    fun `with the option off a recorded hint marks the legacy cell`() {
        assertEquals(setOf(2), hinted(TangoReplay { false }))
    }

    @Test
    fun `a replay with no option is the legacy replay`() {
        assertEquals(setOf(2), hinted(TangoReplay()))
        assertEquals(setOf(2), hinted(TangoReplay(ReplayFirstMove.Off)))
        assertSame(TangoReplay, tangoPluginFor(null).replay)
    }

    @Test
    fun `the option is asked at each mark, so a replay opened after a flip uses the new value`() {
        var on = false
        val replay = TangoReplay { on }

        assertEquals(setOf(2), hinted(replay))
        on = true
        assertEquals(setOf(23), hinted(replay))
        assertNotEquals(setOf(2), hinted(replay))
    }

    @Test
    fun `a bound option reaches the replay the plugin carries`() {
        assertEquals(setOf(23), hinted(checkNotNull(tangoPluginFor { true }.replay)))
        assertEquals(setOf(2), hinted(checkNotNull(tangoPluginFor { false }.replay)))
    }
}
