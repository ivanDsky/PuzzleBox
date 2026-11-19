package com.ivandsky.puzzlebox.games.queens.engine.trainer

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensBoard
import com.ivandsky.puzzlebox.games.queens.QueensDefinition
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.QueensTestSupport
import com.ivandsky.puzzlebox.games.queens.codec.QueensCodec
import com.ivandsky.puzzlebox.games.queens.engine.hints.step.QueensStep
import com.ivandsky.puzzlebox.games.queens.engine.hints.step.QueensStepper
import com.ivandsky.puzzlebox.games.queens.queensPlugin
import com.ivandsky.puzzlebox.games.queens.queensVariantId
import com.ivandsky.puzzlebox.model.GameId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class QueensTechniqueWalkerTest {

    @Test
    fun `the walk takes the stepper's own steps with auto-cross on and ends solved`() {
        for ((name, puzzle) in dailies) {
            val walk = QueensTechniqueWalker.walk(puzzle)
            val rerun = rerun(puzzle)
            assertEquals(name, rerun, walk.map { it.boardBefore to it.step })
            assertEquals(name, QueensStep.Solved, walk.last().step)
            walk.forEach { w ->
                val expected = (w.step as? QueensStep.Deduction)?.unit?.rung?.trainerId
                assertEquals("$name ${w.step}", expected, w.technique)
            }
        }
    }

    @Test
    fun `counts are the rerun's deductions per technique id, the attack rung and reveals skipped`() {
        for ((name, puzzle) in dailies) {
            val expected = countsOf(rerun(puzzle).map { it.second })
            assertTrue(name, expected.isNotEmpty())
            assertEquals(name, expected, QueensTechniqueWalker.techniqueCounts(canonical(puzzle)))
        }
    }

    @Test
    fun `the plugin carries the walker and counts from the canonical puzzle`() {
        val walker = queensPlugin.techniqueWalker
        assertNotNull(walker)
        for ((name, puzzle) in dailies) {
            assertEquals(name, countsOf(rerun(puzzle).map { it.second }), walker!!.techniqueCounts(canonical(puzzle)))
        }
    }

    @Test
    fun `the walk covers the look-ahead and pigeonhole techniques`() {
        val seen = dailies.values.flatMapTo(HashSet()) { QueensTechniqueWalker.techniqueCounts(canonical(it)).keys }
        listOf("singles", "line-lock", "pigeonhole-regions", "pigeonhole-lines", "short-look-ahead", "long-look-ahead")
            .forEach { assertTrue(it, TechniqueId(it) in seen) }
    }

    @Test
    fun `foreign, undecodable and unsolved puzzles count nothing`() {
        val real = dailies.getValue("real-916")
        val bytes = QueensCodec.encode(real)
        val variant = queensVariantId(real.size)
        assertEquals(emptyMap<TechniqueId, Int>(), QueensTechniqueWalker.techniqueCounts(Puzzle(GameId("zip"), variant, QueensCodec.VERSION, bytes)))
        assertEquals(emptyMap<TechniqueId, Int>(), QueensTechniqueWalker.techniqueCounts(Puzzle(QueensDefinition.id, variant, QueensCodec.VERSION, byteArrayOf(1, 2, 3))))
        assertEquals(emptyMap<TechniqueId, Int>(), QueensTechniqueWalker.techniqueCounts(canonical(real.copy(solution = null))))
        val broken = real.copy(solution = (0 until real.size).map(::Cell))
        assertEquals(emptyMap<TechniqueId, Int>(), QueensTechniqueWalker.techniqueCounts(canonical(broken)))
    }

    private companion object {
        val dailies: Map<String, QueensPuzzle> by lazy {
            mapOf(
                "real-916" to QueensTestSupport.daily("queens-daily-real-916.json"),
                "unique" to QueensTestSupport.daily("queens-daily-unique.json"),
                "sample" to QueensTestSupport.daily("queens-daily-sample.json"),
            )
        }

        fun canonical(puzzle: QueensPuzzle): Puzzle =
            (QueensDefinition.decode(QueensCodec.encode(puzzle)) as DecodeResult.Ok).value

        fun countsOf(steps: List<QueensStep>): Map<TechniqueId, Int> =
            steps.filterIsInstance<QueensStep.Deduction>().mapNotNull { it.unit.rung.trainerId }.groupingBy { it }.eachCount()

        /** A direct stepper replay (the `QueensStepperTest.rerun` pattern, auto-cross on): each step with the board it was asked on. */
        fun rerun(puzzle: QueensPuzzle): List<Pair<QueensBoard, QueensStep>> {
            val solution = puzzle.solution!!.toSet()
            var board = QueensBoard()
            var last: Cell? = null
            val steps = mutableListOf<Pair<QueensBoard, QueensStep>>()
            repeat(puzzle.cellCount * 2) {
                val step = QueensStepper.next(puzzle, board, autoCross = true, lastMove = last, solution = solution)
                steps += board to step
                when (step) {
                    QueensStep.Solved -> return steps
                    is QueensStep.Deduction -> when (val r = step.result) {
                        is QueensStep.Deduction.Result.Cross -> {
                            board = board.copy(manual = board.manual + r.cells)
                            last = r.cells.maxBy { it.idx }
                        }
                        is QueensStep.Deduction.Result.LastCell -> {
                            board = board.copy(queens = board.queens + r.cell)
                            last = r.cell
                        }
                    }
                    is QueensStep.Reveal -> {
                        board = board.copy(manual = board.manual + step.cell)
                        last = step.cell
                    }
                    is QueensStep.Mistake, QueensStep.Unavailable -> fail("replay produced $step on $board")
                }
            }
            throw AssertionError("replay did not finish")
        }
    }
}
