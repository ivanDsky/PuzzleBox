package com.ivandsky.puzzlebox.games.tango.engine.trainer

import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoEdgeSide
import com.ivandsky.puzzlebox.games.tango.TangoMarker
import com.ivandsky.puzzlebox.games.tango.TangoMarkerKind
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import com.ivandsky.puzzlebox.games.tango.TangoTestSupport
import com.ivandsky.puzzlebox.games.tango.engine.TangoUniqueness
import com.ivandsky.puzzlebox.games.tango.engine.step.TangoStep
import com.ivandsky.puzzlebox.games.tango.engine.step.TangoStepRung
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class TangoSnippetsTest {

    private val techniques = TangoStepRung.entries.map { it.trainerId }

    @Test
    fun `every cut is the walk's own grid and step just before a step of that technique, in walk order`() {
        var cutsSeen = 0
        for ((name, puzzle) in dailies) {
            val solution = TangoUniqueness.solve(puzzle)!!
            for (technique in techniques) {
                val expected = walkOf(puzzle, solution, technique)
                val cuts = TangoSnippets.cut(puzzle, solution, technique, count = Int.MAX_VALUE)
                assertEquals("$name ${technique.value}", expected, cuts.map { it.gridBefore to it.step })
                cuts.forEach { cut ->
                    assertEquals("$name ${technique.value}", technique, cut.step.rung.trainerId)
                    assertEquals("$name ${technique.value}: the target cell is empty", null, cut.gridBefore[cut.step.idx])
                    assertTrue(
                        "$name ${technique.value}: the grid holds nothing the solution does not",
                        cut.gridBefore.withIndex().all { (idx, value) -> value == null || value == solution[idx] },
                    )
                    assertTrue("$name ${technique.value}: the givens are in", puzzle.givens.all { (idx, value) -> cut.gridBefore[idx] == value })
                }
                cutsSeen += cuts.size
            }
        }
        assertTrue("the fixtures cut at least one snippet", cutsSeen > 0)
    }

    @Test
    fun `a technique the puzzle never uses cuts nothing`() {
        var unused = 0
        for ((name, puzzle) in dailies) {
            val solution = TangoUniqueness.solve(puzzle)!!
            for (technique in techniques.filter { walkOf(puzzle, solution, it).isEmpty() }) {
                assertEquals("$name ${technique.value}", emptyList<TangoSnippetCut>(), TangoSnippets.cut(puzzle, solution, technique, count = 5))
                unused++
            }
        }
        assertTrue("some fixture leaves a technique unused", unused > 0)
    }

    @Test
    fun `a step the walk could only reveal is cut for no technique`() {
        val puzzle = revealPuzzle
        val solution = TangoUniqueness.solve(puzzle)!!
        val walk = TangoTechniqueWalker.walk(puzzle, solution)
        val reveals = walk.filter { it.step is TangoStep.Reveal }
        assertTrue("the puzzle's walk reveals", reveals.isNotEmpty())

        val cuts = techniques.flatMap { TangoSnippets.cut(puzzle, solution, it, count = Int.MAX_VALUE) }

        assertEquals("one cut per deduction, none per reveal", walk.count { it.step is TangoStep.Deduction }, cuts.size)
        reveals.forEach { reveal -> assertTrue("a cut before reveal ${reveal.step}", cuts.none { it.gridBefore == reveal.gridBefore }) }
    }

    @Test
    fun `count caps the cuts and spreads them from the first target step to the last`() {
        val (name, puzzle, technique) = dailies.entries
            .flatMap { (name, puzzle) -> techniques.map { Triple(name, puzzle, it) } }
            .maxBy { (_, puzzle, technique) -> walkOf(puzzle, TangoUniqueness.solve(puzzle)!!, technique).size }
        val solution = TangoUniqueness.solve(puzzle)!!
        val all = walkOf(puzzle, solution, technique)
        assertTrue("$name ${technique.value} has steps to spread: ${all.size}", all.size >= 4)

        fun picked(count: Int) = TangoSnippets.cut(puzzle, solution, technique, count).map { it.gridBefore to it.step }

        assertEquals(emptyList<Pair<*, *>>(), picked(0))
        assertEquals(listOf(all.first()), picked(1))
        assertEquals(listOf(all.first(), all.last()), picked(2))
        assertEquals(listOf(all.first(), all[all.lastIndex / 2], all.last()), picked(3))
        assertEquals(all, picked(all.size))
        assertEquals(all, picked(all.size + 7))
        val three = picked(3)
        assertEquals("a spread never repeats a step", three, three.distinct())
        assertEquals("the same puzzle cuts the same grids", three, picked(3))
        (1 until all.size).forEach { count -> assertEquals("count $count", count, picked(count).size) }
    }

    @Test
    fun `an unknown technique and a solution that is none of the puzzle's cut nothing`() {
        val puzzle = dailies.getValue("byid-1")
        val solution = TangoUniqueness.solve(puzzle)!!
        val sign = TechniqueId("sign")
        assertTrue(TangoSnippets.cut(puzzle, solution, sign, 3).isNotEmpty())

        assertEquals(emptyList<TangoSnippetCut>(), TangoSnippets.cut(puzzle, solution, TechniqueId("no-such-technique"), 3))
        val open = (0 until puzzle.cellCount).first { !puzzle.isGiven(it) }
        val breaksARule = solution.toMutableList().also { it[open] = it[open].other }
        assertEquals("a grid that breaks a rule", emptyList<TangoSnippetCut>(), TangoSnippets.cut(puzzle, breaksARule, sign, 3))
        val breaksAGiven = solution.map { it.other }
        assertEquals("a grid that keeps every rule but drops the givens", emptyList<TangoSnippetCut>(), TangoSnippets.cut(puzzle, breaksAGiven, sign, 3))
        assertEquals("a grid of the wrong size", emptyList<TangoSnippetCut>(), TangoSnippets.cut(puzzle, solution.drop(1), sign, 3))
    }

    @Test
    fun `a negative count is refused`() {
        val puzzle = dailies.getValue("byid-1")
        try {
            TangoSnippets.cut(puzzle, TangoUniqueness.solve(puzzle)!!, TechniqueId("sign"), -1)
            fail("a negative count must be refused")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!, "-1" in expected.message!!)
        }
    }

    private fun walkOf(puzzle: TangoPuzzle, solution: List<TangoCell>, technique: TechniqueId) = TangoTechniqueWalker.walk(puzzle, solution)
        .filter { it.technique == technique }
        .map { it.gridBefore to it.step as TangoStep.Deduction }

    private companion object {
        val dailies: Map<String, TangoPuzzle> by lazy {
            mapOf(
                "byid-1" to TangoTestSupport.dailyOf("linkedin-tango-byid-1.json"),
                "byid-301" to TangoTestSupport.dailyOf("linkedin-tango-byid-301.json"),
                "byid-758" to TangoTestSupport.dailyOf("linkedin-tango-byid-758.json"),
            )
        }

        /** Byid-758 with givens and signs dropped one by one while it stayed uniquely solvable; the stepper reveals a cell on it. */
        val revealPuzzle: TangoPuzzle by lazy {
            val p = dailies.getValue("byid-758")
            TangoPuzzle(
                variant = p.variant,
                n = p.n,
                givens = mapOf(
                    1 to TangoCell.MOON, 2 to TangoCell.SUN, 4 to TangoCell.MOON, 5 to TangoCell.SUN,
                    6 to TangoCell.MOON, 10 to TangoCell.SUN, 11 to TangoCell.MOON,
                ),
                markers = listOf(
                    TangoMarker(24, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME),
                    TangoMarker(28, TangoEdgeSide.RIGHT, TangoMarkerKind.OPPOSITE),
                    TangoMarker(30, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME),
                    TangoMarker(32, TangoEdgeSide.RIGHT, TangoMarkerKind.OPPOSITE),
                ),
            )
        }
    }
}
