package com.ivandsky.puzzlebox.games.tango.engine.trainer

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoDefinition
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import com.ivandsky.puzzlebox.games.tango.TangoTestSupport
import com.ivandsky.puzzlebox.games.tango.codec.TangoCodec
import com.ivandsky.puzzlebox.games.tango.engine.TangoUniqueness
import com.ivandsky.puzzlebox.games.tango.engine.step.TangoStep
import com.ivandsky.puzzlebox.games.tango.engine.step.TangoStepper
import com.ivandsky.puzzlebox.games.tango.tangoPlugin
import com.ivandsky.puzzlebox.model.GameId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class TangoTechniqueWalkerTest {

    @Test
    fun `the walk takes the stepper's own steps and ends solved`() {
        for ((name, puzzle) in dailies) {
            val solution = TangoUniqueness.solve(puzzle)!!
            val walk = TangoTechniqueWalker.walk(puzzle, solution)
            assertEquals(name, rerun(puzzle, solution), walk.map { it.gridBefore to it.step })
            assertEquals(name, TangoStep.Solved, walk.last().step)
            walk.forEach { w ->
                val expected = (w.step as? TangoStep.Deduction)?.rung?.trainerId
                assertEquals("$name ${w.step}", expected, w.technique)
            }
        }
    }

    @Test
    fun `counts are the rerun's deductions per technique id, reveals skipped`() {
        for ((name, puzzle) in dailies) {
            val expected = countsOf(rerun(puzzle, TangoUniqueness.solve(puzzle)!!).map { it.second })
            assertTrue(name, expected.isNotEmpty())
            assertEquals(name, expected, TangoTechniqueWalker.techniqueCounts(canonical(puzzle)))
        }
    }

    @Test
    fun `the plugin carries the walker and counts from the canonical puzzle`() {
        val walker = tangoPlugin.techniqueWalker
        assertNotNull(walker)
        for ((name, puzzle) in dailies) {
            val expected = countsOf(rerun(puzzle, TangoUniqueness.solve(puzzle)!!).map { it.second })
            assertEquals(name, expected, walker!!.techniqueCounts(canonical(puzzle)))
        }
    }

    @Test
    fun `the walk covers eight of the nine techniques`() {
        val seen = dailies.values.flatMapTo(HashSet()) { TangoTechniqueWalker.techniqueCounts(canonical(it)).keys }
        listOf("sign", "pair", "sandwich", "full-line", "equal-pair", "sign-count", "count-squeeze", "line-options")
            .forEach { assertTrue(it, TechniqueId(it) in seen) }
    }

    @Test
    fun `foreign, undecodable and ambiguous puzzles count nothing`() {
        val real = dailies.getValue("byid-1")
        val bytes = TangoCodec.encode(real)
        assertEquals(emptyMap<TechniqueId, Int>(), TangoTechniqueWalker.techniqueCounts(Puzzle(GameId("zip"), real.variant, TangoCodec.VERSION, bytes)))
        assertEquals(emptyMap<TechniqueId, Int>(), TangoTechniqueWalker.techniqueCounts(Puzzle(TangoDefinition.id, real.variant, TangoCodec.VERSION, byteArrayOf(1, 2, 3))))
        val ambiguous = TangoPuzzle(variant = real.variant, n = real.n, givens = mapOf(0 to TangoCell.SUN), markers = emptyList())
        assertEquals(null, TangoUniqueness.solve(ambiguous))
        assertEquals(emptyMap<TechniqueId, Int>(), TangoTechniqueWalker.techniqueCounts(canonical(ambiguous)))
    }

    private companion object {
        val dailies: Map<String, TangoPuzzle> by lazy {
            mapOf(
                "byid-1" to TangoTestSupport.dailyOf("linkedin-tango-byid-1.json"),
                "byid-301" to TangoTestSupport.dailyOf("linkedin-tango-byid-301.json"),
                "byid-758" to TangoTestSupport.dailyOf("linkedin-tango-byid-758.json"),
            )
        }

        fun canonical(puzzle: TangoPuzzle): Puzzle =
            (TangoDefinition.decode(TangoCodec.encode(puzzle)) as DecodeResult.Ok).value

        fun countsOf(steps: List<TangoStep>): Map<TechniqueId, Int> =
            steps.filterIsInstance<TangoStep.Deduction>().map { it.rung.trainerId }.groupingBy { it }.eachCount()

        /** A direct stepper replay as play drives it: each step with the grid it was asked on, the last move the previous step's cell. */
        fun rerun(puzzle: TangoPuzzle, solution: List<TangoCell>): List<Pair<List<TangoCell?>, TangoStep>> {
            var grid: List<TangoCell?> = List(puzzle.cellCount) { puzzle.givens[it] }
            var last: Int? = null
            val steps = mutableListOf<Pair<List<TangoCell?>, TangoStep>>()
            repeat(puzzle.cellCount * 2) {
                val step = TangoStepper.next(puzzle, grid, last, solution)
                steps += grid to step
                when (step) {
                    TangoStep.Solved -> return steps
                    is TangoStep.Deduction -> {
                        grid = grid.toMutableList().also { it[step.idx] = step.value }
                        last = step.idx
                    }
                    is TangoStep.Reveal -> {
                        grid = grid.toMutableList().also { it[step.idx] = step.value }
                        last = step.idx
                    }
                    is TangoStep.Mistake, TangoStep.Unavailable -> fail("replay produced $step on $grid")
                }
            }
            throw AssertionError("replay did not finish")
        }
    }
}
