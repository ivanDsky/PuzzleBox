package com.ivandsky.puzzlebox.games.zip.engine

/**
 * One self-contained rule of the Zip move grammar (A18 addendum §1): a single predicate
 * over (move, state) plus a verbose verdict derived from it. A new rule is a new class
 * plus one line in [ZipRules.pipeline].
 */
interface ZipRule {
    val id: ZipRuleId

    /**
     * Hot path: allocation-free. The SINGLE source of truth — the rules facade and the
     * solver's prunings both read this shape, so the verbose [check] cannot diverge.
     */
    fun violated(move: ZipMove, state: ZipState): Boolean

    /** Session/UI path: verbose verdict. Derived; allocates only on failure. */
    fun check(move: ZipMove, state: ZipState): ZipViolation? =
        if (violated(move, state)) ZipViolation(id, move, state) else null
}

/** Rules that gate live [ZipMove]s (I1–I5): the facade runs them on every authored move. */
interface ZipMoveRule : ZipRule

/**
 * Rules that judge the FINISHED trace (I6/I7): they never gate a live move — a full but
 * wrongly-ending trace validates move-by-move and simply is not complete. [isComplete]
 * evaluates them with the conventional moveless marker ([ZipMove.Reset]).
 */
interface ZipCompletionRule : ZipRule

/**
 * A detected rule violation: which rule, on which move, in which state. The
 * player-visible [reason] is presentation-only, derived 1:1 from the rule id.
 */
data class ZipViolation(
    val rule: ZipRuleId,
    val move: ZipMove,
    val state: ZipState,
) {
    val reason: ZipValidation.Reason
        get() = when (rule.value) {
            ZipRuleId.START_CELL -> ZipValidation.Reason.WrongStart
            ZipRuleId.ADJACENCY -> ZipValidation.Reason.NotAdjacent
            ZipRuleId.WALL_CROSSING -> ZipValidation.Reason.WallBetween
            ZipRuleId.CELL_REUSE -> ZipValidation.Reason.CellAlreadyInPath
            ZipRuleId.WAYPOINT_ORDER -> ZipValidation.Reason.WrongWaypointOrder(
                expectedWaypoint(state.puzzle.board, state.path),
            )
            else -> error("completeness rule $rule never rejects a live move")
        }
}
