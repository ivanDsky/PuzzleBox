package com.ivandsky.puzzlebox.games.sudokuui.trainer

import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuSamples
import com.ivandsky.puzzlebox.games.sudoku.codec.SudokuCodec
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.step.SudokuStep
import com.ivandsky.puzzlebox.games.sudoku.engine.trainer.SudokuMoveCheck
import com.ivandsky.puzzlebox.games.sudoku.engine.trainer.SudokuSnippets
import com.ivandsky.puzzlebox.games.sudoku.engine.trainer.SudokuTechniqueIds
import com.ivandsky.puzzlebox.games.sudoku.engine.trainer.SudokuTrainerMove
import com.ivandsky.puzzlebox.games.sudokuui.hint.SudokuSpotlightMapper
import com.ivandsky.puzzlebox.gameui.trainer.Snippet
import com.ivandsky.puzzlebox.model.GameId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SudokuTrainerSnippetsTest {

    private val trainer = SudokuTrainerFixtures.trainer
    private val ids = trainer.catalog().map { it.id }

    private fun canonicals() = SudokuTrainerFixtures.all.values.map { SudokuTrainerFixtures.canonical(it) }

    @Test
    fun `the trainer is Mini Sudoku's and offers the index and the generated drill sources`() {
        assertEquals(SudokuGames.MINI.id, trainer.gameId)
        assertEquals(2, trainer.drillSources.size)
        assertTrue(trainer.drillSources[0] is SudokuIndexDrillSource)
        assertTrue(trainer.drillSources[1] is SudokuGeneratedDrillSource)
    }

    @Test
    fun `a technique the walk uses gives the boards just before its steps, and every id is reached by some fixture`() {
        val reached = mutableSetOf<TechniqueId>()
        for (puzzle in SudokuTrainerFixtures.all.values) {
            val canonical = SudokuTrainerFixtures.canonical(puzzle)
            for (id in ids) {
                val cuts = SudokuSnippets.cut(puzzle, id, 3)
                val snippets = trainer.buildSnippets(canonical, id, 3).map { it as SudokuSnippet }

                assertEquals(id.value, cuts.map { it.boardBefore }, snippets.map { it.cut.boardBefore })
                snippets.forEach { snippet ->
                    assertEquals(id, snippet.technique)
                    assertEquals(id, SudokuTechniqueIds.of(snippet.cut.step))
                    assertEquals("the move check judges against the puzzle's solution", puzzle, snippet.puzzle)
                }
                if (snippets.isNotEmpty()) reached += id
            }
        }
        assertEquals("the fixtures reach every listed technique", ids.toSet(), reached)
    }

    @Test
    fun `the count is respected`() {
        val canonical = canonicals().first()
        for (id in ids) {
            for (count in 0..3) assertTrue(trainer.buildSnippets(canonical, id, count).size <= count)
        }
        assertEquals(emptyList<Any>(), trainer.buildSnippets(canonical, ids.first(), 0))
        assertThrows(IllegalArgumentException::class.java) { trainer.buildSnippets(canonical, ids.first(), -1) }
    }

    @Test
    fun `a puzzle of another game, a classic one, an undecodable one, an unsolvable one and an unknown technique give none`() {
        val id = SudokuTechniqueIds.all.first { trainer.buildSnippets(canonicals().first(), it, 1).isNotEmpty() }
        val mini = canonicals().first()
        val queens = Puzzle(GameId("queens"), mini.variant, mini.formatVersion, mini.payload)
        val classicId = Puzzle(SudokuGames.CLASSIC.id, mini.variant, mini.formatVersion, mini.payload)
        val classic = Puzzle(SudokuGames.MINI.id, mini.variant, mini.formatVersion, SudokuCodec.encode(SudokuSamples.classic))
        val broken = Puzzle(mini.gameId, mini.variant, mini.formatVersion, byteArrayOf(1, 2, 3))
        val open = SudokuPuzzle(SudokuTrainerFixtures.all.values.first().variant, List(36) { 0 }, SudokuTrainerFixtures.all.values.first().solution)
        val ambiguous = Puzzle(SudokuGames.MINI.id, mini.variant, mini.formatVersion, SudokuCodec.encode(open))

        assertTrue(trainer.buildSnippets(mini, id, 3).isNotEmpty())
        assertEquals(emptyList<Any>(), trainer.buildSnippets(queens, id, 3))
        assertEquals("classic Sudoku never trains", emptyList<Any>(), trainer.buildSnippets(classicId, id, 3))
        assertEquals("a 9x9 board is no Mini Sudoku", emptyList<Any>(), trainer.buildSnippets(classic, id, 3))
        assertEquals(emptyList<Any>(), trainer.buildSnippets(broken, id, 3))
        assertEquals("no unique solution to judge by", emptyList<Any>(), trainer.buildSnippets(ambiguous, id, 3))
        assertEquals(emptyList<Any>(), trainer.buildSnippets(mini, TechniqueId("no-such-technique"), 3))
    }

    @Test
    fun `a snippet's Spotlight is the hint's own, with the step's roles and callout`() {
        val snippets = SudokuTrainerFixtures.snippets()
        assertTrue(snippets.any { it.placesDigit })
        assertTrue(snippets.any { !it.placesDigit })
        for (snippet in snippets) {
            val spotlight = trainer.spotlight(snippet)
            val hint = SudokuSpotlightMapper.map(snippet.cut.step, snippet.puzzle, snippet.cut.boardBefore, playId = 0)

            assertEquals(snippet.technique.value, hint.roles, spotlight.roles)
            assertEquals(snippet.technique.value, hint.callout, spotlight.callout)
            assertTrue("${snippet.technique.value}: a reveal marks some cell", spotlight.roles.isNotEmpty())
        }
    }

    @Test
    fun `a snippet's reveal reads the digit look its own board publishes`() {
        val snippet = SudokuTrainerFixtures.snippets().first()
        val first = trainer.spotlight(snippet)
        val again = trainer.spotlight(snippet)

        assertEquals("asking twice gives equal Spotlights", first, again)
        assertTrue(first.glyphs is SudokuRevealGlyphs)
        assertEquals(null, snippet.digits.digits)
    }

    @Test
    fun `a snippet's prompt names its technique and whether to place a digit or remove a note`() {
        val snippets = SudokuTrainerFixtures.snippets()
        for (snippet in snippets) {
            val name = trainer.catalog().first { it.id == snippet.technique }.name
            val expected = if (snippet.cut.step is SudokuStep.Single) "Tap the square $name fills, then pick its digit" else "Tap a square, then pick a note $name rules out"
            assertEquals(expected, snippet.prompt)
        }
        assertTrue(snippets.any { it.prompt.endsWith("pick its digit") })
        assertTrue(snippets.any { it.prompt.startsWith("Tap a square, then pick a note") })
    }

    @Test
    fun `two snippets built apart are not equal, so a round never shares a digit look`() {
        val a = SudokuTrainerFixtures.snippets().first()
        assertNotEquals(a, a.copy(digits = SudokuDigitStyleHolder()))
    }

    @Test
    fun `a snippet of another game is refused`() {
        val foreign = object : Snippet {
            override val technique = TechniqueId("sign")
            override val prompt = "foreign"
        }

        assertThrows(IllegalStateException::class.java) { trainer.spotlight(foreign) }
    }

    @Test
    fun `a single's strike-free board takes any placement the check classifies`() {
        for (snippet in SudokuTrainerFixtures.snippets().filter { it.placesDigit }) {
            val size = snippet.puzzle.geometry.size
            for (idx in 0 until snippet.puzzle.geometry.cellCount) {
                if (snippet.cut.boardBefore.digitAt(idx) != 0) continue
                for (digit in 1..size) {
                    SudokuMoveCheck.classify(
                        snippet.puzzle, snippet.cut.boardBefore, snippet.cut.struckBefore, snippet.technique, SudokuTrainerMove.Place(idx, digit),
                    )
                }
            }
        }
    }
}
