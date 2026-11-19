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
 *
 * One template per rung serves two readers: the hint fills it with a step's own facts ([text]) and the Trainer's
 * technique list fills it with generic words ([technique]), so a copy edit shows in both (A1: no third copy set).
 */
internal object TangoStepCopy {

    /** What a rung's template names: the target [value], the [line] it counts in, the number of [ways] to fill it, and the sign beside the target. */
    private class Facts(
        val value: TangoCell,
        val line: String,
        val ways: String,
        /** Read only by the sign template, so a step whose rung needs none never scans the puzzle's markers. */
        val sign: () -> TangoMarkerKind?,
    ) {
        val other: TangoCell get() = value.other
    }

    private val deductions: Map<TangoStepRung, (Facts) -> String> = mapOf(
        TangoStepRung.SIGN to { facts -> sign(facts) },
        TangoStepRung.PAIR to { facts -> "Two ${plural(facts.other)} touch, so the cells at both ends are ${plural(facts.value)}" },
        TangoStepRung.SANDWICH to { facts -> "This cell sits between two ${plural(facts.other)}, so it is a ${word(facts.value)}" },
        TangoStepRung.FULL_LINE to { facts ->
            "${facts.line.replaceFirstChar(Char::uppercase)} has all its ${plural(facts.other)}, so the rest are ${plural(facts.value)}"
        },
        TangoStepRung.EQUAL_PAIR to { facts ->
            "Joined by =, these would make three ${plural(facts.other)} in a row, so both are ${plural(facts.value)}"
        },
        TangoStepRung.SIGN_COUNT to { facts ->
            "Counting the signs, no ${word(facts.other)} fits here, so this is a ${word(facts.value)}"
        },
        TangoStepRung.SPLIT_PAIR to { facts -> "The gap holds one of each, so the rest are ${plural(facts.value)}" },
        TangoStepRung.COUNT_SQUEEZE to { facts ->
            "The other kind here would break the line, so this is a ${word(facts.value)}"
        },
        TangoStepRung.LINE_OPTIONS to { facts ->
            "All ${facts.ways} ways to fill this line put a ${word(facts.value)} here"
        },
    )

    /** The line for [step]. */
    fun text(step: TangoStep, puzzle: TangoPuzzle): String = when (step) {
        is TangoStep.Deduction -> {
            val facts = Facts(step.value, lineName(step), step.options.toString()) { signBeside(step, puzzle) }
            deductions.getValue(step.rung)(facts)
        }
        is TangoStep.Reveal -> "This cell is a ${word(step.value)}, though no simple step finds it"
        is TangoStep.Mistake -> "This cell ${mistakeWhy(step.reason)}"
        TangoStep.Solved, TangoStep.Unavailable -> error("$step has no callout")
    }

    /**
     * The sentence of [rung] for a technique with no step in hand (the Trainer's technique list): the same template the
     * hint fills, with generic words: a sun as the target, "this line", "the" ways, and no sign to name, so the sign
     * line is the hint's own fallback.
     */
    fun technique(rung: TangoStepRung): String =
        deductions.getValue(rung)(Facts(TangoCell.SUN, ANY_LINE, ANY_WAYS) { null })

    private const val ANY_LINE = "this line"
    private const val ANY_WAYS = "the"

    private fun sign(facts: Facts): String {
        val value = word(facts.value)
        return when (facts.sign()) {
            TangoMarkerKind.SAME -> "An = sign copies its neighbor, so this is a $value"
            TangoMarkerKind.OPPOSITE -> "A × sign flips its neighbor, so this is a $value"
            null -> "The sign beside it makes this a $value"
        }
    }

    private fun signBeside(step: TangoStep.Deduction, puzzle: TangoPuzzle): TangoMarkerKind? =
        (step.evidence - step.idx).firstNotNullOfOrNull { puzzle.markerBetween(step.idx, it) }

    private fun mistakeWhy(reason: TangoVerdict.Reason?): String = when (reason) {
        TangoVerdict.Reason.ThreeInARow -> "makes three in a row"
        TangoVerdict.Reason.MarkerConflict -> "breaks a sign"
        TangoVerdict.Reason.CountExceeded -> "has too many of one kind"
        else -> "isn't in the solution"
    }

    private fun lineName(step: TangoStep.Deduction): String =
        step.row?.let { "row ${it + 1}" } ?: "column ${(step.col ?: 0) + 1}"

    private fun word(value: TangoCell): String = if (value == TangoCell.SUN) "sun" else "moon"

    private fun plural(value: TangoCell): String = word(value) + "s"
}
