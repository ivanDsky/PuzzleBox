package com.ivandsky.puzzlebox.games.queens.engine.hints.step

import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.engine.hints.HintReason
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.QueensRung

/** The one step a stepwise Queens hint shows. Carries no roles or copy; the UI maps it. */
sealed interface QueensStep {

    /**
     * The player entry at [cell] is wrong. [other] is the queen [cell] attacks for a
     * [Kind.RULE_BREAK], drawn as evidence, and null otherwise.
     */
    data class Mistake(val cell: Cell, val kind: Kind, val other: Cell?) : QueensStep {
        enum class Kind { RULE_BREAK, OFF_SOLUTION, WRONG_CROSS }
    }

    /** [rung] proves [result] about [unit]; [evidence] is the cells the rung read, as its proof states them. */
    data class Deduction(val rung: QueensRung, val unit: HintReason, val evidence: Set<Cell>, val result: Result) : QueensStep {
        sealed interface Result {
            data class Cross(val cells: Set<Cell>) : Result
            data class LastCell(val cell: Cell) : Result
        }
    }

    /** No rung has a proof: [cell] is open and holds no queen, shown without one. */
    data class Reveal(val cell: Cell) : QueensStep

    data object Solved : QueensStep

    /** No sound step exists: the ladder found nothing AND [solution] is null (expected unreachable ≤11×11).
     *  UI: no Spotlight, nothing counted. Never an unsound Reveal. */
    data object Unavailable : QueensStep
}
