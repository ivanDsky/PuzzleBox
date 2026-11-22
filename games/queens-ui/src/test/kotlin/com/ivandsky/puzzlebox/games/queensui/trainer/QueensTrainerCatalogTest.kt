package com.ivandsky.puzzlebox.games.queensui.trainer

import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.QueensLadder
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
                "Singles", "Line lock", "Shared squares", "Pigeonhole (colors)", "Pigeonhole (rows and columns)",
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
    fun `the technique lines are plain sentences that read with no board in sight`() {
        val lines = QueensTrainerFixtures.trainer.catalog().associate { it.id.value to it.line }

        assertEquals("When a row, column or color has one open square left, its queen goes there.", lines.getValue("singles"))
        assertEquals(
            "When a color's open squares all sit in one row or column, or a row's open squares are all one color, " +
                "cross out the rest of that row, column or color.",
            lines.getValue("line-lock"),
        )
        assertEquals(
            "A square that touches every open square of a row, column or color can't hold a queen.",
            lines.getValue("shared-squares"),
        )
        assertEquals(
            "Some colors fit in exactly as many rows or columns (or the other way round), so the rest of those lines or colors is out.",
            lines.getValue("pigeonhole-regions"),
        )
        assertEquals(
            "Some rows fit in exactly as many columns (or columns in rows), so the rest of those lines is out.",
            lines.getValue("pigeonhole-lines"),
        )
        assertEquals(
            "A queen here would leave two or more rows, columns or colors needing more queens than the lines they still reach.",
            lines.getValue("count-squeeze"),
        )
        assertTrue(lines.values.none { it.contains("ringed") || it.contains("regions") })
        assertTrue(lines.getValue("leftover-queen").contains("last queen"))
    }
}
