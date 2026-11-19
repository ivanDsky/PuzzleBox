package com.ivandsky.puzzlebox.games.queens.engine.trainer

import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.QueensTestSupport
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.QueensLadder
import com.ivandsky.puzzlebox.games.queens.engine.hints.step.QueensStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class QueensSnippetsTest {

    private val techniques = QueensLadder.Stepwise.rungs.mapNotNull { it.trainerId }

    @Test
    fun `every cut is the walk's own board and step just before a step of that technique, in walk order`() {
        var cutsSeen = 0
        for ((name, puzzle) in dailies) {
            val solution = puzzle.solution!!.toSet()
            for (technique in techniques) {
                val expected = walkOf(puzzle, technique)
                val cuts = QueensSnippets.cut(puzzle, technique, count = Int.MAX_VALUE)
                assertEquals("$name ${technique.value}", expected, cuts.map { it.boardBefore to it.step })
                cuts.forEach { assertTrue("$name ${technique.value}", it.boardBefore.queens.all { q -> q in solution }) }
                cutsSeen += cuts.size
            }
        }
        assertTrue("the fixtures cut at least one snippet", cutsSeen > 0)
    }

    @Test
    fun `a technique the puzzle never uses cuts nothing`() {
        var unused = 0
        for ((name, puzzle) in dailies) {
            for (technique in techniques.filter { walkOf(puzzle, it).isEmpty() }) {
                assertEquals("$name ${technique.value}", emptyList<QueensSnippetCut>(), QueensSnippets.cut(puzzle, technique, count = 5))
                unused++
            }
        }
        assertTrue("some fixture leaves a technique unused", unused > 0)
    }

    @Test
    fun `count caps the cuts and spreads them from the first target step to the last`() {
        val (name, puzzle, technique) = dailies.entries
            .flatMap { (name, puzzle) -> techniques.map { Triple(name, puzzle, it) } }
            .maxBy { (_, puzzle, technique) -> walkOf(puzzle, technique).size }
        val all = walkOf(puzzle, technique)
        assertTrue("$name ${technique.value} has steps to spread: ${all.size}", all.size >= 4)

        fun picked(count: Int) = QueensSnippets.cut(puzzle, technique, count).map { it.boardBefore to it.step }

        assertEquals(emptyList<Pair<*, *>>(), picked(0))
        assertEquals(listOf(all.first()), picked(1))
        assertEquals(listOf(all.first(), all.last()), picked(2))
        assertEquals(listOf(all.first(), all[(all.lastIndex) / 2], all.last()), picked(3))
        assertEquals(all, picked(all.size))
        assertEquals(all, picked(all.size + 7))
        val three = picked(3)
        assertEquals("a spread never repeats a step", three, three.distinct())
        assertEquals("the same puzzle cuts the same boards", three, picked(3))
        (1 until all.size).forEach { count -> assertEquals("count $count", count, picked(count).size) }
    }

    @Test
    fun `an unknown technique, a missing solution and a wrong solution cut nothing`() {
        val real = dailies.getValue("real-916")
        val singles = TechniqueId("singles")
        assertTrue(QueensSnippets.cut(real, singles, 3).isNotEmpty())

        assertEquals(emptyList<QueensSnippetCut>(), QueensSnippets.cut(real, TechniqueId("no-such-technique"), 3))
        assertEquals(emptyList<QueensSnippetCut>(), QueensSnippets.cut(real.copy(solution = null), singles, 3))
        val wrong = real.copy(solution = (0 until real.size).map(::Cell))
        assertEquals(emptyList<QueensSnippetCut>(), QueensSnippets.cut(wrong, singles, 3))
    }

    @Test
    fun `a negative count is refused`() {
        val real = dailies.getValue("real-916")
        try {
            QueensSnippets.cut(real, TechniqueId("singles"), -1)
            fail("a negative count must be refused")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!, "-1" in expected.message!!)
        }
    }

    private fun walkOf(puzzle: QueensPuzzle, technique: TechniqueId) = QueensTechniqueWalker.walk(puzzle)
        .filter { it.technique == technique }
        .map { it.boardBefore to it.step as QueensStep.Deduction }

    private companion object {
        val dailies: Map<String, QueensPuzzle> by lazy {
            mapOf(
                "real-916" to QueensTestSupport.daily("queens-daily-real-916.json"),
                "unique" to QueensTestSupport.daily("queens-daily-unique.json"),
                "sample" to QueensTestSupport.daily("queens-daily-sample.json"),
            )
        }
    }
}
