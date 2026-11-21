package com.ivandsky.puzzlebox.games.tangoui.trainer

import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import com.ivandsky.puzzlebox.games.tango.engine.step.TangoStep
import com.ivandsky.puzzlebox.games.tango.engine.step.TangoStepRung
import com.ivandsky.puzzlebox.games.tangoui.hint.TangoStepCopy

/**
 * The captions of a technique intro demo, one per frame (spec §2.7): what to look at, then why each square lands. Plain
 * words, no technique names (A1); the landing reason is the hint's own sentence ([TangoStepCopy.text]), so a copy edit
 * there shows here. Keyed by rung, so no `when` over rungs exists to hide a missing entry.
 */
internal object TangoDemoCaptions {

    /** Frame 0: the board as practice opens on it. */
    const val OPENING = "Some squares are filled in. One more can be worked out."

    private val evidence: Map<TangoStepRung, (other: String, line: String) -> String> = mapOf(
        TangoStepRung.SIGN to { _, _ -> "Look at the sign and the square beside it." },
        TangoStepRung.PAIR to { other, _ -> "Look at these two ${other}s side by side." },
        TangoStepRung.SANDWICH to { other, _ -> "Look at these two ${other}s, with one square between them." },
        TangoStepRung.FULL_LINE to { other, line -> "Look at $line. It already has all its ${other}s." },
        TangoStepRung.EQUAL_PAIR to { other, _ -> "Look at the two squares joined by =, and the $other right beside them." },
        TangoStepRung.SIGN_COUNT to { _, line -> "Look at $line. Count what its signs and filled squares leave over." },
        TangoStepRung.SPLIT_PAIR to { _, line -> "Look at $line. The two squares between a sun and a moon hold one of each." },
        TangoStepRung.COUNT_SQUEEZE to { _, line -> "Look at the empty squares in $line." },
        TangoStepRung.LINE_OPTIONS to { _, line -> "Look at $line. Work out every way to fill it." },
    )

    /** Frame 1: what the evidence of [step] is, for the square [step] settles. */
    fun evidence(step: TangoStep.Deduction): String = evidence.getValue(step.rung)(word(step.value.other), lineName(step))

    /** The first landing frame: the whole reason, in the hint's words. */
    fun reason(step: TangoStep, puzzle: TangoPuzzle): String = "${TangoStepCopy.text(step, puzzle)}."

    /** A landing frame after the first of one step: the reason stands. */
    fun again(value: TangoCell): String = "Same reason, so this one is a ${word(value)}, too."

    private fun lineName(step: TangoStep.Deduction): String =
        step.row?.let { "row ${it + 1}" } ?: "column ${(step.col ?: 0) + 1}"

    private fun word(value: TangoCell): String = if (value == TangoCell.SUN) "sun" else "moon"
}
