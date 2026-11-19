package com.ivandsky.puzzlebox.games.sudoku.engine.trainer

import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuBoard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SudokuSnippetsTest {

    private val techniques = SudokuTechniqueIds.all

    @Test
    fun `every cut is the walk's own board and step just before a step of that technique, in walk order`() {
        var cutsSeen = 0
        for ((name, puzzle) in SudokuTrainerFixtures.all) {
            for (technique in techniques) {
                val expected = walkOf(puzzle, technique)
                val cuts = SudokuSnippets.cut(puzzle, technique, count = Int.MAX_VALUE)
                assertEquals("$name ${technique.value}", expected, cuts.map { Triple(it.boardBefore, it.struckBefore, it.step) })
                cuts.forEach { cut ->
                    assertEquals("$name ${technique.value}", technique, SudokuTechniqueIds.of(cut.step))
                    assertTrue(
                        "$name ${technique.value}: the board holds the givens and nothing the solution does not",
                        cut.boardBefore.cells.indices.all { cut.boardBefore.digitAt(it) == 0 || cut.boardBefore.digitAt(it) == puzzle.solution[it] } &&
                            puzzle.givens.indices.all { puzzle.givens[it] == 0 || cut.boardBefore.digitAt(it) == puzzle.givens[it] },
                    )
                }
                cutsSeen += cuts.size
            }
        }
        assertTrue(cutsSeen > 0)
    }

    @Test
    fun `a technique the puzzle never uses cuts nothing`() {
        var unused = 0
        for ((name, puzzle) in SudokuTrainerFixtures.dailies) {
            for (technique in techniques.filter { walkOf(puzzle, it).isEmpty() }) {
                assertEquals("$name ${technique.value}", emptyList<SudokuSnippetCut>(), SudokuSnippets.cut(puzzle, technique, count = 5))
                unused++
            }
        }
        assertTrue("a daily leaves a technique unused", unused > 0)
    }

    @Test
    fun `count caps the cuts and spreads them from the first target step to the last`() {
        val (name, puzzle, technique) = SudokuTrainerFixtures.all.entries
            .flatMap { (name, puzzle) -> techniques.map { Triple(name, puzzle, it) } }
            .maxBy { (_, puzzle, technique) -> walkOf(puzzle, technique).size }
        val all = walkOf(puzzle, technique)
        assertTrue("$name ${technique.value} has steps to spread: ${all.size}", all.size >= 4)

        fun picked(count: Int) = SudokuSnippets.cut(puzzle, technique, count).map { Triple(it.boardBefore, it.struckBefore, it.step) }

        assertEquals(emptyList<Triple<*, *, *>>(), picked(0))
        assertEquals(listOf(all.first()), picked(1))
        assertEquals(listOf(all.first(), all.last()), picked(2))
        assertEquals(listOf(all.first(), all[all.lastIndex / 2], all.last()), picked(3))
        assertEquals(all, picked(all.size))
        assertEquals(all, picked(all.size + 7))
        val three = picked(3)
        assertEquals("a spread never repeats a step", three, three.distinct())
        assertEquals("the same puzzle cuts the same boards", three, picked(3))
        (1 until all.size).forEach { count -> assertEquals("count $count", count, picked(count).size) }
    }

    @Test
    fun `a cut before an elimination carries the strikes the walk had made`() {
        val puzzle = SudokuTrainerFixtures.eliminating.getValue("rnd:1411")
        val cuts = SudokuSnippets.cut(puzzle, TechniqueId("pointing"), count = 10)
        assertEquals(3, cuts.size)
        assertEquals(emptyMap<Int, Int>(), cuts.first().struckBefore)
        assertTrue("a later pointing follows an earlier strike", cuts.drop(1).any { it.struckBefore.isNotEmpty() })
    }

    @Test
    fun `an unknown technique and a stored solution that is not the puzzle's cut nothing`() {
        val puzzle = SudokuTrainerFixtures.dailies.getValue("li:1")
        val id = TechniqueId("last-cell")
        assertTrue(SudokuSnippets.cut(puzzle, id, 3).isNotEmpty())

        assertEquals(emptyList<SudokuSnippetCut>(), SudokuSnippets.cut(puzzle, TechniqueId("no-such-technique"), 3))
        val other = puzzle.copy(solution = puzzle.solution.map { it % puzzle.geometry.size + 1 })
        assertEquals(emptyList<SudokuSnippetCut>(), SudokuSnippets.cut(other, id, 3))
    }

    @Test
    fun `a negative count is refused`() {
        try {
            SudokuSnippets.cut(SudokuTrainerFixtures.dailies.getValue("li:1"), TechniqueId("last-cell"), -1)
            fail("a negative count must be refused")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!, "-1" in expected.message!!)
        }
    }

    private fun walkOf(puzzle: SudokuPuzzle, technique: TechniqueId): List<Triple<SudokuBoard, Map<Int, Int>, com.ivandsky.puzzlebox.games.sudoku.engine.hints.step.SudokuStep>> =
        SudokuTechniqueWalker.walk(puzzle).filter { it.technique == technique }.map { Triple(it.boardBefore, it.struckBefore, it.step) }
}
