package com.ivandsky.puzzlebox.games.strandsui

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.games.strands.StrandsDefinition
import com.ivandsky.puzzlebox.games.strands.StrandsPuzzle
import com.ivandsky.puzzlebox.games.strands.codec.StrandsCodec
import com.ivandsky.puzzlebox.gameui.TutorialSandbox
import com.ivandsky.puzzlebox.gameui.TutorialSession
import com.ivandsky.puzzlebox.gameui.TutorialState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Strands' lessons (A13/A14) are real boards: their words tile every letter once, the
 * spangram runs edge to edge, and the play registration's tutorial plays through the
 * sandbox to ready by tapping each word.
 */
class StrandsTutorialTest {

    private val steps = StrandsTutorial.steps

    private fun puzzleOf(index: Int): StrandsPuzzle =
        (StrandsCodec.decode(steps[index].puzzle.payload) as DecodeResult.Ok).value

    @Test
    fun `the play registration ships two lessons with the A14 copy`() {
        assertSame(steps, StrandsPlayUi().tutorial)
        assertEquals(listOf(StrandsCopy.TUTORIAL_TRACE, StrandsCopy.TUTORIAL_SPANGRAM), steps.map { it.instruction })
    }

    @Test
    fun `every lesson is a tiled board with an edge-to-edge spangram`() {
        for (index in steps.indices) {
            val puzzle = puzzleOf(index)
            assertEquals(StrandsDefinition.id, steps[index].puzzle.gameId)
            val words = puzzle.themeWords + puzzle.spangram
            val cells = words.flatMap { it.cells }
            assertEquals(puzzle.rows * puzzle.cols, cells.size)
            assertEquals(cells.size, cells.toSet().size)
            for (word in words) {
                assertEquals(word.text, word.cells.map(puzzle::letterAt).joinToString(""))
                assertTrue(word.cells.zipWithNext().all { (a, b) -> a.isAdjacentTo(b) })
                assertTrue(word.text in puzzle.validWords)
            }
            val spangram = puzzle.spangram.cells
            val leftRight = spangram.any { it.col == 0 } && spangram.any { it.col == puzzle.cols - 1 }
            val topBottom = spangram.any { it.row == 0 } && spangram.any { it.row == puzzle.rows - 1 }
            assertTrue(leftRight || topBottom)
        }
    }

    @Test
    fun `every lesson is numbered, so its solve hands the sandbox a record`() {
        for (step in steps) assertNotNull(step.ref.puzzleNumber)
    }

    @Test
    fun `the tutorial plays through the sandbox to ready`() {
        val ui = StrandsPlayUi()
        val session = TutorialSession(ui.tutorial)
        val sandbox = TutorialSandbox(ui, session)
        val scope = CoroutineScope(Dispatchers.Unconfined)
        for (index in steps.indices) {
            val lesson = session.state.value as TutorialState.Playing
            assertEquals(index, lesson.index)
            val c = sandbox.controllerFor(lesson, scope) as StrandsPlayController
            val puzzle = puzzleOf(index)
            for (word in puzzle.themeWords + puzzle.spangram) {
                word.cells.forEach { c.tap(it) }
                c.tap(word.cells.last())
            }
            assertTrue(c.isComplete)
            assertTrue((session.state.value as TutorialState.Playing).solved)
            session.next()
        }
        assertEquals(TutorialState.Ready, session.state.value)
    }
}
