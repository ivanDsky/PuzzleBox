package com.ivandsky.puzzlebox.games.queensui.hint

import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.HintReason
import com.ivandsky.puzzlebox.games.queens.engine.hints.step.QueensStep

/**
 * The callout line for a [QueensStep]: one line (at most two at 360 dp). Plain Kotlin strings keyed by the rung's
 * captionKey, so no `when` over rungs exists to hide a missing entry. The sentences are the Spotlight's own.
 * A sentence that needs it is filled with the named unit's kind (color, row, column).
 *
 * Every line is a plain sentence that explains the step; none opens with a technique name.
 */
internal object QueensStepCopy {

    private val deductions: Map<String, (unit: String) -> String> = mapOf(
        "queens.hint.rung.attack" to { _ -> "Queens block their lines: cross out what they attack." },
        "queens.hint.rung.singles" to { unit -> "This $unit has one open square left, so the queen goes there." },
        "queens.hint.rung.line-lock" to { unit -> "A color fits one $unit only, so the rest of that $unit is out." },
        "queens.hint.rung.shared-neighbours" to { unit -> "A queen here would touch a whole $unit, so it can't go here." },
        "queens.hint.rung.pigeonhole" to { _ -> "These colors use up these lines, so the rest are out." },
        "queens.hint.rung.line-group-pigeonhole" to { _ -> "These rows use up these columns, so the rest are out." },
        "queens.hint.rung.leftover-queen" to { _ -> "One queen is left for the ringed squares; the rest are out." },
        "queens.hint.rung.squeeze" to { unit -> "A queen here would block every open square of a $unit." },
        "queens.hint.rung.count-squeeze" to { _ -> "A queen here leaves too few squares for some colors." },
        "queens.hint.rung.short-look-ahead" to { unit -> "A queen here forces a square that leaves a $unit no room." },
        "queens.hint.rung.long-look-ahead" to { unit -> "A queen here leads on to a $unit with no room." },
    )

    /** The callout of a step that has one; [QueensStep.Solved] and [QueensStep.Unavailable] show nothing and throw. */
    fun text(step: QueensStep): String = when (step) {
        is QueensStep.Deduction -> deductions.getValue(step.unit.rung.captionKey)(unitWord(step.unit.kind))
        is QueensStep.Mistake -> when (step.kind) {
            QueensStep.Mistake.Kind.OFF_SOLUTION -> "This queen is not in the solution."
            QueensStep.Mistake.Kind.RULE_BREAK -> "These two queens clash."
            QueensStep.Mistake.Kind.WRONG_CROSS -> "This × is wrong: a queen belongs on this square."
        }
        is QueensStep.Reveal -> "This square can't hold a queen, though no simple step shows why."
        QueensStep.Solved, QueensStep.Unavailable -> error("$step has no callout")
    }

    private fun unitWord(kind: HintReason.Kind): String = when (kind) {
        HintReason.Kind.ROW -> "row"
        HintReason.Kind.COLUMN -> "column"
        HintReason.Kind.REGION -> "color"
    }
}
