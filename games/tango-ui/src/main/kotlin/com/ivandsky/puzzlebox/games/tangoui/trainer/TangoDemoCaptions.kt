package com.ivandsky.puzzlebox.games.tangoui.trainer

import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import com.ivandsky.puzzlebox.games.tango.engine.step.TangoStep
import com.ivandsky.puzzlebox.games.tango.engine.step.TangoStepRung
import com.ivandsky.puzzlebox.games.tangoui.hint.TangoStepCopy

/**
 * The captions of a technique intro demo, one per frame (spec §2.7): what to look at, then why each square lands. Plain
 * words, no technique names (A1). The landing reason is the hint's own sentence ([TangoStepCopy.text]) unless the demo
 * knows more than a lit board does: how many squares land. A hint points at one square, so a demo that lands two ends of
 * a pair, or one last square of a full line, says so here. Keyed by rung, so no `when` over rungs exists to hide an entry.
 */
internal object TangoDemoCaptions {

    /** Frame 0: the board as practice opens on it, with [landing] squares to work out. */
    fun opening(landing: Int): String =
        "Some squares are filled in. ${if (landing == 1) "One more" else "A few more"} can be worked out."

    private val evidence: Map<TangoStepRung, (step: TangoStep.Deduction, other: String, line: String) -> String> = mapOf(
        TangoStepRung.SIGN to { _, _, _ -> "Look at the sign and the square beside it." },
        // "Side by side" reads as left and right, and half the pairs are one above the other.
        TangoStepRung.PAIR to { _, other, _ -> "Look at these two ${other}s next to each other." },
        TangoStepRung.SANDWICH to { _, other, _ -> "Look at these two ${other}s, with one square between them." },
        TangoStepRung.FULL_LINE to { _, other, line -> "Look at $line. It already has all its ${other}s." },
        TangoStepRung.EQUAL_PAIR to { _, other, _ -> "Look at the two squares joined by =, and the $other at one end of them." },
        TangoStepRung.SIGN_COUNT to { step, _, line ->
            if (step.idx in step.evidence) {
                "Look at the empty = pair in $line, and count the suns and moons around it."
            } else {
                "Look at $line. Count its suns and moons, with each empty × pair as one of each."
            }
        },
        TangoStepRung.SPLIT_PAIR to { _, _, line -> "Look at $line. The two squares between a sun and a moon hold one of each." },
        TangoStepRung.COUNT_SQUEEZE to { _, _, line -> "Look at the other empty squares in $line." },
        TangoStepRung.LINE_OPTIONS to { _, _, line -> "Look at $line. Try every way to fill it." },
    )

    /** Frame 1: what the evidence of [step] is, for the square [step] settles. */
    fun evidence(step: TangoStep.Deduction): String = evidence.getValue(step.rung)(step, word(step.value.other), lineName(step))

    /** The first landing frame: the whole reason, in the hint's words, as the demo lands [landing] squares of the step. */
    fun reason(step: TangoStep, landing: Int, puzzle: TangoPuzzle): String {
        if (step is TangoStep.Deduction) specific(step, landing)?.let { return "$it." }
        return "${TangoStepCopy.text(step, puzzle)}."
    }

    /** A landing frame after the first of one step: the reason stands, and "too" only when the kind repeats. */
    fun again(value: TangoCell, previous: TangoCell): String =
        if (value == previous) "Same reason, so this one is a ${word(value)}, too." else "Same reason: this one is a ${word(value)}."

    /** The sentence of a rung whose wording depends on the squares that land or on the demo's one pair, else null. */
    private fun specific(step: TangoStep.Deduction, landing: Int): String? {
        val value = word(step.value)
        val other = word(step.value.other)
        return when (step.rung) {
            TangoStepRung.PAIR ->
                if (landing > 1) "Two ${other}s touch, so both ends are ${value}s" else null
            TangoStepRung.FULL_LINE ->
                if (landing == 1) "${lineName(step).replaceFirstChar(Char::uppercase)} has all its ${other}s, so the last empty square is a $value" else null
            TangoStepRung.COUNT_SQUEEZE ->
                "A $other here would use up the ${lineNoun(step)}'s ${other}s, so every other empty square would be a $value and break the line. So this is a $value"
            else -> null
        }
    }

    private fun lineName(step: TangoStep.Deduction): String =
        step.row?.let { "row ${it + 1}" } ?: "column ${(step.col ?: 0) + 1}"

    private fun lineNoun(step: TangoStep.Deduction): String = if (step.row != null) "row" else "column"

    private fun word(value: TangoCell): String = if (value == TangoCell.SUN) "sun" else "moon"
}
