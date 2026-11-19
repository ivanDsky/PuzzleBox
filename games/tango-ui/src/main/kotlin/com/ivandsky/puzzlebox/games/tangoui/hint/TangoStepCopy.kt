package com.ivandsky.puzzlebox.games.tangoui.hint

import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoMarkerKind
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import com.ivandsky.puzzlebox.games.tango.engine.TangoVerdict
import com.ivandsky.puzzlebox.games.tango.engine.step.TangoStep
import com.ivandsky.puzzlebox.games.tango.engine.step.TangoStepRung

/**
 * The callout line for a [TangoStep]: one line (at most two at 360 dp) naming what the Spotlight draws. Plain Kotlin
 * strings like `TangoSemantics`. Keyed by rung, so no `when` over rungs exists to hide a missing entry.
 *
 * Every line is one sentence in the game's own words and carries no technique name.
 *
 * The step holds the target value, evidence, line and option count; the [TangoPuzzle] adds the marker kinds. The sign
 * line reads its marker from the puzzle and the target's own value, so it cannot name the wrong side. The board size
 * is never stated.
 */
internal object TangoStepCopy {

    private val deductions: Map<TangoStepRung, (TangoStep.Deduction, TangoPuzzle) -> String> = mapOf(
        TangoStepRung.SIGN to { step, puzzle -> sign(step, puzzle) },
        TangoStepRung.PAIR to { step, _ -> "Two ${plural(step.other)} touch, so the cells at both ends are ${plural(step.value)}" },
        TangoStepRung.SANDWICH to { step, _ -> "This cell sits between two ${plural(step.other)}, so it is a ${word(step.value)}" },
        TangoStepRung.FULL_LINE to { step, _ ->
            "${lineName(step).replaceFirstChar(Char::uppercase)} has all its ${plural(step.other)}, so the rest are ${plural(step.value)}"
        },
        TangoStepRung.EQUAL_PAIR to { step, _ -> "Joined by =, these would make three ${plural(step.other)} in a row, so both are ${plural(step.value)}" },
        TangoStepRung.SIGN_COUNT to { step, _ ->
            "Counting the signs, no ${word(step.other)} fits here, so this is a ${word(step.value)}"
        },
        TangoStepRung.SPLIT_PAIR to { step, _ -> "The gap holds one of each, so the rest are ${plural(step.value)}" },
        TangoStepRung.COUNT_SQUEEZE to { step, _ ->
            "The other kind here would break the line, so this is a ${word(step.value)}"
        },
        TangoStepRung.LINE_OPTIONS to { step, _ ->
            "All ${step.options} ways to fill this line put a ${word(step.value)} here"
        },
    )

    /** The line for [step]. */
    fun text(step: TangoStep, puzzle: TangoPuzzle): String = when (step) {
        is TangoStep.Deduction -> deductions.getValue(step.rung)(step, puzzle)
        is TangoStep.Reveal -> "This cell is a ${word(step.value)}, though no simple step finds it"
        is TangoStep.Mistake -> "This cell ${mistakeWhy(step.reason)}"
        TangoStep.Solved, TangoStep.Unavailable -> error("$step has no callout")
    }

    private fun sign(step: TangoStep.Deduction, puzzle: TangoPuzzle): String {
        val kind = (step.evidence - step.idx).firstNotNullOfOrNull { puzzle.markerBetween(step.idx, it) }
        val value = word(step.value)
        return when (kind) {
            TangoMarkerKind.SAME -> "An = sign copies its neighbor, so this is a $value"
            TangoMarkerKind.OPPOSITE -> "A × sign flips its neighbor, so this is a $value"
            null -> "The sign beside it makes this a $value"
        }
    }

    private fun mistakeWhy(reason: TangoVerdict.Reason?): String = when (reason) {
        TangoVerdict.Reason.ThreeInARow -> "makes three in a row"
        TangoVerdict.Reason.MarkerConflict -> "breaks a sign"
        TangoVerdict.Reason.CountExceeded -> "has too many of one kind"
        else -> "isn't in the solution"
    }

    private fun lineName(step: TangoStep.Deduction): String =
        step.row?.let { "row ${it + 1}" } ?: "column ${(step.col ?: 0) + 1}"

    private val TangoStep.Deduction.other: TangoCell get() = value.other

    private fun word(value: TangoCell): String = if (value == TangoCell.SUN) "sun" else "moon"

    private fun plural(value: TangoCell): String = word(value) + "s"
}
