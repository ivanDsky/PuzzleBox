package com.ivandsky.puzzlebox.games.queens

import com.ivandsky.puzzlebox.game.replay.GameReplay
import com.ivandsky.puzzlebox.game.replay.MoveKind
import com.ivandsky.puzzlebox.game.replay.MoveMark
import com.ivandsky.puzzlebox.game.replay.ReplayFacts
import com.ivandsky.puzzlebox.game.replay.ReplayMove
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

    /** A Place that adds a rule violation is a Mistake; a Queens hint places nothing, so it marks no cell. */
    override fun markOf(before: QueensSession, move: ReplayMove, after: QueensSession, previous: ReplayMove?): MoveMark =
        when (QueensReplayMove.of(move, after.puzzle.cellCount)) {
            is QueensReplayMove.Place ->
                if (violationsOf(after).any { it !in violationsOf(before) }) MoveMark(MoveKind.Mistake, emptySet()) else MoveMark.Plain
            QueensReplayMove.Undo, QueensReplayMove.Redo, QueensReplayMove.Clear -> MoveMark(MoveKind.Undo, emptySet())
            QueensReplayMove.Hint -> MoveMark(MoveKind.Hint, emptySet())
            else -> MoveMark.Plain
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
