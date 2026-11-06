package com.ivandsky.puzzlebox.games.zipui

import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.gameui.DragEvent
import com.ivandsky.puzzlebox.gameui.GridCell
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.TutorialSandbox
import com.ivandsky.puzzlebox.gameui.TutorialSession
import com.ivandsky.puzzlebox.gameui.TutorialState
import com.ivandsky.puzzlebox.gameui.TutorialStep
import com.ivandsky.puzzlebox.gameui.playCaption
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.games.zip.ZipPuzzle
import com.ivandsky.puzzlebox.games.zip.ZipPuzzleRef
import com.ivandsky.puzzlebox.games.zip.codec.ZipCodec
import com.ivandsky.puzzlebox.games.zip.codec.ZipSessionCodec
import com.ivandsky.puzzlebox.games.zip.engine.ZipRules
import com.ivandsky.puzzlebox.games.zip.engine.ZipSolver
import com.ivandsky.puzzlebox.games.zip.engine.ZipValidation
import com.ivandsky.puzzlebox.games.zip.engine.ZipVariants
import com.ivandsky.puzzlebox.model.PuzzleKey
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ticket 14: Zip's lessons are tiny, readable, and won by doing what each one says. */
class ZipTutorialTest {

    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private val steps = ZipTutorial.steps

    private fun zip(step: TutorialStep): ZipPuzzle =
        (ZipCodec.decode(step.puzzle.payload) as DecodeResult.Ok).value

    private fun intendedPath(step: TutorialStep): List<Cell> = checkNotNull(zip(step).solution)

    private fun controller(
        saved: MutableList<ByteArray> = mutableListOf(),
        won: MutableList<CompletionRecord> = mutableListOf(),
    ) = ZipPlayController(PlayEnvironment(scope, { saved += it }, { won += it }))

    private fun adopted(step: TutorialStep, c: ZipPlayController = controller()): ZipPlayController = c.also {
        check(it.adoptToday(step.puzzle, step.ref) == Adopt.Adopted) { "lesson did not adopt" }
    }

    private fun extend(c: ZipPlayController, cell: Cell, n: Int) =
        c.onDragEvent(DragEvent.Extend(GridCell(cell.idx / n, cell.idx % n)))

    @Test
    fun `three lessons on tutorial-only boards under custom refs`() {
        assertEquals(listOf(3, 3, 4), steps.map { zip(it).board.n })
        steps.forEach { step ->
            val variant = step.puzzle.variant
            assertEquals(ZipDefinition.id, step.puzzle.gameId)
            assertTrue(variant in ZipVariants.tutorial.keys)
            assertNull(ZipVariants.dailyById(variant)) // never a daily size
            assertEquals(ZipPuzzleRef(PuzzleKey.Daily(LocalDate.of(2026, 1, 1), puzzleNumber = null)), step.ref)
        }
        assertEquals(listOf(2, 4, 2), steps.map { zip(it).board.waypoints.size })
    }

    @Test
    fun `each lesson decodes through the plugin and the session codec`() {
        steps.forEach { step ->
            assertTrue(ZipDefinition.decode(step.puzzle.payload) is DecodeResult.Ok)
            val c = adopted(step)
            val restored = ZipSessionCodec.deserialize(checkNotNull(c.persist()))
            val session = (restored as DecodeResult.Ok).value
            assertEquals(zip(step), session.puzzle)
            assertEquals(step.ref, session.ref)
        }
    }

    @Test
    fun `lessons one and two have one path, and every intended path is a real solution`() {
        val solver = ZipSolver()
        steps.forEach { step -> assertTrue(ZipRules.verifyPath(zip(step), intendedPath(step))) }
        assertEquals(1, solver.countSolutions(zip(steps[0])))
        assertEquals(1, solver.countSolutions(zip(steps[1])))
        // Lesson three is about filling, so any full path wins; the short line does not.
        assertTrue(solver.countSolutions(zip(steps[2]), limit = 10) > 1)
    }

    /** A child doing what the line says never sees an error: no caption at any point. */
    @Test
    fun `each intended path wins through the real controller with no refusal on the way`() {
        steps.forEachIndexed { index, step ->
            val saved = mutableListOf<ByteArray>()
            val won = mutableListOf<CompletionRecord>()
            val c = adopted(step, controller(saved, won))
            val n = zip(step).board.n
            intendedPath(step).forEachIndexed { taken, cell ->
                extend(c, cell, n)
                val where = "lesson ${index + 1}, move ${taken + 1}"
                assertEquals(where, taken + 1, c.session?.path?.size)
                assertNull(where, c.feedback.rejectedMove)
                assertNull(where, c.rejection)
                assertNull(where, c.violation)
                assertNull(where, playCaption(c, c))
            }
            assertTrue(c.isComplete)
            assertEquals(1, won.size)
            assertEquals(step.ref, won.single().puzzleRef)
            val last = (ZipSessionCodec.deserialize(saved.last()) as DecodeResult.Ok).value
            assertTrue(last.isComplete)
        }
    }

    @Test
    fun `lesson two refuses a number out of order and says which comes first`() {
        val step = steps[1]
        val c = adopted(step)
        listOf(0, 1, 2).forEach { extend(c, Cell(it), 3) } // cell 2 is the 4, before the 2
        assertEquals(listOf(Cell(0), Cell(1)), c.session?.path)
        assertTrue(c.feedback.rejectedMove?.reason is ZipValidation.Reason.WrongWaypointOrder)
        assertEquals("Go to 2 first", playCaption(c, c))
    }

    @Test
    fun `lesson three's short line to 2 does not win`() {
        val won = mutableListOf<CompletionRecord>()
        val c = adopted(steps[2], controller(won = won))
        listOf(0, 4).forEach { extend(c, Cell(it), 4) }
        assertEquals(listOf(Cell(0), Cell(4)), c.session?.path)
        assertFalse(c.isComplete)
        assertTrue(won.isEmpty())
    }

    @Test
    fun `the play registration's tutorial plays through the sandbox to ready`() {
        val ui = ZipPlayUi()
        assertEquals(steps.map { it.instruction }, ui.tutorial.map { it.instruction })
        val session = TutorialSession(ui.tutorial)
        val sandbox = TutorialSandbox(ui, session)
        repeat(ui.tutorial.size) {
            val lesson = session.state.value as TutorialState.Playing
            val c = sandbox.controllerFor(lesson, scope) as ZipPlayController
            val n = zip(lesson.step).board.n
            intendedPath(lesson.step).forEach { extend(c, it, n) }
            assertEquals(true, (session.state.value as TutorialState.Playing).solved)
            session.next()
        }
        assertEquals(TutorialState.Ready, session.state.value)
    }
}
