package com.ivandsky.puzzlebox.games.queensui.trainer

import com.ivandsky.puzzlebox.game.trainer.BoardKind
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.engine.trainer.QueensIntro
import com.ivandsky.puzzlebox.games.queens.engine.trainer.QueensPractice
import com.ivandsky.puzzlebox.games.queensui.trainer.QueensTrainerFixtures.canonical
import com.ivandsky.puzzlebox.games.queensui.trainer.QueensTrainerFixtures.trainer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Queens intro (spec §2.7): the variants a technique writes down and the demo cut from the step that opens its Focus board. */
class QueensIntroDemoTest {

    private val lineLock = TechniqueId("line-lock")
    private val ids = QueensTrainerCatalog.techniques.map { it.id }
    private val puzzles: List<QueensPuzzle> = listOf(QueensTrainerFixtures.puzzle, QueensTrainerFixtures.unique)

    @Test
    fun `line lock has two variants, every other technique one titled with its name`() {
        for (technique in QueensTrainerCatalog.techniques) {
            val variants = trainer.variants(technique.id)
            if (technique.id == lineLock) {
                assertEquals(listOf("Fill the line", "Fill the region"), variants.map { it.title })
            } else {
                assertEquals(technique.id.value, listOf(technique.name), variants.map { it.title })
            }
            assertTrue(variants.all { it.rule.isNotBlank() && it.spotIt.isNotBlank() && it.doIt.isNotBlank() })
        }
    }

    @Test
    fun `a demo opens on the focus board, ends on the board after the step, and has a caption per frame`() {
        var demos = 0
        for (puzzle in puzzles) {
            for (technique in ids) {
                val opening = QueensIntro.opening(puzzle, technique)
                val demo = (trainer.variants(technique).indices).mapNotNull { trainer.demo(canonical(puzzle), technique, it) as QueensIntroDemo? }
                if (opening == null) {
                    assertTrue(demo.isEmpty())
                    continue
                }
                // Exactly the one variant the opening step shows has a demo on this puzzle.
                assertEquals(technique.value, 1, demo.size)
                demos++
                val shown = demo.single()
                assertEquals(QueensPractice.start(puzzle, technique, BoardKind.Focus), shown.frames.first().board)
                assertEquals(QueensPractice.after(puzzle, opening.board, opening.step), shown.frames.last().board)
                // The opening, the evidence, then one frame per square the step settles.
                assertEquals(2 + opening.landing.size, shown.frames.size)
                assertEquals(shown.frames.size, shown.captions.size)
                assertTrue(shown.captions.all { it.isNotBlank() })
                assertNull(shown.frames.first().spotlight)
                assertTrue(shown.frames[1].spotlight!!.roles.isNotEmpty())
                assertEquals(shown.frames.first().board, shown.frames[1].board)
            }
        }
        assertTrue(demos > 0)
    }

    @Test
    fun `a puzzle opens the variant its step shows and no other`() {
        // Singles has one variant, so the second index is no demo; a technique the puzzle never uses has none at all.
        assertNotNull(trainer.demo(QueensTrainerFixtures.canonical, TechniqueId("singles"), 0))
        assertNull(trainer.demo(QueensTrainerFixtures.canonical, TechniqueId("singles"), 1))
        assertNull(trainer.demo(QueensTrainerFixtures.canonical, TechniqueId("squeeze"), 0))
    }

    @Test
    fun `both line lock variants have a demo on the fixtures`() {
        for (variant in 0..1) {
            val demos = puzzles.mapNotNull { trainer.demo(canonical(it), lineLock, variant) }
            assertTrue("Line lock variant $variant", demos.isNotEmpty())
            assertTrue(demos.all { it.variant == variant })
        }
    }
}
