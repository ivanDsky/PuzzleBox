package com.ivandsky.puzzlebox.games.tangoui.trainer

import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import com.ivandsky.puzzlebox.games.tango.engine.step.TangoStep
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
    fun `every line is the hint's sentence for that rung, one short line, none blank`() {
        for ((rung, technique) in TangoStepRung.entries.zip(catalog)) {
            assertEquals(technique.id.value, TangoStepCopy.technique(rung), technique.line)
            assertTrue(technique.id.value, technique.line.isNotBlank())
            assertFalse("${technique.id.value}: ${technique.line}", '\n' in technique.line)
            assertTrue("${technique.line} is ${technique.line.length} chars", technique.line.length <= 70)
        }
    }

    @Test
    fun `each line is pinned, in the generic words a technique list can use`() {
        assertEquals(
            listOf(
                "The sign beside it makes this a sun",
                "Two moons touch, so the cells at both ends are suns",
                "This cell sits between two moons, so it is a sun",
                "This line has all its moons, so the rest are suns",
                "Joined by =, these would make three moons in a row, so both are suns",
                "Counting the signs, no moon fits here, so this is a sun",
                "The gap holds one of each, so the rest are suns",
                "The other kind here would break the line, so this is a sun",
                "All the ways to fill this line put a sun here",
            ),
            catalog.map { it.line },
        )
    }

    @Test
    fun `a line is its rung's hint sentence for a sun, with a line or an option count in generic words`() {
        val plain = TangoPuzzle(VariantId("tango/n4"), 4, emptyMap(), emptyList())
        // The two rungs whose hint names a row or a number of ways; every other hint sentence is its line word for word.
        val generic = mapOf(
            TangoStepRung.FULL_LINE to ("Row 2" to "This line"),
            TangoStepRung.LINE_OPTIONS to ("All 2 ways" to "All the ways"),
        )
        for ((rung, technique) in TangoStepRung.entries.zip(catalog)) {
            val step = TangoStep.Deduction(rung, 1, TangoCell.SUN, setOf(0, 1), row = 1, col = null, options = 2)
            val (named, any) = generic[rung] ?: ("" to "")
            val hint = if (named.isEmpty()) TangoStepCopy.text(step, plain) else TangoStepCopy.text(step, plain).replace(named, any)

            assertEquals(rung.name, hint, technique.line)
        }
    }
}
