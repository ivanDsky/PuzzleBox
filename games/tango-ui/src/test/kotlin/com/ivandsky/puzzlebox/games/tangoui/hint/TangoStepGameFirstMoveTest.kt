package com.ivandsky.puzzlebox.games.tangoui.hint

import com.ivandsky.puzzlebox.gameui.hint.HintMode
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoSession
import com.ivandsky.puzzlebox.games.tango.engine.TangoMove
import com.ivandsky.puzzlebox.games.tango.engine.step.TangoStep
import com.ivandsky.puzzlebox.games.tango.engine.step.TangoStepper
import com.ivandsky.puzzlebox.games.tangoui.TangoPlayFixture
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Pins that the mode's first-move option reaches the stepper (nav-rethink B4, A2): on a board where the two pick
 * orders differ, a game built from a first-move mode shows the first-move step and a game built without shows the
 * catalog step.
 */
class TangoStepGameFirstMoveTest {

    private val puzzle = TangoPlayFixture.puzzle
    private val solution: List<TangoCell> = TangoPlayFixture.solution

    /** The fixture replayed in index order up to the first board where the first-move step is not the catalog step. */
    private val differing: TangoSession = TangoPlayFixture.placements
        .runningFold(TangoSession(ref = null, puzzle = puzzle)) { session, (idx, value) ->
            session.withMove(TangoMove(idx, value), Instant.EPOCH)
        }
        .first { catalogStep(it) != firstMoveStep(it) }

    private fun lastMove(session: TangoSession): Int? = TangoPlayFixture.placements
        .lastOrNull { (idx, _) -> session.grid()[idx] != null }?.first

    private fun catalogStep(session: TangoSession): TangoStep =
        TangoStepper.next(puzzle, session.grid(), lastMove(session), solution)

    private fun firstMoveStep(session: TangoSession): TangoStep =
        TangoStepper.next(puzzle, session.grid(), lastMove(session), solution, firstMove = true)

    @Test
    fun `a game built with first move on asks the stepper for the first-move step`() {
        val step = TangoStepGame(session = { differing }, firstMove = true).capture()()

        assertEquals(firstMoveStep(differing), step)
        assertNotEquals(catalogStep(differing), step)
    }

    @Test
    fun `a game built without it keeps the catalog step`() {
        assertEquals(catalogStep(differing), TangoStepGame(session = { differing }).capture()())
    }

    @Test
    fun `the step hints of a first-move mode show the first-move step`() {
        val hints = tangoStepHints(
            HintMode.Stepwise(Dispatchers.Unconfined, firstMove = true),
            CoroutineScope(Dispatchers.Unconfined),
            session = { differing },
            count = {},
            solve = { solution },
        )

        hints.press()

        assertEquals(TangoSpotlightMapper.map(firstMoveStep(differing), puzzle, differing.grid(), hints.spotlight!!.playId), hints.spotlight)
    }
}
