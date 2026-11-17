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
 * Two sets, picked per play by the caller (`plain`, the HintPolish flag): the legacy lines start with the deduction
 * name (`Pair: ...`); the plain lines are one sentence in the game's own words and carry no technique name.
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

    private val plainDeductions: Map<TangoStepRung, (TangoStep.Deduction, TangoPuzzle) -> String> = mapOf(
        TangoStepRung.SIGN to { step, puzzle -> plainSign(step, puzzle) },
        TangoStepRung.PAIR to { step, _ -> "Two ${plural(step.other)} touch, so the cells at both ends are ${plural(step.value)}" },
        TangoStepRung.SANDWICH to { step, _ -> "This cell sits between two ${plural(step.other)}, so it is a ${word(step.value)}" },
        TangoStepRung.FULL_LINE to { step, _ ->
            "${lineName(step).replaceFirstChar(Char::uppercase)} has all its ${plural(step.other)}, so the rest are ${plural(step.value)}"
        },
        TangoStepRung.EQUAL_PAIR to { step, _ -> "Beside a ${word(step.other)}, so both of these cells are ${plural(step.value)}" },
        TangoStepRung.SIGN_COUNT to { step, _ ->
            "The signs use up the ${plural(step.other)}, so this is a ${word(step.value)}"
        },
        TangoStepRung.SPLIT_PAIR to { step, _ -> "The gap holds one of each, so the rest are ${plural(step.value)}" },
        TangoStepRung.COUNT_SQUEEZE to { step, _ ->
            "The other kind here would break the line, so this is a ${word(step.value)}"
        },
        TangoStepRung.LINE_OPTIONS to { step, _ ->
            "All ${step.options} ways to fill this line put a ${word(step.value)} here"
        },
    )

    /** The line for [step]; [plain] picks the sentence set (HintPolish) over the legacy name-first one. */
    fun text(step: TangoStep, puzzle: TangoPuzzle, plain: Boolean = false): String = when (step) {
        is TangoStep.Deduction -> (if (plain) plainDeductions else deductions).getValue(step.rung)(step, puzzle)
        is TangoStep.Reveal ->
            if (plain) "This cell is a ${word(step.value)}, though no simple step finds it"
            else "Reveal: this cell is a ${word(step.value)}, no simple step"
        is TangoStep.Mistake ->
            if (plain) "This cell ${plainMistakeWhy(step.reason)}" else "Wrong cell: ${mistakeWhy(step.reason)}"
        TangoStep.Solved, TangoStep.Unavailable -> error("$step has no callout")
    }

    private fun plainSign(step: TangoStep.Deduction, puzzle: TangoPuzzle): String {
        val kind = (step.evidence - step.idx).firstNotNullOfOrNull { puzzle.markerBetween(step.idx, it) }
        val value = word(step.value)
        return when (kind) {
            TangoMarkerKind.SAME -> "An = sign copies its neighbor, so this is a $value"
            TangoMarkerKind.OPPOSITE -> "A × sign flips its neighbor, so this is a $value"
            null -> "The sign beside it makes this a $value"
        }
    }

    private fun plainMistakeWhy(reason: TangoVerdict.Reason?): String = when (reason) {
        TangoVerdict.Reason.ThreeInARow -> "makes three in a row"
        TangoVerdict.Reason.MarkerConflict -> "breaks a sign"
        TangoVerdict.Reason.CountExceeded -> "has too many of one kind"
        else -> "isn't in the solution"
    }

    private fun sign(step: TangoStep.Deduction, puzzle: TangoPuzzle): String {
        val kind = (step.evidence - step.idx).firstNotNullOfOrNull { puzzle.markerBetween(step.idx, it) }
        val value = word(step.value)
        return when (kind) {
            TangoMarkerKind.SAME -> "Sign: = copies its neighbor, so this is a $value"
            TangoMarkerKind.OPPOSITE -> "Sign: × flips its neighbor, so this is a $value"
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

    private val TangoStep.Deduction.other: TangoCell get() = value.other

    private fun word(value: TangoCell): String = if (value == TangoCell.SUN) "sun" else "moon"

    private fun plural(value: TangoCell): String = word(value) + "s"
}
