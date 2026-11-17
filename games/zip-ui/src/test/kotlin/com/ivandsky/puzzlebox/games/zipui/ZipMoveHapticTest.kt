package com.ivandsky.puzzlebox.games.zipui

import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.gameui.DragEvent
import com.ivandsky.puzzlebox.gameui.GridCell
import com.ivandsky.puzzlebox.gameui.MoveHaptic
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S2 for Zip: a step that grows or shrinks the trace ticks, a refused or invalid step buzzes once, and the solving
 * step, undo, redo, clear and hint are silent. Each row is real controller input, classified from the snapshot before
 * it to the one after.
 */
class ZipMoveHapticTest {

    private val controller = ZipPlayController(PlayEnvironment(CoroutineScope(Dispatchers.Unconfined), {}, {})).also {
        check(it.adoptToday(ZipPlayFixture.envelope(), ZipPlayFixture.ref(LocalDate.of(2026, 11, 16))) == Adopt.Adopted)
    }

    private fun snapshot() = ZipMoveSnapshot(controller.session, controller.feedback.refusals)

    private fun haptic(input: ZipPlayController.() -> Unit): MoveHaptic? {
        val before = snapshot()
        controller.input()
        return zipMoveHaptic(before, snapshot())
    }

    private fun step(idx: Int): MoveHaptic? = haptic { onDragEvent(DragEvent.Extend(grid(idx))) }

    private fun grid(idx: Int) = GridCell(idx / 5, idx % 5)

    private fun path(): List<Int> = controller.session!!.path.map { it.idx }

    @Test
    fun `a step that extends the trace ticks`() {
        assertEquals(MoveHaptic.Placed, step(0))
        assertEquals(MoveHaptic.Placed, step(1))
    }

    @Test
    fun `a drag back that shrinks the trace ticks`() {
        step(0)
        step(1)
        step(2)

        assertEquals(MoveHaptic.Placed, haptic { onDragEvent(DragEvent.Retract(1)) })
        assertEquals(listOf(0, 1), path())
    }

    @Test
    fun `the solving step is silent`() {
        ZipPlayFixture.solution.dropLast(1).forEach { step(it.idx) }

        assertNull(step(ZipPlayFixture.solution.last().idx))
        assertTrue(controller.isComplete)
    }

    @Test
    fun `an invalid step buzzes once`() {
        assertEquals(MoveHaptic.Rejected, step(24))
    }

    @Test
    fun `a refused drag step buzzes`() {
        // Starting off 1 is a refusal the player meant (WrongStart): explained, and it buzzes.
        assertEquals(MoveHaptic.Rejected, haptic { onDragEvent(DragEvent.Refused(grid(1))) })
    }

    @Test
    fun `a second refusal of the same kind buzzes again`() {
        assertEquals(MoveHaptic.Rejected, haptic { onDragEvent(DragEvent.Refused(grid(1))) })
        assertEquals(MoveHaptic.Rejected, haptic { onDragEvent(DragEvent.Refused(grid(1))) })
    }

    @Test
    fun `an unexplained refusal of a traced cell is silent`() {
        // A snake fill strays onto the traced row: CellAlreadyInPath is not a mistake the player made.
        step(0)
        step(1)
        step(2)

        assertNull(haptic { onDragEvent(DragEvent.Refused(grid(0))) })
        assertEquals(listOf(0, 1, 2), path())
    }

    @Test
    fun `a rejection buzzes once - a clock fold after it is silent and the next step ticks`() {
        step(0)
        assertEquals(MoveHaptic.Rejected, step(24))
        val rejected = controller.session

        assertNull(haptic { onBackground() })
        assertNull(haptic { onForeground() })
        assertNotSame("the clock fold changed the session", rejected, controller.session)
        assertEquals(MoveHaptic.Placed, step(1))
    }

    @Test
    fun `steps that land between two reads tick once`() {
        assertEquals(
            MoveHaptic.Placed,
            haptic { listOf(0, 1, 2).forEach { onDragEvent(DragEvent.Extend(grid(it))) } },
        )
        assertEquals(listOf(0, 1, 2), path())
    }

    @Test
    fun `undo and clear are silent`() {
        step(0)
        step(1)

        assertNull(haptic { undo() })
        assertEquals(listOf(0), path())
        assertNull(haptic { edits.clear() })
    }

    @Test
    fun `a redo that empties the redo stack is silent`() {
        step(0)
        step(1)
        controller.undo()

        assertNull(haptic { redo() })
        assertEquals(listOf(0, 1), path())
    }

    @Test
    fun `a redo with more still undone is silent`() {
        step(0)
        step(1)
        step(2)
        controller.undo()
        controller.undo()

        assertNull(haptic { redo() })
        assertEquals(listOf(0, 1), path())
    }

    @Test
    fun `redos that land between two reads are silent`() {
        step(0)
        step(1)
        step(2)
        controller.undo()
        controller.undo()

        assertNull(haptic { redo(); redo() })
        assertEquals(listOf(0, 1, 2), path())
    }

    @Test
    fun `a cell drawn again after an undo ticks`() {
        step(0)
        step(1)
        controller.undo()

        assertEquals(MoveHaptic.Placed, step(1))
    }

    @Test
    fun `a hint is silent`() {
        assertNull(haptic { hint() })
        assertEquals(1, controller.hintsUsed)
    }

    @Test
    fun `an invalid step and the solving step between two reads are silent`() {
        ZipPlayFixture.solution.dropLast(1).forEach { step(it.idx) }

        assertNull(
            haptic {
                onDragEvent(DragEvent.Extend(grid(ZipPlayFixture.solution.first().idx))) // traced cell: invalid
                onDragEvent(DragEvent.Extend(grid(ZipPlayFixture.solution.last().idx)))
            },
        )
        assertTrue(controller.isComplete)
    }

    @Test
    fun `no session on either side is silent`() {
        assertNull(zipMoveHaptic(ZipMoveSnapshot(null, 0), snapshot()))
        assertNull(zipMoveHaptic(snapshot(), ZipMoveSnapshot(null, 0)))
    }
}
