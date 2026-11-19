package com.ivandsky.puzzlebox.games.sudoku.engine.trainer

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.games.sudoku.SudokuBoard
import com.ivandsky.puzzlebox.games.sudoku.SudokuCell
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.codec.SudokuCodec
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.step.SudokuStep
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.step.SudokuStepper
import com.ivandsky.puzzlebox.games.sudoku.sudokuPlugin
import com.ivandsky.puzzlebox.model.GameId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SudokuTechniqueWalkerTest {

    @Test
    fun `the walk takes the stepper's own steps and ends solved`() {
        for ((name, puzzle) in SudokuTrainerFixtures.all) {
            val walk = SudokuTechniqueWalker.walk(puzzle)
            assertEquals(name, rerun(puzzle), walk.map { Triple(it.boardBefore, it.struckBefore, it.step) })
            assertEquals(name, SudokuStep.Solved, walk.last().step)
            walk.forEach { assertEquals("$name ${it.step}", SudokuTechniqueIds.of(it.step), it.technique) }
        }
    }

    @Test
    fun `counts are the rerun's steps per technique id, nothing for the end`() {
        for ((name, puzzle) in SudokuTrainerFixtures.all) {
            val expected = rerun(puzzle).mapNotNull { SudokuTechniqueIds.of(it.third) }.groupingBy { it }.eachCount()
            assertTrue(name, expected.isNotEmpty())
            assertEquals(name, expected, SudokuTechniqueWalker.techniqueCounts(canonical(puzzle)))
        }
    }

    @Test
    fun `the fixtures reach all eight techniques`() {
        val seen = SudokuTrainerFixtures.all.values.flatMapTo(HashSet()) { SudokuTechniqueWalker.techniqueCounts(canonical(it)).keys }
        assertEquals(SudokuTechniqueIds.all.toSet(), seen)
    }

    @Test
    fun `the Mini plugin carries the walker, the classic plugin none`() {
        val walker = sudokuPlugin(SudokuGames.MINI).techniqueWalker
        assertNotNull(walker)
        assertNull(sudokuPlugin(SudokuGames.CLASSIC).techniqueWalker)
        for ((name, puzzle) in SudokuTrainerFixtures.all) {
            assertEquals(name, SudokuTechniqueWalker.techniqueCounts(canonical(puzzle)), walker!!.techniqueCounts(canonical(puzzle)))
        }
    }

    @Test
    fun `foreign, undecodable and wrongly solved puzzles count nothing`() {
        val real = SudokuTrainerFixtures.dailies.values.first()
        val bytes = SudokuCodec.encode(real)
        val none = emptyMap<TechniqueId, Int>()
        assertEquals(none, SudokuTechniqueWalker.techniqueCounts(Puzzle(GameId("zip"), real.variant.id, SudokuCodec.VERSION, bytes)))
        assertEquals(none, SudokuTechniqueWalker.techniqueCounts(Puzzle(SudokuGames.MINI.id, real.variant.id, SudokuCodec.VERSION, byteArrayOf(1, 2, 3))))
        val otherSolution = real.copy(solution = real.solution.map { it % real.geometry.size + 1 })
        assertEquals(none, SudokuTechniqueWalker.techniqueCounts(canonical(otherSolution)))
    }

    private companion object {
        fun canonical(puzzle: SudokuPuzzle): Puzzle =
            (SudokuGames.MINI.decode(SudokuCodec.encode(puzzle)) as DecodeResult.Ok).value

        /** A direct stepper replay as play drives it, written out here with the `known` accumulation. */
        fun rerun(puzzle: SudokuPuzzle): List<Triple<SudokuBoard, Map<Int, Int>, SudokuStep>> {
            var board = SudokuBoard.of(puzzle)
            var known = emptyMap<Int, Int>()
            var last: Int? = null
            val steps = mutableListOf<Triple<SudokuBoard, Map<Int, Int>, SudokuStep>>()
            repeat(puzzle.geometry.cellCount + 40) {
                val step = SudokuStepper.next(puzzle, board, last, known)
                steps += Triple(board, known, step)
                when (step) {
                    SudokuStep.Solved -> return steps
                    is SudokuStep.Mistake -> fail("replay produced $step")
                    is SudokuStep.Single -> {
                        board = board.with(step.place.cell, SudokuCell.Entry(step.place.digit))
                        last = step.place.cell
                    }
                    is SudokuStep.Reveal -> {
                        board = board.with(step.cell, SudokuCell.Entry(step.digit))
                        last = null
                    }
                    is SudokuStep.Elimination -> {
                        val merged = known.toMutableMap()
                        for ((cell, mask) in step.struck) merged[cell] = (merged[cell] ?: 0) or mask
                        known = merged
                    }
                }
            }
            throw AssertionError("replay did not finish")
        }
    }
}
