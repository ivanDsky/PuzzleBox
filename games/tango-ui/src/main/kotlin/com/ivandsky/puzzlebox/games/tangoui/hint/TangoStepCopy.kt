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
 * The hint templates are written for a board with one square lit, so they point ("this", "here"). The Trainer's technique
 * list and "The idea" line have no board to point at, so each rung has a technique-level line of its own ([technique]).
 */
internal object TangoStepCopy {

    /**
     * What a rung's template names: the target [value], the [line] it counts in, the number of [ways] to fill it, whether
     * the step reads an `=` pair that holds its own targets ([inPair]), and the sign beside the target.
     */
    private class Facts(
        val value: TangoCell,
        val line: String,
        val ways: Int?,
        val inPair: Boolean,
        /** Read only by the sign template, so a step whose rung needs none never scans the puzzle's markers. */
        val sign: () -> TangoMarkerKind?,
    ) {
        val other: TangoCell get() = value.other
    }

    private val deductions: Map<TangoStepRung, (Facts) -> String> = mapOf(
        TangoStepRung.SIGN to { facts -> sign(facts) },
        TangoStepRung.PAIR to { facts -> "Two ${plural(facts.other)} touch, so an empty end next to them is a ${word(facts.value)}" },
        TangoStepRung.SANDWICH to { facts -> "This square sits between two ${plural(facts.other)}, so it is a ${word(facts.value)}" },
        TangoStepRung.FULL_LINE to { facts ->
            "${facts.line.replaceFirstChar(Char::uppercase)} has all its ${plural(facts.other)}, so the rest are ${plural(facts.value)}"
        },
        TangoStepRung.EQUAL_PAIR to { facts ->
            "Joined by =, these would make three ${plural(facts.other)} in a row, so both are ${plural(facts.value)}"
        },
        TangoStepRung.SIGN_COUNT to { facts ->
            if (facts.inPair) {
                "Only one ${word(facts.other)} is left but the = pair needs two, so both are ${plural(facts.value)}"
            } else {
                "Counting the signs, no ${word(facts.other)} fits here, so this is a ${word(facts.value)}"
            }
        },
        TangoStepRung.SPLIT_PAIR to { facts -> "The gap holds one of each, so the rest are ${plural(facts.value)}" },
        TangoStepRung.COUNT_SQUEEZE to { facts ->
            "A ${word(facts.other)} here uses up the ${plural(facts.other)} and breaks the line, so this is a ${word(facts.value)}"
        },
        TangoStepRung.LINE_OPTIONS to { facts ->
            when (val ways = facts.ways) {
                1 -> "Only one way to fill this line works, and it puts a ${word(facts.value)} here"
                2 -> "Both ways to fill this line put a ${word(facts.value)} here"
                else -> "All $ways ways to fill this line put a ${word(facts.value)} here"
            }
        },
    )

    /** The line for [step]. */
    fun text(step: TangoStep, puzzle: TangoPuzzle): String = when (step) {
        is TangoStep.Deduction -> {
            val facts = Facts(step.value, lineName(step), step.options, step.idx in step.evidence) { signBeside(step, puzzle) }
            deductions.getValue(step.rung)(facts)
        }
        is TangoStep.Reveal -> "This square is a ${word(step.value)}, though no simple step finds it"
        is TangoStep.Mistake -> "This square ${mistakeWhy(step.reason)}"
        TangoStep.Solved, TangoStep.Unavailable -> error("$step has no callout")
    }

    /**
     * The technique-level line of [rung]: what the technique does, in words that need no board (no "this", "here" or
     * "the gap") and that name both kinds. The Trainer's technique list and the "The idea" line under a practice board.
     */
    fun technique(rung: TangoStepRung): String = techniques.getValue(rung)

    private val techniques: Map<TangoStepRung, String> = mapOf(
        TangoStepRung.SIGN to "= copies a filled neighbor; × gives it the other kind",
        TangoStepRung.PAIR to "Two of a kind next to each other: the empty ends get the other kind",
        TangoStepRung.SANDWICH to "A square between two of a kind gets the other kind",
        TangoStepRung.FULL_LINE to "A line with all 3 of one kind: fill the rest with the other",
        TangoStepRung.EQUAL_PAIR to "An = pair with a filled square at one end takes the other kind",
        TangoStepRung.SIGN_COUNT to "Count each empty × pair as one of each, then fill what's left",
        TangoStepRung.SPLIT_PAIR to "Two empty squares between a sun and a moon hold one of each",
        TangoStepRung.COUNT_SQUEEZE to "If one kind would use up the count and the rest then breaks a rule, the square is the other kind",
        TangoStepRung.LINE_OPTIONS to "Try every fill of a line; squares that never change are settled",
    )

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
