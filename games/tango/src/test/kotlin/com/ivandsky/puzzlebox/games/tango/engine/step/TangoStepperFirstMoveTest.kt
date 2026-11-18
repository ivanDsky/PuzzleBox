package com.ivandsky.puzzlebox.games.tango.engine.step

import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoCell.MOON
import com.ivandsky.puzzlebox.games.tango.TangoCell.SUN
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import com.ivandsky.puzzlebox.games.tango.engine.TangoBoardGenerator
import com.ivandsky.puzzlebox.games.tango.engine.TangoUniqueness
import com.ivandsky.puzzlebox.games.tango.engine.TangoVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The first-move pick order: a step the player sees at a glance (SIGN, PAIR,
 * SANDWICH) waits while a later rung forces a cell they cannot see that way. Boards here carry no givens and no
 * solution, so every filled cell is a player entry and only a rule break is a mistake.
 */
class TangoStepperFirstMoveTest {

    private val bare = TangoPuzzle(VariantId("tango/n6"), 6, emptyMap(), emptyList())

    private fun grid(vararg cells: Pair<Int, TangoCell>): List<TangoCell?> =
        MutableList<TangoCell?>(36) { null }.apply { cells.forEach { (idx, v) -> this[idx] = v } }

    /** Row 0 `SS....` forces a moon at 2 by PAIR; row 3 `SMSMS.` holds its three suns, so FULL_LINE forces a moon at 23. */
    private val pairAndFullLine = grid(0 to SUN, 1 to SUN, 18 to SUN, 19 to MOON, 20 to SUN, 21 to MOON, 22 to SUN)

    private val glance = listOf(TangoLegacyStepRules.sign, TangoLegacyStepRules.pair, TangoLegacyStepRules.sandwich)
    private val worked = listOf(
        TangoLegacyStepRules.fullLine,
        TangoEqualPairRule,
        TangoSignCountRule,
        TangoSplitPairRule,
        TangoCountSqueezeRule,
        TangoLegacyStepRules.lineOptions,
    )
    private val glanceRungs = setOf(TangoStepRung.SIGN, TangoStepRung.PAIR, TangoStepRung.SANDWICH)

    @Test
    fun `a pair waits for a full line the player has to count`() {
        val step = TangoStepper.next(bare, pairAndFullLine, null, null) as TangoStep.Deduction

        assertEquals(TangoStepRung.FULL_LINE, step.rung)
        assertEquals(23, step.idx)
        assertEquals(MOON, step.value)
    }

    @Test
    fun `a later rung never names a cell a pair already forces`() {
        // Row 0 `SS.S..`: the full line forces moons at 2, 4 and 5, and the pair already shows 2.
        val g = grid(0 to SUN, 1 to SUN, 3 to SUN)

        val step = TangoStepper.next(bare, g, null, null) as TangoStep.Deduction

        assertEquals(TangoStepRung.FULL_LINE, step.rung)
        assertEquals(4, step.idx)
    }

    @Test
    fun `and only glance steps left the cheapest one is returned`() {
        // Row 0 `SS.SMM`: the full line forces a moon at 2 as well, but the pair already shows that cell.
        val g = grid(0 to SUN, 1 to SUN, 3 to SUN, 4 to MOON, 5 to MOON)
        assertEquals(setOf(2), worked.flatMap { rule -> rule.forced(bare, g) }.mapTo(HashSet()) { it.idx })

        val step = TangoStepper.next(bare, g, null, null) as TangoStep.Deduction

        assertEquals(TangoStepRung.PAIR, step.rung)
        assertEquals(TangoStepper.next(bare, g, null, null), step)
    }

    @Test
    fun `a wrong entry still comes first`() {
        val g = pairAndFullLine.toMutableList().apply { this[2] = SUN }

        assertEquals(TangoStep.Mistake(0, TangoVerdict.Reason.ThreeInARow), TangoStepper.next(bare, g, null, null))
    }

    @Test
    fun `re-running boards names only solution values and a glance step only when nothing else is forced`() {
        val boards = (0L until 20L).map { TangoBoardGenerator.boards(6, it).minimal } + TangoBoardGenerator.boards(8, 1).minimal
        var ahead = 0
        for (puzzle in boards) {
            val solution = requireNotNull(TangoUniqueness.solve(puzzle))
            val g = MutableList(puzzle.cellCount) { puzzle.givens[it] }
            var last: Int? = null
            var step = TangoStepper.next(puzzle, g, last, solution)
            repeat(puzzle.cellCount) {
                val seen = glance.flatMap { it.forced(puzzle, g) }.mapTo(HashSet()) { it.idx }
                val unseen = worked.flatMap { it.forced(puzzle, g) }.filter { it.idx !in seen }
                val (idx, value) = when (val now = step) {
                    is TangoStep.Deduction -> {
                        if (now.rung in glanceRungs) assertEquals("glance step ${now.rung} at ${now.idx}", emptyList<TangoStep.Deduction>(), unseen)
                        else assertTrue("${now.rung} names glance cell ${now.idx}", now.idx !in seen)
                        if (now.rung !in glanceRungs && seen.isNotEmpty()) ahead++
                        now.idx to now.value
                    }
                    is TangoStep.Reveal -> now.idx to now.value
                    else -> return@repeat
                }
                assertEquals(null, g[idx])
                assertEquals(solution[idx], value)
                g[idx] = value
                last = idx
                step = TangoStepper.next(puzzle, g, last, solution)
            }
            assertEquals(TangoStep.Solved, step)
        }
        assertTrue("no board ever stepped past a glance step", ahead > 0)
    }
}
