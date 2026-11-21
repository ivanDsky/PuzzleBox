package com.ivandsky.puzzlebox.games.sudoku.engine.trainer

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.trainer.BoardKind
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.codec.SudokuCodec
import com.ivandsky.puzzlebox.games.sudoku.sudokuPlugin
import com.ivandsky.puzzlebox.model.GameId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SudokuTechniqueWalkerTest {

    @Test
    fun `a technique is counted exactly where a practice of either kind can start`() {
        val disagree = SudokuTrainerFixtures.corpus.flatMap { (name, puzzle) ->
            val counts = SudokuTechniqueWalker.techniqueCounts(canonical(puzzle))
            SudokuTechniqueIds.all.flatMap { technique ->
                val counted = (counts[technique] ?: 0) > 0
                BoardKind.entries.mapNotNull { kind ->
                    val opens = SudokuPractice.start(puzzle, technique, kind) != null
                    "$name ${technique.value} $kind: counted=$counted opens=$opens".takeIf { counted != opens }
                }
            }
        }
        assertEquals(emptyList<String>(), disagree)
    }

    @Test
    fun `a single the stepper defers still counts when it is the easiest rung`() {
        // li:111 is a real daily where naked single is the lowest rung at some point of the walk.
        val counts = SudokuTechniqueWalker.techniqueCounts(canonical(SudokuTrainerFixtures.dailies.getValue("li:111")))
        assertEquals(EXPECTED_LI_111, counts.mapKeys { it.key.value })
        assertNotNull(SudokuPractice.start(SudokuTrainerFixtures.dailies.getValue("li:111"), TechniqueId("naked-single"), BoardKind.Focus))
    }

    @Test
    fun `a generated board counts its eliminations`() {
        val counts = SudokuTechniqueWalker.techniqueCounts(canonical(SudokuTrainerFixtures.eliminating.getValue("rnd:517")))
        assertEquals(EXPECTED_RND_517, counts.mapKeys { it.key.value })
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
        val EXPECTED_LI_111 = mapOf("last-cell" to 13, "hidden-single-box" to 12, "hidden-single-line" to 1, "naked-single" to 1)
        val EXPECTED_RND_517 = mapOf("hidden-single-box" to 3, "claiming" to 1, "naked-pair" to 1)

        fun canonical(puzzle: SudokuPuzzle): Puzzle =
            (SudokuGames.MINI.decode(SudokuCodec.encode(puzzle)) as DecodeResult.Ok).value
    }
}
