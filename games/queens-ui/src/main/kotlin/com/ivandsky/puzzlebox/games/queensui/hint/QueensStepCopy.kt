package com.ivandsky.puzzlebox.games.queensui.hint

import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.HintReason
import com.ivandsky.puzzlebox.games.queens.engine.hints.step.QueensStep

/**
 * The callout line for a [QueensStep]: one line (at most two at 360 dp), opening with the deduction name and a colon.
 * Plain Kotlin strings like `QueensRungCopy`, keyed by the rung's captionKey the same way, so no `when` over rungs
 * exists to hide a missing entry. The sentences are the Spotlight's own; the legacy `QueensRungCopy` is not reused.
 * A sentence that needs it is filled with the named unit's kind (color, row, column).
 */
internal object QueensStepCopy {

    private val deductions: Map<String, (unit: String) -> String> = mapOf(
        "queens.hint.rung.attack" to { _ -> "Queens block their lines: cross out what they attack." },
        "queens.hint.rung.singles" to { unit -> "Last cell: this $unit has one open square left." },
        "queens.hint.rung.line-lock" to { unit -> "Line lock: a color fits one $unit only, so the rest of it is out." },
        "queens.hint.rung.shared-neighbours" to { unit -> "Shared squares: a queen here would touch a whole $unit." },
        "queens.hint.rung.pigeonhole" to { _ -> "Pigeonhole: these colors fill these lines, so the rest are out." },
        "queens.hint.rung.line-group-pigeonhole" to { _ -> "Pigeonhole: these rows fill these columns, so the rest are out." },
        "queens.hint.rung.leftover-queen" to { _ -> "Leftover: one queen is left for the ringed squares; the rest are out." },
        "queens.hint.rung.squeeze" to { unit -> "Squeeze: a queen here would block every open square of a $unit." },
        "queens.hint.rung.count-squeeze" to { _ -> "Count squeeze: a queen here leaves too few squares for some colors." },
        "queens.hint.rung.short-look-ahead" to { unit -> "Look-ahead: a queen here forces a square that leaves a $unit no room." },
        "queens.hint.rung.long-look-ahead" to { unit -> "Long look-ahead: a queen here leads on to a $unit with no room." },
    )

    /** The callout of a step that has one; [QueensStep.Solved] and [QueensStep.Unavailable] show nothing and throw. */
    fun text(step: QueensStep): String = when (step) {
        is QueensStep.Deduction -> deductions.getValue(step.unit.rung.captionKey)(unitWord(step.unit.kind))
        is QueensStep.Mistake -> when (step.kind) {
            QueensStep.Mistake.Kind.OFF_SOLUTION -> "Wrong queen: this queen is not in the solution."
            QueensStep.Mistake.Kind.RULE_BREAK -> "Wrong queen: these two queens clash."
            QueensStep.Mistake.Kind.WRONG_CROSS -> "Wrong ×: a queen belongs on this square."
        }
        is QueensStep.Reveal ->
            "No simple step here: this square can't hold a queen."
        QueensStep.Solved, QueensStep.Unavailable -> error("$step has no callout")
    }

    private fun unitWord(kind: HintReason.Kind): String = when (kind) {
        HintReason.Kind.ROW -> "row"
        HintReason.Kind.COLUMN -> "column"
        HintReason.Kind.REGION -> "color"
    }
}
