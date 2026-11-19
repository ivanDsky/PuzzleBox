package com.ivandsky.puzzlebox.games.tangoui.trainer

import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.engine.TangoUniqueness
import com.ivandsky.puzzlebox.games.tango.engine.trainer.TangoSnippets
import com.ivandsky.puzzlebox.games.tangoui.TangoPlayFixture
import com.ivandsky.puzzlebox.games.tangoui.hint.TangoSpotlightMapper
import com.ivandsky.puzzlebox.gameui.trainer.Snippet
import com.ivandsky.puzzlebox.model.GameId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TangoTrainerSnippetsTest {

    private val trainer = TangoTrainerFixtures.trainer
    private val canonical = TangoTrainerFixtures.canonical
    private val puzzle = TangoTrainerFixtures.puzzle
    private val solution = TangoUniqueness.solve(puzzle)!!
    private val ids = trainer.catalog().map { it.id }

    @Test
    fun `a technique the walk uses gives the grids just before its steps`() {
        var built = 0
        for (id in ids) {
            val cuts = TangoSnippets.cut(puzzle, solution, id, 3)
            val snippets = trainer.buildSnippets(canonical, id, 3).map { it as TangoSnippet }

            assertEquals(id.value, cuts.map { it.gridBefore }, snippets.map { it.cut.gridBefore })
            snippets.forEach { snippet ->
                assertEquals(id, snippet.technique)
                assertEquals(id, snippet.cut.step.rung.trainerId)
                assertEquals("the move check judges against the puzzle's solution", solution, snippet.solution)
            }
            built += snippets.size
        }
        assertTrue("byid-1 needs some technique", built > 0)
    }

    @Test
    fun `the daily reaches eight of the nine techniques, and the one it never uses gives no snippet`() {
        val unused = ids.filter { TangoSnippets.cut(puzzle, solution, it, 1).isEmpty() }

        assertEquals(listOf(TechniqueId("split-pair")), unused)
        unused.forEach { assertEquals(it.value, emptyList<Any>(), trainer.buildSnippets(canonical, it, 5)) }
    }

    @Test
    fun `a puzzle of another game, an undecodable one, an unsolvable one, and an unknown technique give none`() {
        val sign = TechniqueId("sign")
        val queens = Puzzle(GameId("queens"), canonical.variant, canonical.formatVersion, canonical.payload)
        val broken = Puzzle(canonical.gameId, canonical.variant, canonical.formatVersion, byteArrayOf(1, 2, 3))
        val ambiguous = TangoPlayFixture.envelope(TangoPlayFixture.ambiguous)

        assertTrue(trainer.buildSnippets(canonical, sign, 3).isNotEmpty())
        assertEquals(emptyList<Any>(), trainer.buildSnippets(queens, sign, 3))
        assertEquals(emptyList<Any>(), trainer.buildSnippets(broken, sign, 3))
        assertEquals("no unique solution to judge by", emptyList<Any>(), trainer.buildSnippets(ambiguous, sign, 3))
        assertEquals(emptyList<Any>(), trainer.buildSnippets(canonical, TechniqueId("no-such-technique"), 3))
    }

    @Test
    fun `a snippet's Spotlight is the hint's own, and it names the cell the step fills`() {
        val snippets = TangoTrainerFixtures.snippets
        assertTrue(snippets.any { it.value == TangoCell.SUN })
        assertTrue(snippets.any { it.value == TangoCell.MOON })
        for (snippet in snippets) {
            val spotlight = trainer.spotlight(snippet)
            val hint = TangoSpotlightMapper.map(snippet.cut.step, snippet.puzzle, snippet.cut.gridBefore, playId = 0)

            assertEquals(hint.roles, spotlight.roles)
            assertEquals(hint.callout, spotlight.callout)
            assertEquals(snippet.cut.step.value, snippet.value)
            assertTrue("${snippet.technique.value}: the step's cell is open", snippet.cut.gridBefore[snippet.cut.step.idx] == null)
        }
    }

    @Test
    fun `a snippet's prompt names its technique and whether to place a sun or a moon`() {
        for (snippet in TangoTrainerFixtures.snippets) {
            val name = trainer.catalog().first { it.id == snippet.technique }.name
            val kind = if (snippet.value == TangoCell.SUN) "sun" else "moon"
            assertEquals("Place a $kind that $name proves", snippet.prompt)
        }
    }

    @Test
    fun `a snippet of another game is refused`() {
        val foreign = object : Snippet {
            override val technique = TechniqueId("sign")
            override val prompt = "foreign"
        }

        assertThrows(IllegalStateException::class.java) { trainer.spotlight(foreign) }
    }
}
