package com.ivandsky.puzzlebox.games.queensui.trainer

import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.HintReason
import com.ivandsky.puzzlebox.games.queens.engine.trainer.QueensIntro
import com.ivandsky.puzzlebox.games.queensui.hint.QueensStepCopy

/**
 * One plain caption per frame of a Queens intro demo ([QueensIntroDemo]): the position, what to look at, each square as
 * it lands, the last one with the result. A demo of N landing squares has N + 2 of them. Singles and Line lock say what is special about
 * the squares lit; every other technique lights its evidence and gives the hint's own sentence ([QueensStepCopy]) for
 * the first square, so a copy edit there shows here too (no third copy set).
 */
internal object QueensIntroCaptions {

    private val singles = TechniqueId("singles")
    private val lineLock = TechniqueId("line-lock")

    fun of(puzzle: QueensPuzzle, technique: TechniqueId, variant: Int, opening: QueensIntro.Opening): List<String> {
        val step = opening.step
        val lines = lineWord(step.unit.kind)
        val colorOfEvidence = step.evidence.firstOrNull()?.let { colorName(puzzle.regions[it.idx]) }.orEmpty()
        val first: String
        val look: String
        val last: String
        when {
            technique == singles -> {
                val crossed = step.evidence.size - 1
                look = "Look at ${unitName(step.unit)}. ${crossedCount(crossed)} One is open."
                first = "One open square left, so the queen goes there."
                last = "The queen is down."
            }
            technique == lineLock && variant == QueensIntroCopy.FILL_LINE -> {
                look = "Look at $colorOfEvidence's open squares. ${allOf(step.evidence.size)} in ${unitName(step.unit)}."
                first = "${cap(colorOfEvidence)}'s queen is in this $lines, so cross out the rest of the $lines."
                last = "The rest of the $lines is crossed out."
            }
            technique == lineLock -> {
                look = "Look at the open squares in ${unitName(step.unit)}. ${allOf(step.evidence.size)} $colorOfEvidence."
                first = "This $lines's queen is $colorOfEvidence, so cross out the rest of $colorOfEvidence."
                last = "The rest of $colorOfEvidence is crossed out."
            }
            else -> {
                look = "Look at the lit squares."
                first = QueensStepCopy.text(step)
                last = "Those squares are crossed out."
            }
        }
        return buildList {
            add(position(opening.board.queens.size))
            add(look)
            val squares = opening.landing.size
            repeat(squares) { add(if (it == 0) first else if (it == squares - 1) last else "Cross out this one too.") }
        }
    }

    private fun position(queens: Int): String = when (queens) {
        0 -> "No queens are down yet."
        1 -> "One queen is down. Many squares are crossed out."
        else -> "${cap(number(queens))} queens are down. Many squares are crossed out."
    }

    private fun crossedCount(n: Int): String =
        if (n == 1) "One square is crossed out." else "${cap(number(n))} squares are crossed out."

    /** "Both are in" / "All three are in", ready for a unit name to follow, or for "the same color" via the caller. */
    private fun allOf(n: Int): String = if (n == 2) "Both are" else "All ${number(n)} are"

    private fun lineWord(kind: HintReason.Kind): String = if (kind == HintReason.Kind.ROW) "row" else "column"

    /** "row 3", "column 2" or a color's name: the unit a reason names, counted from 1 as the board's lines are. */
    private fun unitName(reason: HintReason): String = when (reason.kind) {
        HintReason.Kind.ROW -> "row ${reason.unit + 1}"
        HintReason.Kind.COLUMN -> "column ${reason.unit + 1}"
        HintReason.Kind.REGION -> colorName(reason.unit)
    }

    /** The color of a region, by the palette's ten hues; a region past ten repeats one and is dotted. */
    private fun colorName(region: Int): String =
        (if (region >= HUES.size) "dotted " else "") + HUES[region % HUES.size]

    private fun cap(text: String): String = text.replaceFirstChar { it.uppercase() }

    private fun number(n: Int): String = WORDS.getOrNull(n) ?: n.toString()

    private val HUES = listOf("red", "orange", "amber", "green", "teal", "cyan", "blue", "indigo", "violet", "magenta")

    private val WORDS = listOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve")
}
