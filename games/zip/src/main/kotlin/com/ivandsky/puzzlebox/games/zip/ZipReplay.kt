package com.ivandsky.puzzlebox.games.zip

import com.ivandsky.puzzlebox.game.replay.GameReplay
import com.ivandsky.puzzlebox.game.replay.MoveKind
import com.ivandsky.puzzlebox.game.replay.MoveMark
import com.ivandsky.puzzlebox.game.replay.ReplayFacts
import com.ivandsky.puzzlebox.game.replay.ReplayMove
import com.ivandsky.puzzlebox.games.zip.engine.ZipMove
import com.ivandsky.puzzlebox.games.zip.engine.ZipRules
import com.ivandsky.puzzlebox.games.zip.engine.ZipSolver
import com.ivandsky.puzzlebox.games.zip.engine.ZipState
import com.ivandsky.puzzlebox.games.zip.engine.ZipValidation
import java.time.Instant

/**
 * Zip solve replay: a recorded move runs through the session reducer live play calls for it,
 * so a replayed board cannot drift from the played one.
 */
object ZipReplay : GameReplay<ZipSession> {

    override fun step(session: ZipSession, move: ReplayMove, atMs: Long): ZipSession? {
        val now = Instant.ofEpochMilli(atMs)
        return when (val typed = ZipReplayMove.of(move, session.puzzle.board.cellCount)) {
            null -> null
            is ZipReplayMove.Extend -> extended(session, typed.cell, now)
            is ZipReplayMove.RetractTo -> session.withMove(ZipMove.RetractTo(typed.cell), now)
            ZipReplayMove.Reset -> session.withMove(ZipMove.Reset, now)
            ZipReplayMove.Undo -> session.withUndo()
            ZipReplayMove.Redo -> session.withRedo()
            // Live hint() returns early once the trace is finished.
            // Live input is ignored once the trace is finished, so no rejection follows it.
            ZipReplayMove.Rejection -> if (session.isComplete) session else session.withRejection()
            ZipReplayMove.Hint -> if (session.isComplete) session else session.withHintApplied()
        }
    }

    /**
     * A hint is recorded as its trim, [ZipReplayMove.Hint], then its step, so the [ZipReplayMove.Extend]
     * right after a Hint is the cell the hint placed. A refused step is a Mistake, undo/redo/clear an Undo.
     */
    override fun markOf(before: ZipSession, move: ReplayMove, after: ZipSession, previous: ReplayMove?): MoveMark {
        val cellCount = before.puzzle.board.cellCount
        return when (val typed = ZipReplayMove.of(move, cellCount)) {
            ZipReplayMove.Hint -> MoveMark(MoveKind.Hint, emptySet())
            ZipReplayMove.Rejection -> MoveMark(MoveKind.Mistake, emptySet())
            ZipReplayMove.Undo, ZipReplayMove.Redo, ZipReplayMove.Reset -> MoveMark(MoveKind.Undo, emptySet())
            is ZipReplayMove.Extend ->
                if (previous?.let { ZipReplayMove.of(it, cellCount) } == ZipReplayMove.Hint) {
                    MoveMark(MoveKind.Hint, setOf(typed.cell.idx))
                } else {
                    MoveMark.Plain
                }
            else -> MoveMark.Plain
        }
    }

    override fun factsOf(initial: ZipSession): (ZipSession) -> ReplayFacts {
        val solution = initial.puzzle.solution ?: onlySolution(initial.puzzle)
        val total = initial.puzzle.board.cellCount
        return { session ->
            ReplayFacts(
                hints = session.hintsUsed,
                correct = when {
                    session.isComplete -> total
                    // A trace is rule-legal cell by cell, so with no single answer its length is what stands.
                    solution == null -> session.path.size
                    else -> session.path.zip(solution).takeWhile { (a, b) -> a == b }.size
                },
                total = total,
                solved = session.isComplete,
            )
        }
    }

    /** Live play validates before it applies; a recorded step the rules refuse is not a Zip move. */
    private fun extended(session: ZipSession, cell: Cell, now: Instant): ZipSession? {
        val move = ZipMove.Extend(cell)
        return when (ZipRules.validateMove(move, ZipState(session.puzzle, session.path))) {
            is ZipValidation.Valid -> session.withMove(move, now)
            // A finished trace is frozen: the reducer ignores the move, the rules would call it a reuse.
            is ZipValidation.Invalid -> session.takeIf { it.isComplete }
        }
    }

    /** A board with several answers has none to count against: the player may be on another. */
    private fun onlySolution(puzzle: ZipPuzzle): List<Cell>? =
        ZipSolver().search(puzzle, emptyList(), limit = 2).takeIf { it.solutionCount == 1 }?.firstSolution
}
