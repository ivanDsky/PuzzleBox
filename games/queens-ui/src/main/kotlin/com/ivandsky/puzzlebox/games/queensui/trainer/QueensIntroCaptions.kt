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

    /** The two look-aheads, whose lit squares are the queens that would follow and the line left with no room. */
    private val lookAheads = setOf(TechniqueId("short-look-ahead"), TechniqueId("long-look-ahead"))

    /** The techniques whose lit squares are all of one row, column or color's open squares. */
    private val ownerOfLit = setOf(TechniqueId("shared-squares"), TechniqueId("squeeze"))
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
                look = "Look at ${unitSquares(step.unit)}. ${crossedCount(crossed)} One is open."
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
                first = "This $lines's queen is $colorOfEvidence, so cross out the rest of the $colorOfEvidence squares."
                last = "The rest of the $colorOfEvidence squares are crossed out."
            }
            technique in lookAheads -> {
                look = "Try a queen on the marked square. The lit squares show what follows."
                first = "If a queen stood here, these queens would follow, and ${unitName(step.unit)} would have no room."
                last = "Those squares are crossed out."
            }
            else -> {
                look = lit(step.evidence.size, owner = if (technique in ownerOfLit) possessive(step.unit) else null)
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

    /**
     * What the lit squares of a step are, in one sentence that counts them: "The lit squares are green's last open
     * squares." when the step lights one [owner]'s squares, a plain description when it lights several lines' squares.
     */
    internal fun lit(count: Int, owner: String?): String = when {
        owner != null && count == 1 -> "The lit square is $owner only open square."
        owner != null -> "The lit squares are $owner last open squares."
        count == 1 -> "The lit square is the open square this move rests on."
        else -> "The lit squares are the open squares this move rests on."
    }

    private fun position(queens: Int): String = when (queens) {
        0 -> "No queens are down yet."
        1 -> "One queen is down. Many squares are crossed out."
        else -> "${cap(number(queens))} queens are down. Many squares are crossed out."
    }

    private fun crossedCount(n: Int): String =
        if (n == 1) "One is crossed out." else "${cap(number(n))} are crossed out."

    /** "Both are in" / "All three are in", ready for a unit name to follow, or for "the same color" via the caller. */
    private fun allOf(n: Int): String = if (n == 2) "Both are" else "All ${number(n)} are"

    private fun lineWord(kind: HintReason.Kind): String = if (kind == HintReason.Kind.ROW) "row" else "column"

    /** "row 3", "column 2" or a color's name: the unit a reason names, counted from 1 as the board's lines are. */
    private fun unitName(reason: HintReason): String = when (reason.kind) {
        HintReason.Kind.ROW -> "row ${reason.unit + 1}"
        HintReason.Kind.COLUMN -> "column ${reason.unit + 1}"
        HintReason.Kind.REGION -> colorName(reason.unit)
    }

    /** "the red squares", "row 3": what to look at, a color being a group of squares and not a thing to look at. */
    private fun unitSquares(reason: HintReason): String =
        if (reason.kind == HintReason.Kind.REGION) "the ${colorName(reason.unit)} squares" else unitName(reason)

    /** "green's", "row 3's": the possessive of [unitName], ready for "only open square" or "last open squares". */
    private fun possessive(reason: HintReason): String = "${unitName(reason)}'s"

    /** The color of a region, by the palette's ten hues; a region past ten repeats one and is dotted. */
    private fun colorName(region: Int): String =
        (if (region >= HUES.size) "dotted " else "") + HUES[region % HUES.size]

    private fun cap(text: String): String = text.replaceFirstChar { it.uppercase() }

    private fun number(n: Int): String = WORDS.getOrNull(n) ?: n.toString()

    private val HUES = listOf("red", "orange", "amber", "green", "teal", "cyan", "blue", "indigo", "violet", "magenta")

    private val WORDS = listOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve")
}
