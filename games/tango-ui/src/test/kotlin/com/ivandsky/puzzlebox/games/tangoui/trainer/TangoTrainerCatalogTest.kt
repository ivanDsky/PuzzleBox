package com.ivandsky.puzzlebox.games.tangoui.trainer

import com.ivandsky.puzzlebox.games.tango.engine.step.TangoStepRung
import com.ivandsky.puzzlebox.games.tangoui.hint.TangoStepCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TangoTrainerCatalogTest {

    private val catalog = TangoTrainerFixtures.trainer.catalog()

    @Test
    fun `the catalog lists the nine techniques in the stepper's order`() {
        assertEquals(TangoStepRung.entries.map { it.trainerId }, catalog.map { it.id })
        assertEquals(
            listOf(
                "Sign", "Pair", "Sandwich", "Full line", "Equal pair",
                "Sign count", "Split pair", "Count squeeze", "Line options",
            ),
            catalog.map { it.name },
        )
    }

    @Test
    fun `every line is a technique-level sentence, one short line, none blank`() {
        for ((rung, technique) in TangoStepRung.entries.zip(catalog)) {
            assertEquals(technique.id.value, TangoStepCopy.technique(rung), technique.line)
            assertTrue(technique.id.value, technique.line.isNotBlank())
            assertFalse("${technique.id.value}: ${technique.line}", '\n' in technique.line)
            assertTrue("${technique.line} is ${technique.line.length} chars", technique.line.length <= 100)
        }
    }

    @Test
    fun `each line is pinned, and works with no board to point at`() {
        assertEquals(
            listOf(
                "= copies a filled neighbor; × gives it the other kind",
                "Two of a kind side by side: both ends get the other kind",
                "A square between two of a kind gets the other kind",
                "A line with all 3 of one kind: fill the rest with the other",
                "An = pair next to a filled square takes the other kind",
                "Count each empty × pair as one of each, then fill what's left",
                "Two empty squares between a sun and a moon hold one of each",
                "If one kind would use up the count and force three in a row, the square is the other kind",
                "Try every fill of a line; squares that never change are settled",
            ),
            catalog.map { it.line },
        )
    }

    @Test
    fun `no line points at a board or says cell`() {
        val pointers = listOf("this", "these", "here", "the gap", "cell")
        for (technique in catalog) {
            val padded = " ${technique.line.lowercase()} "
            for (word in pointers) assertFalse("${technique.id.value}: ${technique.line} has $word", word in padded)
        }
    }
}
