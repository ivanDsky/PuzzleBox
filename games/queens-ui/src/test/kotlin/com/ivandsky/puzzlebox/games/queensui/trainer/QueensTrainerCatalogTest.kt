package com.ivandsky.puzzlebox.games.queensui.trainer

import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.HintReason
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.QueensLadder
import com.ivandsky.puzzlebox.games.queens.engine.hints.step.QueensStep
import com.ivandsky.puzzlebox.games.queensui.hint.QueensStepCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QueensTrainerCatalogTest {

    private val rungs = QueensLadder.Stepwise.rungs.filter { it.trainerId != null }

    @Test
    fun `the catalog lists the Stepwise techniques in the stepper's order`() {
        assertEquals(rungs.map { it.trainerId }, QueensTrainerFixtures.trainer.catalog().map { it.id })
        assertEquals(
            listOf(
                "Singles", "Line lock", "Shared squares", "Pigeonhole (regions)", "Pigeonhole (rows and columns)",
                "Leftover queen", "Squeeze", "Count squeeze", "Short look-ahead", "Long look-ahead",
            ),
            QueensTrainerFixtures.trainer.catalog().map { it.name },
        )
    }

    @Test
    fun `every line is the hint's sentence for that rung, none blank`() {
        val catalog = QueensTrainerFixtures.trainer.catalog()
        for ((rung, technique) in rungs.zip(catalog)) {
            assertTrue(technique.id.value, technique.line.isNotBlank())
            assertEquals(technique.id.value, QueensStepCopy.technique(rung.captionKey), technique.line)
        }
    }

    @Test
    fun `a rung whose hint sentence names no unit is its line word for word`() {
        val catalog = QueensTrainerFixtures.trainer.catalog().associateBy { it.id }
        var shared = 0
        for (rung in rungs) {
            val hints = HintReason.Kind.entries.map { kind ->
                QueensStepCopy.text(QueensStep.Deduction(HintReason(kind, 0, rung), emptySet(), QueensStep.Deduction.Result.LastCell(Cell(0))))
            }
            if (hints.distinct().size == 1) {
                assertEquals(rung.captionKey, hints.first(), catalog.getValue(rung.trainerId!!).line)
                shared++
            }
        }
        assertTrue("some rungs name no unit", shared > 0)
    }
}
