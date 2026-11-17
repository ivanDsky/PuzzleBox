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
import org.junit.Assert.assertNull
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
    }

    @Test
    fun `the solving step is silent`() {
        ZipPlayFixture.solution.dropLast(1).forEach { step(it.idx) }

        assertNull(step(ZipPlayFixture.solution.last().idx))
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
    fun `undo redo and clear are silent`() {
        step(0)
        step(1)

        assertNull(haptic { undo() })
        assertNull(haptic { redo() })
        assertNull(haptic { edits.clear() })
    }

    @Test
    fun `a redo that empties the redo stack is silent`() {
        step(0)
        step(1)
        controller.undo()

        assertNull(haptic { redo() })
    }

    @Test
    fun `a hint is silent`() {
        assertNull(haptic { hint() })
    }

    @Test
    fun `no session on either side is silent`() {
        assertNull(zipMoveHaptic(ZipMoveSnapshot(null, 0), snapshot()))
        assertNull(zipMoveHaptic(snapshot(), ZipMoveSnapshot(null, 0)))
    }
}
