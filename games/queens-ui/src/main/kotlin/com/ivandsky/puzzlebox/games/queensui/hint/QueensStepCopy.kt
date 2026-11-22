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

    /**
     * A rung's sentences: [hint] for the callout of a step (filled with the unit it names), and [list] for the technique
     * list when that must read with no board in sight ("any of these squares" has nothing to point at there). A rung
     * with no [list] shows its hint with the unit left generic.
     */
    private class Copy(val hint: (unit: String) -> String, val list: String? = null)

    private val deductions: Map<String, Copy> = mapOf(
        "queens.hint.rung.attack" to Copy({ _ -> "Queens block their lines: cross out what they attack." }),
        "queens.hint.rung.singles" to Copy(
            { unit -> "This $unit has one open square left, so the queen goes there." },
            list = "When a row, column or color has one open square left, its queen goes there.",
        ),
        "queens.hint.rung.line-lock" to Copy(
            { unit -> "A color fits one $unit only, so the rest of that $unit is out." },
            list = "When a color's open squares all sit in one row or column, or a row's open squares are all one color, " +
                "cross out the rest of that row, column or color.",
        ),
        "queens.hint.rung.shared-neighbours" to Copy(
            { unit -> "A queen on any of these squares would touch every open square of a $unit." },
            list = "A square that touches every open square of a row, column or color can't hold a queen.",
        ),
        "queens.hint.rung.pigeonhole" to Copy(
            { _ -> "These colors and these rows or columns use each other up, so the rest are out." },
            list = "Some colors fit in exactly as many rows or columns (or the other way round), " +
                "so the rest of those lines or colors is out.",
        ),
        "queens.hint.rung.line-group-pigeonhole" to Copy(
            { _ -> "These rows and these columns use each other up, so the rest are out." },
            list = "Some rows fit in exactly as many columns (or columns in rows), so the rest of those lines is out.",
        ),
        "queens.hint.rung.leftover-queen" to Copy(
            { _ -> "A square that would rule out every square left for the last queen is out." },
        ),
        "queens.hint.rung.squeeze" to Copy(
            { unit -> "A queen on any of these squares would rule out every open square of a $unit." },
            list = "A square that rules out every open square of a row, column or color, by touching them or " +
                "sharing a row, column or color with them, can't hold a queen.",
        ),
        "queens.hint.rung.count-squeeze" to Copy(
            { _ -> "A queen on any of these squares would leave rows, columns or colors short of room." },
            list = "A queen here would leave two or more rows, columns or colors needing more queens than the lines they still reach.",
        ),
        "queens.hint.rung.short-look-ahead" to Copy(
            { unit -> "A queen here forces moves that leave a $unit or a group of lines no room." },
            list = "A square whose queen forces moves that leave a row, column or color, or a group of them, " +
                "no room can't hold a queen.",
        ),
        "queens.hint.rung.long-look-ahead" to Copy({ unit -> "A queen here leads on to a $unit with no room." }),
    )

    /** The callout of a step that has one; [QueensStep.Solved] and [QueensStep.Unavailable] show nothing and throw. */
    fun text(step: QueensStep): String = when (step) {
        is QueensStep.Deduction -> deductions.getValue(step.unit.rung.captionKey).hint(unitWord(step.unit.kind))
        is QueensStep.Mistake -> when (step.kind) {
            QueensStep.Mistake.Kind.OFF_SOLUTION -> "This queen is not in the solution."
            QueensStep.Mistake.Kind.RULE_BREAK -> "These two queens clash."
            QueensStep.Mistake.Kind.WRONG_CROSS -> "This × is wrong: a queen belongs on this square."
        }
        is QueensStep.Reveal -> "This square can't hold a queen, though no simple step shows why."
        QueensStep.Solved, QueensStep.Unavailable -> error("$step has no callout")
    }

    /**
     * The sentence of the rung keyed [captionKey], for a technique with no step in hand (the Trainer's technique list):
     * the rung's list sentence, or the hint's with the unit left generic.
     */
    fun technique(captionKey: String): String {
        val copy = deductions.getValue(captionKey)
        return copy.list ?: copy.hint(ANY_UNIT)
    }

    private const val ANY_UNIT = "row, column or color"

    private fun unitWord(kind: HintReason.Kind): String = when (kind) {
        HintReason.Kind.ROW -> "row"
        HintReason.Kind.COLUMN -> "column"
        HintReason.Kind.REGION -> "color"
    }
}
