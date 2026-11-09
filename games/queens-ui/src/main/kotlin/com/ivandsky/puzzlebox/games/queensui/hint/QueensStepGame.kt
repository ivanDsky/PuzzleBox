package com.ivandsky.puzzlebox.games.queensui.hint

import com.ivandsky.puzzlebox.designsystem.spotlight.Spotlight
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.gameui.hint.StepHints
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensCommand
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.QueensSession
import com.ivandsky.puzzlebox.games.queens.engine.hints.step.QueensStep
import com.ivandsky.puzzlebox.games.queens.engine.hints.step.QueensStepper
import com.ivandsky.puzzlebox.games.queens.engine.solve.QueensSearchOutcome
import com.ivandsky.puzzlebox.games.queens.engine.solve.QueensSolver

/**
 * Queens behind [StepHints]: snapshots the live [session], runs [QueensStepper] off the main thread, maps the step to
 * its Spotlight, and hands a culprit to [onEraseCulprit] (the caller removes a wrong queen with `withQueenRemoved` or a
 * wrong cross with `withCrossToggled`, one undo unit, A4). A press never places anything (A1).
 *
 * The solution is resolved once per puzzle (A19) inside the compute block: the stored one when the puzzle carries it,
 * else a search bounded by a poll-counting [Budget], never wall-clock, so the answer is repeatable. The search keeps a
 * solution only when it is the one solution (Main ruling on 27, as Tango): judging entries against one of several would
 * accuse a queen that is right in another. A spent budget, no solution or several is null, and the stepper then
 * accuses only rule breaks and never reveals. Main-confined except the compute block, which only reads the snapshot
 * and the memo. [solve] is a seam for tests.
 */
internal class QueensStepGame(
    private val session: () -> QueensSession,
    private val onEraseCulprit: (QueensStep.Mistake) -> Unit,
    private val solve: (QueensPuzzle) -> Set<Cell>? = { boundedSolution(it) },
) : StepHints.Game<QueensStep, QueensSpotlightMapper.Key> {

    /** The resolved solution of one puzzle; null [solution] when none could be found. */
    private class Solved(val puzzle: QueensPuzzle, val solution: Set<Cell>?)

    /** Written off-main by a compute block, read by later ones on any thread; racing searches agree (deterministic). */
    @Volatile private var solved: Solved? = null

    override fun capture(): () -> QueensStep {
        val current = session()
        val puzzle = current.puzzle
        val board = current.board
        val autoCross = current.autoCross
        val lastMove = current.history.done.lastOrNull()?.let(::cellOf)
        return { QueensStepper.next(puzzle, board, autoCross, lastMove, solutionOf(puzzle)) }
    }

    override fun keyOf(step: QueensStep): QueensSpotlightMapper.Key? = QueensSpotlightMapper.keyOf(step)

    override fun isCulprit(step: QueensStep): Boolean = step is QueensStep.Mistake

    override fun spotlight(step: QueensStep, playId: Int): Spotlight? =
        QueensSpotlightMapper.map(step, session().puzzle, playId)

    override fun eraseCulprit(step: QueensStep) {
        if (step is QueensStep.Mistake) onEraseCulprit(step)
    }

    private fun solutionOf(puzzle: QueensPuzzle): Set<Cell>? {
        solved?.takeIf { it.puzzle == puzzle }?.let { return it.solution }
        return (puzzle.solution?.toSet() ?: solve(puzzle)).also { solved = Solved(puzzle, it) }
    }

    /** The cell a player's own edit touched; a clear names none. */
    private fun cellOf(command: QueensCommand): Cell? = when (command) {
        is QueensCommand.PlaceQueen -> command.cell
        is QueensCommand.RemoveQueen -> command.cell
        is QueensCommand.ToggleCross -> command.cell
        is QueensCommand.Clear -> null
    }

    companion object {
        /** Budget polls allowed, each after 1024 column candidates; only guarantees the call never hangs. */
        const val POLL_LIMIT = 20_000

        /** [puzzle]'s one solution within [pollLimit] budget polls; null when exhausted, unsolvable or not unique. */
        fun boundedSolution(puzzle: QueensPuzzle, pollLimit: Int = POLL_LIMIT): Set<Cell>? {
            var polls = 0
            val outcome = QueensSolver().solutionsWithin(puzzle, limit = 2, budget = Budget { polls++ >= pollLimit })
            return (outcome as? QueensSearchOutcome.Done)?.solutions?.singleOrNull()?.toSet()
        }
    }
}
