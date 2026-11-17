package com.ivandsky.puzzlebox.games.queensui.hint

import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.HintReason
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.QueensLadder
import com.ivandsky.puzzlebox.games.queens.engine.hints.step.QueensStep
import com.ivandsky.puzzlebox.games.queens.engine.hints.step.QueensStep.Deduction.Result
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueensStepCopyTest {

    private val deductions: List<QueensStep> = QueensLadder.Stepwise.rungs.flatMap { rung ->
        HintReason.Kind.entries.map { QueensStep.Deduction(HintReason(it, 0, rung), emptySet(), Result.LastCell(Cell(0))) }
    }

    private val mistakes = QueensStep.Mistake.Kind.entries.map { QueensStep.Mistake(Cell(0), it, null) }
    private val reveals = listOf(QueensStep.Reveal(Cell(0)))

    @Test
    fun `every text opens with a name and a colon and fits the budget`() {
        for (step in deductions + mistakes + reveals) {
            val text = QueensStepCopy.text(step)
            assertTrue(text, text.indexOf(':') in 1..24)
            assertTrue("${text.length}: $text", text.length <= BUDGET)
        }
    }

    @Test
    fun `copy names the unit it is about`() {
        fun text(kind: HintReason.Kind) = QueensStepCopy.text(
            QueensStep.Deduction(HintReason(kind, 0), emptySet(), Result.LastCell(Cell(0))),
        )

        assertTrue(text(HintReason.Kind.ROW).contains("row"))
        assertTrue(text(HintReason.Kind.COLUMN).contains("column"))
        assertTrue(text(HintReason.Kind.REGION).contains("color"))
    }

    @Test
    fun `a mistake callout says only what is wrong, with no second-press instruction`() {
        for (step in mistakes) {
            val text = QueensStepCopy.text(step)
            assertTrue(text, text.startsWith("Wrong"))
            assertFalse(text, text.contains("hint", ignoreCase = true))
            assertFalse(text, text.contains("remove", ignoreCase = true))
        }
    }

    @Test
    fun `a solved or unavailable step has no callout`() {
        for (step in listOf(QueensStep.Solved, QueensStep.Unavailable)) {
            val thrown = runCatching { QueensStepCopy.text(step) }.exceptionOrNull()
            assertEquals(IllegalStateException::class, thrown?.let { it::class })
        }
    }

    // HintPolish (#1174): the plain set keeps each sentence and drops the technique name in front of it.

    private val everyStep = deductions + mistakes + reveals

    /** Every opening a legacy line can have: the deduction names and the mistake labels. */
    private val bannedOpenings = listOf(
        "Last cell", "Line lock", "Shared squares", "Pigeonhole", "Leftover", "Squeeze", "Count squeeze", "Look-ahead",
        "Long look-ahead", "Wrong queen", "Wrong ×", "No simple step here",
    )

    @Test
    fun `no plain text opens with a technique name, and each fits the budget`() {
        for (step in everyStep) {
            val text = QueensStepCopy.text(step, plain = true)
            for (name in bannedOpenings) assertFalse("$text opens with $name", text.startsWith("$name:", ignoreCase = true))
            assertFalse(text, '\n' in text)
            assertTrue("${text.length}: $text", text.length <= BUDGET)
        }
    }

    @Test
    fun `plain deductions are pinned for one rung per unit kind`() {
        fun plain(rungId: String, kind: HintReason.Kind): String {
            val rung = QueensLadder.Stepwise.rungs.first { it.captionKey == "queens.hint.rung.$rungId" }
            return QueensStepCopy.text(QueensStep.Deduction(HintReason(kind, 0, rung), emptySet(), Result.LastCell(Cell(0))), plain = true)
        }

        assertEquals("A color fits one row only, so the rest of that row is out.", plain("line-lock", HintReason.Kind.ROW))
        assertEquals("This color has one open square left, so the queen goes there.", plain("singles", HintReason.Kind.REGION))
        assertEquals("A queen here would block every open square of a column.", plain("squeeze", HintReason.Kind.COLUMN))
        assertEquals("These colors use up these lines, so the rest are out.", plain("pigeonhole", HintReason.Kind.ROW))
    }

    @Test
    fun `plain mistakes and the reveal are pinned`() {
        fun plain(kind: QueensStep.Mistake.Kind) = QueensStepCopy.text(QueensStep.Mistake(Cell(0), kind, null), plain = true)

        assertEquals("This queen is not in the solution.", plain(QueensStep.Mistake.Kind.OFF_SOLUTION))
        assertEquals("These two queens clash.", plain(QueensStep.Mistake.Kind.RULE_BREAK))
        assertEquals("This × is wrong: a queen belongs on this square.", plain(QueensStep.Mistake.Kind.WRONG_CROSS))
        assertEquals(
            "This square can't hold a queen, though no simple step shows why.",
            QueensStepCopy.text(QueensStep.Reveal(Cell(0)), plain = true),
        )
    }

    @Test
    fun `flag off keeps today's line for a rung`() {
        val step = deductions.first()
        assertEquals(QueensStepCopy.text(step), QueensStepCopy.text(step, plain = false))
        val lock = QueensStep.Deduction(
            HintReason(HintReason.Kind.ROW, 0, QueensLadder.Stepwise.rungs.first { it.captionKey == "queens.hint.rung.line-lock" }),
            emptySet(), Result.LastCell(Cell(0)),
        )
        assertEquals("Line lock: a color fits one row only, so the rest of it is out.", QueensStepCopy.text(lock, plain = false))
    }

    private companion object {
        /** The longest handoff example, until 06's golden measures a real two-line fit at 360dp (SC10). */
        const val BUDGET = 70
    }
}
