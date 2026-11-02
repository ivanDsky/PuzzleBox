package com.ivandsky.puzzlebox.games.zip.engine

import com.ivandsky.puzzlebox.game.GameRules
import com.ivandsky.puzzlebox.game.Hint
import com.ivandsky.puzzlebox.game.Move
import com.ivandsky.puzzlebox.game.MoveVerdict
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.ZipPuzzle

/** The rules state: the puzzle plus the player's current trace ([path][0] = start). */
data class ZipState(
    val puzzle: ZipPuzzle,
    val path: List<Cell>,
)

/** Game-authored moves (drag steps, drag-backs, tap-ahead, reset). */
sealed interface ZipMove : Move {
    /** Append the next cell — from a drag step or an adjacent tap (F2 §10). */
    data class Extend(val to: Cell) : ZipMove

    /** Trim the trace back to an already-visited cell (drag-back over the path). */
    data class RetractTo(val to: Cell) : ZipMove

    data object Reset : ZipMove
}

/** Game-authored validation outcome; [Reason] drives the rejection animation + mistakes.
 *  [cell] is the offending target cell whenever the rejected move had one (null for a
 *  foreign [Move]) — it feeds the renderer model's rejection highlight. */
sealed interface ZipValidation : MoveVerdict {
    data object Valid : ZipValidation
    data class Invalid(val reason: Reason, val cell: Cell? = null) : ZipValidation

    sealed interface Reason {
        data object NotAdjacent : Reason
        data object WallBetween : Reason
        data object CellAlreadyInPath : Reason
        data object WrongStart : Reason
        data class WrongWaypointOrder(val expectedNext: Cell) : Reason
        data object OutOfBounds : Reason

        /** RetractTo target is not on the trace (defensive; the UI cannot produce it). */
        data object NotInPath : Reason

        /** Core-level [Move] that is not a ZipMove (defensive safe-cast rejection). */
        data object ForeignMove : Reason
    }
}

/**
 * Zip rules (F2 §2): O(1) local checks per move (I1–I5, I8), completeness (I6/I7), and
 * solver-backed hints counted by the caller (A3). Stateless singleton — pure, never
 * mutates state; rules/solver/hints are single instances shared by every caller.
 *
 * Thin facade over the ordered [pipeline] (A18 addendum §1): each rule is a self-contained
 * class whose `violated()` is the single truth; this object only composes them (cheap rules
 * first, one `val` — never per-move allocation), maps the first violation to the
 * player-visible [ZipValidation.Reason], and delegates completeness + hints. Structural
 * preconditions that are not board rules stay here: bounds (I8) and the retract target
 * check.
 */
object ZipRules : GameRules<ZipState> {

    private val hints = ZipHints()

    /** The fixed rule pipeline (A18): a new rule is a new class + one line here. */
    val pipeline: List<ZipRule> = listOf(
        StartCellRule(), // O(1), only consulted on the empty trace
        AdjacencyRule(), // O(1) index math
        WallCrossingRule(), // O(1) set lookup
        CellReuseRule(), // O(path) membership
        WaypointOrderRule(), // O(path) waypoint count
        CoverageOnCompleteRule(), // completion judges (I7)
        EndAtLastWaypointRule(), // completion judges (I6)
    )

    private val moveRules: List<ZipMoveRule> = pipeline.filterIsInstance<ZipMoveRule>()
    private val completionRules: List<ZipCompletionRule> = pipeline.filterIsInstance<ZipCompletionRule>()

    /**
     * Core hands us an untyped [Move]: safe-cast, never `as` — a foreign move yields a
     * typed rejection verdict, never a ClassCastException (GameRules contract).
     */
    override fun validate(move: Move, state: ZipState): MoveVerdict =
        when (val zipMove = move as? ZipMove) {
            is ZipMove -> validateMove(zipMove, state)
            null -> ZipValidation.Invalid(ZipValidation.Reason.ForeignMove)
        }

    fun validateMove(move: ZipMove, state: ZipState): ZipValidation {
        when (move) {
            ZipMove.Reset -> return ZipValidation.Valid

            is ZipMove.RetractTo -> return if (move.to in state.path) {
                ZipValidation.Valid // trim to the tail itself is a no-op
            } else {
                ZipValidation.Invalid(ZipValidation.Reason.NotInPath, move.to)
            }

            is ZipMove.Extend -> {
                if (!state.puzzle.board.contains(move.to)) {
                    return ZipValidation.Invalid(ZipValidation.Reason.OutOfBounds, move.to)
                }
                // I1–I5: the pipeline is the single truth; the first violated rule names
                // the rejection. For an Extend the offending cell is always the target,
                // so the verdict carries it for the renderer's rejection highlight.
                for (rule in moveRules) {
                    rule.check(move, state)?.let { return ZipValidation.Invalid(it.reason, move.to) }
                }
                return ZipValidation.Valid
            }
        }
    }

    override fun isComplete(state: ZipState): Boolean {
        // I6/I7 as pipeline judges first (O(1) rejects), then the linear I1–I5 + I8 oracle.
        if (completionRules.any { it.violated(ZipMove.Reset, state) }) return false
        return ZipVerifier.verifyPath(state.puzzle, state.path)
    }

    /** Hint (F2 §5 §3, A18 §6): Step onto the next correct segment of the stored
     *  solution, or — when the trace diverged from it — Backtrack to the last agreeing
     *  cell. The trim itself is free; the resulting hint counts once (A3, F2 §10).
     *  Solver-backed only for boards without a stored solution ([ZipHints]). */
    override fun hint(state: ZipState): Hint = hints.hint(state)

    fun isAdjacent(a: Cell, b: Cell, n: Int): Boolean = ZipVerifier.isAdjacent(a, b, n)

    /**
     * Linear verification that [path] satisfies I1–I5 and I8 against [puzzle] — the
     * A16 solution-verification half and the core of [isComplete]. Also implicitly
     * required of any prefix the hint machinery extends. Implementation: [ZipVerifier].
     */
    fun verifyPath(puzzle: ZipPuzzle, path: List<Cell>): Boolean =
        ZipVerifier.verifyPath(puzzle, path)
}
