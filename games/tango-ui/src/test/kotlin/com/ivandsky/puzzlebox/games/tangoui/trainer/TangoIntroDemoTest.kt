package com.ivandsky.puzzlebox.games.tangoui.trainer

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.trainer.BoardKind
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import com.ivandsky.puzzlebox.games.tango.codec.TangoCodec
import com.ivandsky.puzzlebox.games.tango.engine.TangoUniqueness
import com.ivandsky.puzzlebox.games.tango.engine.step.TangoStepRung
import com.ivandsky.puzzlebox.games.tango.engine.trainer.TangoPractice
import com.ivandsky.puzzlebox.model.GameId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Tango technique intro (spec §2.7): its variants and the demo cut from the step a focus board opens on. */
class TangoIntroDemoTest {

    private val trainer = TangoTrainerFixtures.trainer
    private val techniques = TangoStepRung.entries.map { it.trainerId }

    private class Case(val puzzle: Puzzle, val decoded: TangoPuzzle, val solution: List<TangoCell>)

    private val cases: List<Case> = TangoTrainerFixtures.dailies.map { daily ->
        val decoded = (TangoCodec.decode(daily.payload) as DecodeResult.Ok).value
        Case(daily, decoded, checkNotNull(TangoUniqueness.solve(decoded)))
    }

    /** Every (case, technique) the demo exists for. */
    private fun demos(): List<Triple<Case, TechniqueId, TangoIntroDemo>> = cases.flatMap { case ->
        techniques.mapNotNull { id -> (trainer.demo(case.puzzle, id, 0) as TangoIntroDemo?)?.let { Triple(case, id, it) } }
    }

    @Test
    fun `every technique has one variant, titled with its catalog name, written in plain words`() {
        for (id in techniques) {
            val variants = trainer.variants(id)
            assertEquals(id.value, listOf(TangoTrainerCatalog.name(id)), variants.map { it.title })
            val variant = variants.single()
            for (text in listOf(variant.rule, variant.spotIt, variant.doIt)) assertTrue("${id.value}: $text", text.isNotBlank())
        }
    }

    @Test
    fun `a demo exists exactly when the puzzle opens a focus board for the technique`() {
        var found = 0
        for (case in cases) {
            for (id in techniques) {
                val opens = TangoPractice.opening(case.decoded, case.solution, id, BoardKind.Focus) != null
                val demo = trainer.demo(case.puzzle, id, 0)
                assertEquals(id.value, opens, demo != null)
                if (demo != null) found++
            }
        }
        assertTrue(found > 0)
        // byid-1's walk uses every technique but the split pair (TangoTrainerFixtures).
        assertNull(trainer.demo(TangoTrainerFixtures.canonical, TangoStepRung.SPLIT_PAIR.trainerId, 0))
    }

    @Test
    fun `frame 0 is the board a focus drill opens on and the last frame is that board after the whole step`() {
        for ((case, id, demo) in demos()) {
            val start = TangoPractice.start(case.decoded, case.solution, id, BoardKind.Focus)!!
            val step = TangoPractice.opening(case.decoded, case.solution, id, BoardKind.Focus)!!.step
            assertEquals(id.value, start, demo.frames.first().grid)
            val after = start.toMutableList().also { grid -> step.squares.forEach { grid[it.idx] = it.value } }
            assertEquals(id.value, after, demo.frames.last().grid)
            assertTrue(id.value, after.count { it != null } > start.count { it != null })
        }
    }

    @Test
    fun `a demo is the opening, the evidence, then one frame per square, each with a caption`() {
        for ((case, id, demo) in demos()) {
            val squares = TangoPractice.opening(case.decoded, case.solution, id, BoardKind.Focus)!!.step.squares
            assertEquals(id.value, squares.size + 2, demo.frames.size)
            assertEquals(id.value, demo.frames.size, demo.captions.size)
            assertTrue(id.value, demo.captions.all { it.isNotBlank() })
            assertEquals(id.value, 0, demo.variant)
            assertNull(id.value, demo.frames[0].spotlight)
            assertEquals(id.value, demo.frames[0].grid, demo.frames[1].grid)
            // One square lands per frame after the evidence, in the step's own order.
            for ((at, square) in squares.withIndex()) {
                val before = demo.frames[1 + at].grid
                val landed = demo.frames[2 + at].grid
                assertNull(id.value, before[square.idx])
                assertEquals(id.value, square.value, landed[square.idx])
            }
        }
    }

    @Test
    fun `the evidence is lit from frame 1 on, never a landing square, and nothing is lit before`() {
        for ((case, id, demo) in demos()) {
            val squares = TangoPractice.opening(case.decoded, case.solution, id, BoardKind.Focus)!!.step.squares.map { it.idx }.toSet()
            for (frame in demo.frames.drop(1)) {
                val lit = checkNotNull(frame.spotlight) { id.value }.roles
                assertTrue("${id.value}: the evidence of the step is lit", lit.isNotEmpty())
                assertTrue(id.value, lit.keys.none { it in squares })
            }
        }
    }

    @Test
    fun `only the technique's one variant has a demo, and another game's puzzle has none`() {
        val id = demos().first().second
        val daily = demos().first().first.puzzle
        assertNotNull(trainer.demo(daily, id, 0))
        assertNull(trainer.demo(daily, id, 1))
        assertNull(trainer.demo(daily, id, -1))
        val foreign = Puzzle(GameId("someone-else"), daily.variant, daily.formatVersion, daily.payload)
        assertNull(trainer.demo(foreign, id, 0))
    }
}
