package com.ivandsky.puzzlebox.games.tangoui.hint

import com.ivandsky.puzzlebox.designsystem.spotlight.Spotlight
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.gameui.hint.StepHints
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoPlace
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import com.ivandsky.puzzlebox.games.tango.TangoSession
import com.ivandsky.puzzlebox.games.tango.engine.TangoSearchOutcome
import com.ivandsky.puzzlebox.games.tango.engine.TangoUniqueness
import com.ivandsky.puzzlebox.games.tango.engine.step.TangoStep
import com.ivandsky.puzzlebox.games.tango.engine.step.TangoStepper

/**
 * Tango behind [StepHints]: snapshots the live [session], runs [TangoStepper] off the main thread, maps the step to its
 * Spotlight, and hands a culprit to [onEraseCulprit] (the caller records `TangoMove(idx, null)` through `withMove`, one
 * undo unit, A4). A press never places anything (A1).
 *
 * Tango stores no solution, so the compute block searches it once per puzzle (A19) with a poll-counting [Budget], never
 * wall-clock, so the answer is repeatable; no unique solution or a spent budget is null, and the stepper then accuses
 * only rule breaks and never reveals. Main-confined except the compute block, which only reads the snapshot and the
 * memo. [solve] is a seam for tests.
 */
internal class TangoStepGame(
    private val session: () -> TangoSession,
    private val onEraseCulprit: (TangoStep.Mistake) -> Unit,
    private val solve: (TangoPuzzle) -> List<TangoCell>? = { boundedSolution(it) },
) : StepHints.Game<TangoStep, TangoSpotlightMapper.Key> {

    /** The searched solution of one puzzle; null [solution] when the search found none unique. */
    private class Solved(val puzzle: TangoPuzzle, val solution: List<TangoCell>?)

    /** Written off-main by a compute block, read by later ones on any thread; racing searches agree (deterministic). */
    @Volatile private var solved: Solved? = null

    override fun capture(): () -> TangoStep {
        val current = session()
        val puzzle = current.puzzle
        val grid = current.grid()
        val lastMove = (current.history.done.lastOrNull() as? TangoPlace)?.idx
        return { TangoStepper.next(puzzle, grid, lastMove, solutionOf(puzzle)) }
    }

    override fun keyOf(step: TangoStep): TangoSpotlightMapper.Key? = TangoSpotlightMapper.keyOf(step)

    override fun isCulprit(step: TangoStep): Boolean = step is TangoStep.Mistake

    /** The press-time board: any input invalidates the search before it delivers, so the live board is the snapshot. */
    override fun spotlight(step: TangoStep, playId: Int): Spotlight? {
        val current = session()
        return TangoSpotlightMapper.map(step, current.puzzle, current.grid(), playId)
    }

    override fun eraseCulprit(step: TangoStep) {
        if (step is TangoStep.Mistake) onEraseCulprit(step)
    }

    private fun solutionOf(puzzle: TangoPuzzle): List<TangoCell>? {
        solved?.takeIf { it.puzzle == puzzle }?.let { return it.solution }
        return solve(puzzle).also { solved = Solved(puzzle, it) }
    }

    companion object {
        /** Search nodes allowed; a 6×6 daily needs a handful, so this only guarantees the call never hangs. */
        const val POLL_LIMIT = 100_000

        /** [puzzle]'s one solution within [pollLimit] search nodes; null when exhausted or not unique. */
        fun boundedSolution(puzzle: TangoPuzzle, pollLimit: Int = POLL_LIMIT): List<TangoCell>? {
            var polls = 0
            val outcome = TangoUniqueness.bounded(puzzle, limit = 2, budget = Budget { polls++ >= pollLimit })
            return (outcome as? TangoSearchOutcome.Done)?.solutions?.singleOrNull()
        }
    }
}
