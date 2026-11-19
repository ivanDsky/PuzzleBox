package com.ivandsky.puzzlebox.games.queensui.trainer

import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.games.queens.engine.hints.step.QueensStep
import com.ivandsky.puzzlebox.games.queens.engine.trainer.QueensSnippets
import com.ivandsky.puzzlebox.games.queensui.hint.QueensSpotlightMapper
import com.ivandsky.puzzlebox.model.GameId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QueensTrainerSnippetsTest {

    private val trainer = QueensTrainerFixtures.trainer
    private val canonical = QueensTrainerFixtures.canonical
    private val puzzle = QueensTrainerFixtures.puzzle
    private val ids = trainer.catalog().map { it.id }

    @Test
    fun `a technique the walk uses gives the boards just before its steps`() {
        var built = 0
        for (id in ids) {
            val cuts = QueensSnippets.cut(puzzle, id, 3)
            val snippets = trainer.buildSnippets(canonical, id, 3).map { it as QueensSnippet }

            assertEquals(id.value, cuts.map { it.boardBefore }, snippets.map { it.cut.boardBefore })
            snippets.forEach { snippet ->
                assertEquals(id, snippet.technique)
                assertEquals(id, snippet.cut.step.unit.rung.trainerId)
            }
            built += snippets.size
        }
        assertTrue("daily 916 needs some technique", built > 0)
    }

    @Test
    fun `a technique the puzzle never uses gives no snippet`() {
        val unused = ids.filter { QueensSnippets.cut(puzzle, it, 1).isEmpty() }

        assertTrue("daily 916 leaves a technique unused", unused.isNotEmpty())
        unused.forEach { assertEquals(it.value, emptyList<Any>(), trainer.buildSnippets(canonical, it, 5)) }
    }

    @Test
    fun `a puzzle of another game, an undecodable one, and an unknown technique give none`() {
        val singles = TechniqueId("singles")
        val zip = Puzzle(GameId("zip"), canonical.variant, canonical.formatVersion, canonical.payload)
        val broken = Puzzle(canonical.gameId, canonical.variant, canonical.formatVersion, byteArrayOf(1, 2, 3))

        assertTrue(trainer.buildSnippets(canonical, singles, 3).isNotEmpty())
        assertEquals(emptyList<Any>(), trainer.buildSnippets(zip, singles, 3))
        assertEquals(emptyList<Any>(), trainer.buildSnippets(broken, singles, 3))
        assertEquals(emptyList<Any>(), trainer.buildSnippets(canonical, TechniqueId("no-such-technique"), 3))
    }

    @Test
    fun `a snippet places a queen exactly when its step does, and its Spotlight is the hint's own`() {
        val snippets = QueensTrainerFixtures.snippets
        assertTrue(snippets.any { it.placesQueen })
        assertTrue(snippets.any { !it.placesQueen })
        for (snippet in snippets) {
            assertEquals(snippet.cut.step.result is QueensStep.Deduction.Result.LastCell, snippet.placesQueen)
            val spotlight = trainer.spotlight(snippet)
            val hint = QueensSpotlightMapper.map(snippet.cut.step, snippet.puzzle, playId = 0)
            assertEquals(hint.roles, spotlight.roles)
            assertEquals(hint.callout, spotlight.callout)
        }
    }

    @Test
    fun `a snippet's prompt names its technique and whether to place a queen or cross a square`() {
        for (snippet in QueensTrainerFixtures.snippets) {
            val name = trainer.catalog().first { it.id == snippet.technique }.name
            val verb = if (snippet.placesQueen) "Place a queen" else "Cross a square"
            assertEquals("$verb that $name proves", snippet.prompt)
        }
    }
}
