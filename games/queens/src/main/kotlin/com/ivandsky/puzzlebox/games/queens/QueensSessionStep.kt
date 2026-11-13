package com.ivandsky.puzzlebox.games.queens

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.games.queens.engine.hints.step.QueensStep
import com.ivandsky.puzzlebox.games.queens.engine.hints.step.QueensStepper
import com.ivandsky.puzzlebox.games.queens.engine.solve.QueensSearchOutcome
import com.ivandsky.puzzlebox.games.queens.engine.solve.QueensSolver

/**
 * The step a live hint press finds on a [QueensSession], so live play and a replay of that press ask
 * the same question: [QueensStepper] fed the session's board, Auto-cross, last player edit and the
 * puzzle's one [boundedSolution]. Pure and deterministic: the search is bounded by polls, never by time.
 */
object QueensSessionStep {

    /** Budget polls allowed, each after 1024 column candidates; only guarantees the call never hangs. */
    const val POLL_LIMIT = 20_000

    fun next(session: QueensSession, solution: Set<Cell>?): QueensStep =
        QueensStepper.next(
            session.puzzle, session.board, session.autoCross, session.history.done.lastOrNull()?.let(::cellOf), solution,
        )

    /**
     * [puzzle]'s one solution within [pollLimit] budget polls; null when unsolvable or not unique. A spent budget
     * gives the stored solution, if any, unchecked.
     */
    fun boundedSolution(puzzle: QueensPuzzle, pollLimit: Int = POLL_LIMIT): Set<Cell>? {
        var polls = 0
        val outcome = QueensSolver().solutionsWithin(puzzle, limit = 2, budget = Budget { polls++ >= pollLimit })
        return when (outcome) {
            is QueensSearchOutcome.Done -> outcome.solutions.singleOrNull()?.toSet()
            QueensSearchOutcome.Exhausted -> puzzle.solution?.toSet()
        }
    }

    /** The cell a player's own edit touched; a clear names none. */
    private fun cellOf(command: QueensCommand): Cell? = when (command) {
        is QueensCommand.PlaceQueen -> command.cell
        is QueensCommand.RemoveQueen -> command.cell
        is QueensCommand.ToggleCross -> command.cell
        is QueensCommand.EraseCrosses -> null
        is QueensCommand.Clear -> null
    }
}
