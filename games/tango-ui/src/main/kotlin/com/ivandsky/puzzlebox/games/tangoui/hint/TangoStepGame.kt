package com.ivandsky.puzzlebox.games.tangoui.hint

import com.ivandsky.puzzlebox.designsystem.spotlight.Spotlight
import com.ivandsky.puzzlebox.gameui.hint.OncePerPuzzle
import com.ivandsky.puzzlebox.gameui.hint.StepHints
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoPlace
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import com.ivandsky.puzzlebox.games.tango.TangoSession
import com.ivandsky.puzzlebox.games.tango.engine.TangoBoundedSolution
import com.ivandsky.puzzlebox.games.tango.engine.step.TangoStep
import com.ivandsky.puzzlebox.games.tango.engine.step.TangoStepper

/**
 * Tango behind [StepHints]: snapshots the live [session], runs [TangoStepper] off the main thread and maps the step to
 * its Spotlight. A press never places or erases anything (A1, A4): a culprit is only marked.
 *
 * Tango stores no solution, so the compute block searches it once per puzzle (A19) with a poll-counting budget, never
 * wall-clock, so the answer is repeatable; no unique solution or a spent budget is null, and the stepper then accuses
 * only rule breaks and never reveals. Main-confined except the compute block, which only reads the snapshot and the
 * memo. [solve] is a seam for tests.
 */
internal class TangoStepGame(
    private val session: () -> TangoSession,
    solve: (TangoPuzzle) -> List<TangoCell>? = { TangoBoundedSolution.of(it) },
) : StepHints.Game<TangoStep, TangoSpotlightMapper.Key> {

    private val solutions = OncePerPuzzle(solve)

    override fun capture(): () -> TangoStep {
        val current = session()
        val puzzle = current.puzzle
        val grid = current.grid()
        val lastMove = (current.history.done.lastOrNull() as? TangoPlace)?.idx
        return { TangoStepper.next(puzzle, grid, lastMove, solutionOf(puzzle)) }
    }

    override fun keyOf(step: TangoStep): TangoSpotlightMapper.Key? = TangoSpotlightMapper.keyOf(step)

    /** The press-time board: any input invalidates the search before it delivers, so the live board is the snapshot. */
    override fun spotlight(step: TangoStep, playId: Int): Spotlight {
        val current = session()
        return TangoSpotlightMapper.map(step, current.puzzle, current.grid(), playId)
    }

    private fun solutionOf(puzzle: TangoPuzzle): List<TangoCell>? = solutions.get(puzzle)
}
