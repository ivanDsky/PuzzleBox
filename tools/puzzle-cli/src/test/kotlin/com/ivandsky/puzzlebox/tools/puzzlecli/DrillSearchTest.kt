package com.ivandsky.puzzlebox.tools.puzzlecli

import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.trainer.GeneratedDrillSet
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class DrillSearchTest {

    private val a = DrillGameFixtures.A
    private val b = DrillGameFixtures.B
    private val c = DrillGameFixtures.C

    /** A generator that hands out [puzzles] in order, then null. */
    private fun scripted(vararg puzzles: Puzzle?): Pair<DrillGenerator, () -> Int> {
        var calls = 0
        return DrillGenerator { puzzles.getOrNull(calls++) } to { calls }
    }

    private fun find(game: DrillGame, target: TechniqueId, want: Int = 5, attempts: Int = 100, taken: Set<String> = emptySet()) =
        DrillSearch.find(game, target, want, attempts, Random(1), taken)

    @Test
    fun `it keeps up to want puzzles that prove the target, each with its own steps`() {
        val found = find(DrillGameFixtures.game(), a, want = 4)
        assertEquals(4, found.size)
        for (drill in found) {
            assertEquals(a, drill.technique)
            assertEquals(drill.puzzle.payload[0].toInt(), drill.targetSteps)
            assertEquals(0, drill.puzzle.payload[1].toInt())
            assertEquals(GeneratedDrillSet.idOf(drill.puzzle.payload), drill.id)
        }
        assertEquals(4, found.map { it.id }.toSet().size)
    }

    @Test
    fun `the same seed finds the same drills and another seed does not`() {
        val game = DrillGameFixtures.game()
        val first = DrillSearch.find(game, a, 5, 100, Random(11), emptySet())
        assertEquals(first, DrillSearch.find(game, a, 5, 100, Random(11), emptySet()))
        assertTrue(first != DrillSearch.find(game, a, 5, 100, Random(12), emptySet()))
    }

    @Test
    fun `a technique the walker never finds costs exactly the attempts and keeps nothing`() {
        var calls = 0
        val game = DrillGameFixtures.game(generator = { random -> calls++; DrillGameFixtures.generator.next(random) })
        assertEquals(emptyList<GeneratedDrillSet.Drill>(), find(game, c, attempts = 40))
        assertEquals(40, calls)
    }

    @Test
    fun `a generator that finds nothing still ends the search`() {
        val (generator, calls) = scripted()
        assertEquals(emptyList<GeneratedDrillSet.Drill>(), find(DrillGameFixtures.game(generator), a, attempts = 9))
        assertEquals(9, calls())
    }

    @Test
    fun `the search stops at want without drawing more`() {
        var calls = 0
        val game = DrillGameFixtures.game(generator = { calls++; DrillGameFixtures.puzzle(1, 0, calls) })
        assertEquals(3, find(game, a, want = 3).size)
        assertEquals(3, calls)
    }

    @Test
    fun `a puzzle that repeats is kept once`() {
        val same = DrillGameFixtures.puzzle(2, 0, 7)
        val game = DrillGameFixtures.game(generator = { same })
        assertEquals(1, find(game, a, attempts = 10).size)
    }

    @Test
    fun `a puzzle already taken by another technique is passed over`() {
        val first = DrillGameFixtures.puzzle(1, 0, 1)
        val second = DrillGameFixtures.puzzle(1, 0, 2)
        val (generator, _) = scripted(first, second)
        val found = find(DrillGameFixtures.game(generator), a, taken = setOf(GeneratedDrillSet.idOf(first.payload)))
        assertEquals(listOf(second), found.map { it.puzzle })
    }

    @Test
    fun `a harder technique in the walk rejects the candidate`() {
        val withPair = DrillGameFixtures.puzzle(2, 1, 1)
        val clean = DrillGameFixtures.puzzle(2, 0, 2)
        val (generator, _) = scripted(withPair, clean)
        assertEquals(listOf(clean), find(DrillGameFixtures.game(generator), a).map { it.puzzle })
    }

    @Test
    fun `the game's own check can still turn a candidate down`() {
        val first = DrillGameFixtures.puzzle(1, 0, 1)
        val second = DrillGameFixtures.puzzle(1, 0, 2)
        val (generator, _) = scripted(first, second)
        val game = DrillGameFixtures.game(generator, accepts = { puzzle, target ->
            assertEquals(a, target)
            puzzle.payload[2].toInt() == 2
        })
        assertEquals(listOf(second), find(game, a).map { it.puzzle })
    }

    @Test
    fun `the check runs only on candidates the counts already passed`() {
        val (generator, _) = scripted(DrillGameFixtures.puzzle(0, 0, 1), DrillGameFixtures.puzzle(2, 1, 2))
        var asked = 0
        find(DrillGameFixtures.game(generator, accepts = { _, _ -> asked++ >= 0 }), a)
        assertEquals(0, asked)
    }
}
