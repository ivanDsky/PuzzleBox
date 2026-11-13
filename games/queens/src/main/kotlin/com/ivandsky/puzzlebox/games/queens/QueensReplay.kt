package com.ivandsky.puzzlebox.games.queens

import com.ivandsky.puzzlebox.game.replay.GameReplay
import com.ivandsky.puzzlebox.game.replay.MoveKind
import com.ivandsky.puzzlebox.game.replay.MoveMark
import com.ivandsky.puzzlebox.game.replay.ReplayFacts
import com.ivandsky.puzzlebox.game.replay.ReplayMove
import com.ivandsky.puzzlebox.games.queens.engine.hints.step.QueensStep
import com.ivandsky.puzzlebox.games.queens.engine.rules.QueensRuleViolation
import com.ivandsky.puzzlebox.games.queens.engine.rules.QueensRules
import com.ivandsky.puzzlebox.games.queens.engine.rules.QueensRulesVerdict
import com.ivandsky.puzzlebox.games.queens.engine.solve.QueensSolver
import java.time.Instant

/**
 * Queens solve replay: a recorded move runs through the session reducer live play calls for it,
 * so a replayed board cannot drift from the played one.
 */
object QueensReplay : GameReplay<QueensSession> {

    override fun step(session: QueensSession, move: ReplayMove, atMs: Long): QueensSession? {
        val now = Instant.ofEpochMilli(atMs)
        return when (val typed = QueensReplayMove.of(move, session.puzzle.cellCount)) {
            null -> null
            is QueensReplayMove.Place -> session.withQueenPlaced(typed.cell, now)
            is QueensReplayMove.Remove -> session.withQueenRemoved(typed.cell, now)
            is QueensReplayMove.Cross -> crossed(session, typed.cell, now)
            is QueensReplayMove.Erase -> session.withCrossErased(typed.cell, typed.extend, now)
            QueensReplayMove.Clear -> session.withCleared(now)
            QueensReplayMove.Undo -> session.withUndo()
            QueensReplayMove.Redo -> session.withRedo()
            QueensReplayMove.Hint -> session.withHintUsed()
            is QueensReplayMove.AutoCross -> session.withAutoCross(typed.on)
        }
    }

    /**
     * A Place that adds a rule violation is a Mistake; Undo/Redo/Clear are Undo. A Hint is marked with
     * the cells its live press pointed at, found by asking [QueensSessionStep] of [before], the very
     * session the press read (the step is a pure function of it). Indices are `Cell.idx` (row-major):
     * a Mistake step names the wrong entry, a Deduction the cells it crosses or the one it fills, a
     * Reveal its cell; evidence cells are proof, not target, and are left out. Solved or Unavailable names none.
     */
    override fun markOf(before: QueensSession, move: ReplayMove, after: QueensSession, previous: ReplayMove?): MoveMark =
        when (QueensReplayMove.of(move, after.puzzle.cellCount)) {
            is QueensReplayMove.Place ->
                if (violationsOf(after).any { it !in violationsOf(before) }) MoveMark(MoveKind.Mistake, emptySet()) else MoveMark.Plain
            QueensReplayMove.Undo, QueensReplayMove.Redo, QueensReplayMove.Clear -> MoveMark(MoveKind.Undo, emptySet())
            QueensReplayMove.Hint -> MoveMark(MoveKind.Hint, hintTargets(before))
            else -> MoveMark.Plain
        }

    /** True when every violation [after] added over [before] is gone in [next]: the player fixed it before the live board showed it. */
    override fun isTransientMistake(before: QueensSession, after: QueensSession, next: QueensSession): Boolean {
        val settled = violationsOf(next)
        val kept = violationsOf(before)
        return violationsOf(after).none { it !in kept && it in settled }
    }

    override fun factsOf(initial: QueensSession): (QueensSession) -> ReplayFacts {
        val solution = (initial.puzzle.solution ?: onlySolution(initial.puzzle))?.toSet()
        val total = initial.requiredQueens
        return { session ->
            ReplayFacts(
                hints = session.hintsUsed,
                correct = when {
                    // A solved board may stand on an answer the stored solution does not name.
                    session.isComplete -> total
                    solution != null -> session.queens.count { it in solution }
                    else -> cleanQueens(session)
                },
                total = total,
                solved = session.isComplete,
            )
        }
    }

    /** The reducer is a toggle, and a recorded Cross only adds: a record must not erase a mark. */
    private fun crossed(session: QueensSession, cell: Cell, now: Instant): QueensSession =
        if (session.markAt(cell)?.kind == MarkKind.MANUAL) session else session.withCrossToggled(cell, now)

    /** A board with two answers has none to count against: the player may be on the other one. */
    private fun onlySolution(puzzle: QueensPuzzle): List<Cell>? =
        QueensSolver().search(puzzle, limit = 2).takeIf { it.solutionCount == 1 }?.firstSolution

    private fun hintTargets(before: QueensSession): Set<Int> =
        when (val step = QueensSessionStep.next(before, QueensSessionStep.boundedSolution(before.puzzle))) {
            is QueensStep.Mistake -> setOf(step.cell.idx)
            is QueensStep.Deduction -> when (val result = step.result) {
                is QueensStep.Deduction.Result.Cross -> result.cells.mapTo(HashSet()) { it.idx }
                is QueensStep.Deduction.Result.LastCell -> setOf(result.cell.idx)
            }
            is QueensStep.Reveal -> setOf(step.cell.idx)
            QueensStep.Solved, QueensStep.Unavailable -> emptySet()
        }

    private fun violationsOf(session: QueensSession): Set<QueensRuleViolation> =
        (QueensRules.evaluate(session.puzzle, session.queens.toSet()) as? QueensRulesVerdict.Invalid)
            ?.violations?.toSet().orEmpty()

    private fun cleanQueens(session: QueensSession): Int {
        val queens = session.queens.toSet()
        val verdict = QueensRules.evaluate(session.puzzle, queens)
        if (verdict !is QueensRulesVerdict.Invalid) return queens.size
        return (queens - verdict.violations.map { it.cell }.toSet()).size
    }
}
