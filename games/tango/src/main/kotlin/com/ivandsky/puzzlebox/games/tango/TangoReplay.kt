package com.ivandsky.puzzlebox.games.tango

import com.ivandsky.puzzlebox.game.replay.GameReplay
import com.ivandsky.puzzlebox.game.replay.MoveKind
import com.ivandsky.puzzlebox.game.replay.MoveMark
import com.ivandsky.puzzlebox.game.replay.ReplayFacts
import com.ivandsky.puzzlebox.game.replay.ReplayMove
import com.ivandsky.puzzlebox.games.tango.engine.TangoBoundedSolution
import com.ivandsky.puzzlebox.games.tango.engine.TangoMove
import com.ivandsky.puzzlebox.games.tango.engine.TangoRules
import com.ivandsky.puzzlebox.games.tango.engine.TangoState
import com.ivandsky.puzzlebox.games.tango.engine.TangoVerdict
import com.ivandsky.puzzlebox.games.tango.engine.step.TangoStep
import com.ivandsky.puzzlebox.games.tango.engine.step.TangoStepper
import com.ivandsky.puzzlebox.games.tango.render.TangoViolations
import java.time.Instant

/**
 * Tango solve replay: a recorded move runs through the session reducer live play calls for it,
 * so a replayed board cannot drift from the played one.
 */
object TangoReplay : GameReplay<TangoSession> {

    override fun step(session: TangoSession, move: ReplayMove, atMs: Long): TangoSession? =
        when (val typed = TangoReplayMove.of(move, session.puzzle.cellCount)) {
            null -> null
            is TangoReplayMove.Place -> placed(session, typed, Instant.ofEpochMilli(atMs))
            TangoReplayMove.Clear -> session.withCleared()
            TangoReplayMove.Undo -> session.withUndo()
            TangoReplayMove.Redo -> session.withRedo()
            TangoReplayMove.Hint -> hinted(session)
            TangoReplayMove.Rejection -> session.withRejection()
        }

    /**
     * A hint is marked with the cell its step pointed at: [MoveMark.hinted] holds that one row-major
     * cell index. Live play's hint (TangoStepGame) is a pure function of the board and the last
     * placement, with a poll-counted, repeatable solution search, so it is recomputed here from
     * [before] with the same [TangoStepper] call. The target is the step's own cell: the proven
     * cell of a deduction or reveal, the wrong entry of a mistake; evidence cells are not marked.
     * A solved board or an unavailable step marks none. A rejection is a Mistake, undo/redo/clear an Undo.
     */
    override fun markOf(before: TangoSession, move: ReplayMove, after: TangoSession, previous: ReplayMove?): MoveMark =
        when (TangoReplayMove.of(move, before.puzzle.cellCount)) {
            TangoReplayMove.Rejection -> MoveMark(MoveKind.Mistake, emptySet())
            TangoReplayMove.Undo, TangoReplayMove.Redo, TangoReplayMove.Clear -> MoveMark(MoveKind.Undo, emptySet())
            TangoReplayMove.Hint -> MoveMark(MoveKind.Hint, hintTarget(before))
            is TangoReplayMove.Place, null -> MoveMark.Plain
        }

    /**
     * A mistake is transient when every violation it introduced ([after] against [before], by the
     * scan live play settles with) is gone again in [next]: live play never showed it. A mistake that
     * introduced no new violation was shown at once, so it is never transient.
     */
    override fun isTransientMistake(before: TangoSession, after: TangoSession, next: TangoSession): Boolean {
        val introduced = TangoViolations.cellSets(after) - TangoViolations.cellSets(before)
        return introduced.isNotEmpty() && introduced.none { it in TangoViolations.cellSets(next) }
    }

    /** The cell the live step hint points at on [session]'s board, as TangoStepGame.capture computes it. */
    private fun hintTarget(session: TangoSession): Set<Int> {
        val puzzle = session.puzzle
        val lastMove = (session.history.done.lastOrNull() as? TangoPlace)?.idx
        return when (val step = TangoStepper.next(puzzle, session.grid(), lastMove, TangoBoundedSolution.of(puzzle))) {
            is TangoStep.Deduction -> setOf(step.idx)
            is TangoStep.Reveal -> setOf(step.idx)
            is TangoStep.Mistake -> setOf(step.idx)
            TangoStep.Solved, TangoStep.Unavailable -> emptySet()
        }
    }

    override fun factsOf(initial: TangoSession): (TangoSession) -> ReplayFacts {
        val solution = TangoBoundedSolution.of(initial.puzzle)
        val total = initial.puzzle.cellCount - initial.puzzle.givens.size
        return { session ->
            ReplayFacts(
                hints = session.hintsUsed,
                correct = when {
                    session.isComplete -> total
                    solution != null -> session.placements.count { (idx, value) -> solution[idx] == value }
                    else -> cleanCells(session)
                },
                total = total,
                solved = session.isComplete,
            )
        }
    }

    /** A given is no player cell: live input never reaches one, so a record that does is not Tango play. */
    private fun placed(session: TangoSession, move: TangoReplayMove.Place, now: Instant): TangoSession? =
        if (session.puzzle.isGiven(move.cell)) null else session.withMove(TangoMove(move.cell, move.value), now)

    /** Live play disables the hint control on a finished board (TangoPlayController.hintEnabled), so none is ever recorded. */
    private fun hinted(session: TangoSession): TangoSession? =
        if (session.isComplete) null else session.copy(hintsUsed = session.hintsUsed + 1)

    /** With no solution to count against, a cell counts while the rules find nothing wrong with it. */
    private fun cleanCells(session: TangoSession): Int {
        val grid = session.grid()
        return session.placements.keys.count { idx ->
            val without = grid.toMutableList().also { it[idx] = null }
            TangoRules.validate(TangoMove(idx, grid[idx]), TangoState(session.puzzle, without)) == TangoVerdict.Valid
        }
    }
}
