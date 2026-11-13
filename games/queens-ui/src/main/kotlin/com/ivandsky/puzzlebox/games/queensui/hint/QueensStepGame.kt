package com.ivandsky.puzzlebox.games.queensui.hint

import com.ivandsky.puzzlebox.designsystem.spotlight.Spotlight
import com.ivandsky.puzzlebox.gameui.hint.OncePerPuzzle
import com.ivandsky.puzzlebox.gameui.hint.StepHints
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.QueensSession
import com.ivandsky.puzzlebox.games.queens.QueensSessionStep
import com.ivandsky.puzzlebox.games.queens.engine.hints.step.QueensStep

/**
 * Queens behind [StepHints]: snapshots the live [session], runs [QueensSessionStep] off the main thread and maps the step
 * to its Spotlight. A press never places or removes anything (A1, A4): a culprit is only marked.
 *
 * The solution is resolved once per puzzle (A19) inside the compute block, by a search bounded by a poll-counting
 * Budget, never wall-clock, so the answer is repeatable; it runs even when the puzzle stores a solution. It keeps a
 * solution only when it is the one solution (A3; Main ruling on 27, as Tango): judging entries against one of several
 * would accuse a queen that is right in another. No solution or several is null, and the stepper then accuses only
 * rule breaks and never reveals. A budget spent before a second solution turns up keeps the stored solution (null when
 * none is stored), so a hard board judges as it did before the search ran. Main-confined except the compute block,
 * which only reads the snapshot and the memo. [solve] is a seam for tests.
 */
internal class QueensStepGame(
    private val session: () -> QueensSession,
    solve: (QueensPuzzle) -> Set<Cell>? = { QueensSessionStep.boundedSolution(it) },
) : StepHints.Game<QueensStep, QueensSpotlightMapper.Key> {

    private val solutions = OncePerPuzzle(solve)

    override fun capture(): () -> QueensStep {
        val current = session()
        return { QueensSessionStep.next(current, solutionOf(current.puzzle)) }
    }

    override fun keyOf(step: QueensStep): QueensSpotlightMapper.Key? = QueensSpotlightMapper.keyOf(step)

    override fun spotlight(step: QueensStep, playId: Int): Spotlight =
        QueensSpotlightMapper.map(step, session().puzzle, playId)

    private fun solutionOf(puzzle: QueensPuzzle): Set<Cell>? = solutions.get(puzzle)
}
