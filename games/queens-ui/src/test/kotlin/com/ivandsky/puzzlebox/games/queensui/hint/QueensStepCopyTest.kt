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
    private val everyStep = deductions + mistakes + reveals

    /** Every opening a name-first line can have: the deduction names and the mistake labels. */
    private val bannedOpenings = listOf(
        "Last cell", "Line lock", "Shared squares", "Pigeonhole", "Leftover", "Squeeze", "Count squeeze", "Look-ahead",
        "Long look-ahead", "Wrong queen", "Wrong ×", "No simple step here",
    )

    @Test
    fun `no text opens with a technique name, and each fits the budget`() {
        for (step in everyStep) {
            val text = QueensStepCopy.text(step)
            for (name in bannedOpenings) assertFalse("$text opens with $name", text.startsWith("$name:", ignoreCase = true))
            assertFalse(text, '\n' in text)
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

    @Test
    fun `deductions are pinned for one rung per unit kind`() {
        fun text(rungId: String, kind: HintReason.Kind): String {
            val rung = QueensLadder.Stepwise.rungs.first { it.captionKey == "queens.hint.rung.$rungId" }
            return QueensStepCopy.text(QueensStep.Deduction(HintReason(kind, 0, rung), emptySet(), Result.LastCell(Cell(0))))
        }

        assertEquals("A color fits one row only, so the rest of that row is out.", text("line-lock", HintReason.Kind.ROW))
        assertEquals("This color has one open square left, so the queen goes there.", text("singles", HintReason.Kind.REGION))
        assertEquals(
            "A queen on any of these squares would rule out every open square of a column.",
            text("squeeze", HintReason.Kind.COLUMN),
        )
        assertEquals(
            "A queen on any of these squares would touch every open square of a color.",
            text("shared-neighbours", HintReason.Kind.REGION),
        )
        assertEquals(
            "These colors and these rows or columns use each other up, so the rest are out.",
            text("pigeonhole", HintReason.Kind.ROW),
        )
        assertEquals(
            "These rows and these columns use each other up, so the rest are out.",
            text("line-group-pigeonhole", HintReason.Kind.ROW),
        )
        assertEquals(
            "A queen on any of these squares would leave rows, columns or colors short of room.",
            text("count-squeeze", HintReason.Kind.ROW),
        )
        assertEquals(
            "A queen here forces moves that leave a row or a group of lines no room.",
            text("short-look-ahead", HintReason.Kind.ROW),
        )
    }

    @Test
    fun `no sentence says ringed, and a hint about several squares says any of these`() {
        for (rung in QueensLadder.Stepwise.rungs) {
            assertFalse(rung.captionKey, QueensStepCopy.technique(rung.captionKey).contains("ringed"))
            for (kind in HintReason.Kind.entries) {
                val text = QueensStepCopy.text(
                    QueensStep.Deduction(HintReason(kind, 0, rung), emptySet(), Result.LastCell(Cell(0))),
                )
                assertFalse(text, text.contains("ringed"))
                assertFalse(text, text.contains("row, column, or"))
            }
        }
    }

    @Test
    fun `mistakes and the reveal are pinned`() {
        fun text(kind: QueensStep.Mistake.Kind) = QueensStepCopy.text(QueensStep.Mistake(Cell(0), kind, null))

        assertEquals("This queen is not in the solution.", text(QueensStep.Mistake.Kind.OFF_SOLUTION))
        assertEquals("These two queens clash.", text(QueensStep.Mistake.Kind.RULE_BREAK))
        assertEquals("This × is wrong: a queen belongs on this square.", text(QueensStep.Mistake.Kind.WRONG_CROSS))
        assertEquals(
            "This square can't hold a queen, though no simple step shows why.",
            QueensStepCopy.text(QueensStep.Reveal(Cell(0))),
        )
    }

    private companion object {
        /** The longest handoff example, until 06's golden measures a real two-line fit at 360dp (SC10). */
        const val BUDGET = 85
    }
}
