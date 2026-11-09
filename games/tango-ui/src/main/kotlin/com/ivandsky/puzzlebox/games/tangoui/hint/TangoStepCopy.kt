package com.ivandsky.puzzlebox.games.tangoui.hint

import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoMarkerKind
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import com.ivandsky.puzzlebox.games.tango.engine.TangoVerdict
import com.ivandsky.puzzlebox.games.tango.engine.step.TangoStep
import com.ivandsky.puzzlebox.games.tango.engine.step.TangoStepRung

/**
 * The callout line for a [TangoStep]: one line (at most two at 360 dp), starting with the deduction name and
 * naming what the Spotlight draws. Plain Kotlin strings like `TangoSemantics`. Keyed by rung like `QueensRungCopy`,
 * so no `when` over rungs exists to hide a missing entry.
 *
 * The step holds the target value, evidence, line and option count; the [TangoPuzzle] adds the marker kinds. The sign
 * line reads its marker from the puzzle and the target's own value, so it cannot name the wrong side. The board size
 * is never stated.
 */
internal object TangoStepCopy {

    private val deductions: Map<TangoStepRung, (TangoStep.Deduction, TangoPuzzle) -> String> = mapOf(
        TangoStepRung.SIGN to { step, puzzle -> sign(step, puzzle) },
        TangoStepRung.PAIR to { step, _ -> "Pair: two ${plural(step.other)} touch, so ${plural(step.value)} at both ends" },
        TangoStepRung.SANDWICH to { step, _ -> "Sandwich: a ${word(step.value)} goes between two ${plural(step.other)}" },
        TangoStepRung.FULL_LINE to { step, _ ->
            "Full line: ${lineName(step)} has all its ${plural(step.other)}, the rest are ${plural(step.value)}"
        },
        TangoStepRung.EQUAL_PAIR to { step, _ ->
            "Equal pair: beside a ${word(step.other)}, so both are ${plural(step.value)}"
        },
        TangoStepRung.SIGN_COUNT to { step, _ ->
            "Sign count: the signs use up the ${plural(step.other)}, so a ${word(step.value)}"
        },
        TangoStepRung.SPLIT_PAIR to { step, _ -> "Split pair: gap holds 1 each, rest ${plural(step.value)}" },
        TangoStepRung.COUNT_SQUEEZE to { step, _ ->
            "Count squeeze: the other value here would break the line, so a ${word(step.value)}"
        },
        TangoStepRung.LINE_OPTIONS to { step, _ ->
            "Line options: all ${step.options} fills, a ${word(step.value)} here"
        },
    )

    fun text(step: TangoStep, puzzle: TangoPuzzle): String = when (step) {
        is TangoStep.Deduction -> deductions.getValue(step.rung)(step, puzzle)
        is TangoStep.Reveal -> "Reveal: this cell is a ${word(step.value)}, no simple step"
        is TangoStep.Mistake -> "Wrong cell: ${mistakeWhy(step.reason)}"
        TangoStep.Solved, TangoStep.Unavailable -> error("$step has no callout")
    }

    private fun sign(step: TangoStep.Deduction, puzzle: TangoPuzzle): String {
        val kind = (step.evidence - step.idx).firstNotNullOfOrNull { puzzle.markerBetween(step.idx, it) }
        val value = word(step.value)
        return when (kind) {
            TangoMarkerKind.SAME -> "Sign: = copies its neighbour, so this is a $value"
            TangoMarkerKind.OPPOSITE -> "Sign: × flips its neighbour, so this is a $value"
            null -> "Sign: the sign beside it makes this a $value"
        }
    }

    private fun mistakeWhy(reason: TangoVerdict.Reason?): String = when (reason) {
        TangoVerdict.Reason.ThreeInARow -> "three in a row"
        TangoVerdict.Reason.MarkerConflict -> "breaks a sign"
        TangoVerdict.Reason.CountExceeded -> "too many of one kind"
        else -> "this one isn't in the solution"
    }

    private fun lineName(step: TangoStep.Deduction): String =
        step.row?.let { "row ${it + 1}" } ?: "column ${(step.col ?: 0) + 1}"

    private val TangoStep.Deduction.other: TangoCell get() = other(value)

    private fun other(value: TangoCell): TangoCell = if (value == TangoCell.SUN) TangoCell.MOON else TangoCell.SUN

    private fun word(value: TangoCell): String = if (value == TangoCell.SUN) "sun" else "moon"

    private fun plural(value: TangoCell): String = word(value) + "s"
}
