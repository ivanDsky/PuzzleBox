package com.ivandsky.puzzlebox.games.queensui.hint

import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.engine.hints.HintReason
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.QueensLadder
import com.ivandsky.puzzlebox.games.queens.engine.hints.step.QueensStep
import com.ivandsky.puzzlebox.games.queens.engine.hints.step.QueensStep.Deduction.Result
import com.ivandsky.puzzlebox.games.queensui.QueensRungCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueensStepCopyTest {

    private val deductions: List<QueensStep> = QueensLadder.Stepwise.rungs.flatMap { rung ->
        HintReason.Kind.entries.map { QueensStep.Deduction(rung, HintReason(it, 0), emptySet(), Result.LastCell(Cell(0))) }
    }

    private val mistakes = QueensStep.Mistake.Kind.entries.map { QueensStep.Mistake(Cell(0), it, null) }
    private val reveals = listOf(true, false).map { QueensStep.Reveal(Cell(0), it) }

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
            QueensStep.Deduction(QueensLadder.Stepwise.rungs.first { it.id == "singles" }, HintReason(kind, 0), emptySet(), Result.LastCell(Cell(0))),
        )

        assertTrue(text(HintReason.Kind.ROW).contains("row"))
        assertTrue(text(HintReason.Kind.COLUMN).contains("column"))
        assertTrue(text(HintReason.Kind.REGION).contains("color"))
    }

    @Test
    fun `copy never reuses a legacy caption`() {
        val legacy = QueensLadder.Stepwise.rungs.mapNotNull { QueensRungCopy.caption(it.captionKey) }.toSet()

        for (step in deductions) assertFalse(QueensStepCopy.text(step) in legacy)
    }

    @Test
    fun `a solved or unavailable step has no callout`() {
        for (step in listOf(QueensStep.Solved, QueensStep.Unavailable)) {
            val thrown = runCatching { QueensStepCopy.text(step) }.exceptionOrNull()
            assertEquals(IllegalStateException::class, thrown?.let { it::class })
        }
    }

    private companion object {
        /** The longest handoff example, until 06's golden measures a real two-line fit at 360dp (SC10). */
        const val BUDGET = 70
    }
}
