package com.ivandsky.puzzlebox.games.tangoui

import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.gameui.MoveHaptic
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.hint.HintMode
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S2 for Tango: a mark the player lands ticks; the solving mark, history steps, hints and a fresh board are silent,
 * and Tango never buzzes. Each row is real controller input, classified from the session before it to the one after.
 */
class TangoMoveHapticTest {

    private val controller = TangoPlayController(
        PlayEnvironment(CoroutineScope(Dispatchers.Unconfined), onSessionChanged = {}, onWon = {}),
        hintMode = HintMode.Stepwise(Dispatchers.Unconfined),
    ).also {
        assertEquals(Adopt.Adopted, it.adoptToday(TangoPlayFixture.envelope(), TangoPlayFixture.ref(LocalDate.parse("2026-11-16"))))
    }

    private fun haptic(input: TangoPlayController.() -> Unit): MoveHaptic? {
        val before = controller.session
        controller.input()
        return tangoMoveHaptic(before, controller.session)
    }

    /** Row 0 is S S . . . . with givens at 0 and 1; cells 2..5 are the player's. */
    private fun place(idx: Int, value: TangoCell?) = haptic { onPlacement(TangoPlacement(idx, value)) }

    @Test
    fun `a mark that lands ticks`() {
        assertEquals(MoveHaptic.Placed, place(2, TangoCell.MOON))
        assertEquals("a tap is a placement too", MoveHaptic.Placed, haptic { onTap(3) })
    }

    @Test
    fun `changing a mark and cycling it back to empty tick`() {
        place(2, TangoCell.SUN)

        assertEquals(MoveHaptic.Placed, place(2, TangoCell.MOON))
        assertEquals(MoveHaptic.Placed, place(2, null))
    }

    @Test
    fun `a placement that lands with a rule break still ticks`() {
        place(2, TangoCell.MOON)
        place(3, TangoCell.MOON)

        val third = place(4, TangoCell.MOON) // three moons in a row

        assertNotNull("the rule break is on the board", controller.lastRejection)
        assertEquals(MoveHaptic.Placed, third)
    }

    @Test
    fun `marks that land between two reads tick once`() {
        val both = haptic {
            onTap(2)
            onTap(3)
        }

        assertEquals(MoveHaptic.Placed, both)
    }

    @Test
    fun `the mark that solves the board is silent, the solve confirm owns that moment`() {
        val marks = TangoPlayFixture.placements.map { (idx, value) -> TangoPlacement(idx, value) }
        marks.dropLast(1).forEach(controller::onPlacement)

        val solving = haptic { onPlacement(marks.last()) }

        assertTrue(controller.isComplete)
        assertNull(solving)
    }

    @Test
    fun `input on a given, or the value a cell already holds, is silent`() {
        assertNull(place(0, TangoCell.MOON))
        assertNull(haptic { onTap(0) })

        place(2, TangoCell.MOON)
        assertNull("the cell already holds it", place(2, TangoCell.MOON))
    }

    @Test
    fun `undo and redo are silent`() {
        place(2, TangoCell.MOON)

        assertNull(haptic { undo() })
        // The only command is on the redo stack: redo has the stack shape of a first placement.
        assertNull(haptic { redo() })

        place(3, TangoCell.SUN)
        assertNull(haptic { undo() })
        assertNull(haptic { redo() })
    }

    @Test
    fun `redos that land between two reads are silent`() {
        place(2, TangoCell.MOON)
        place(3, TangoCell.SUN)
        controller.undo()
        controller.undo()

        val both = haptic {
            redo()
            redo()
        }

        assertNull(both)
    }

    @Test
    fun `clear is silent`() {
        place(2, TangoCell.MOON)
        place(3, TangoCell.SUN)

        assertNull(haptic { clearBoard() })
        assertNull("clear is one undo unit", haptic { undo() })
    }

    @Test
    fun `a hint step is silent`() {
        place(2, TangoCell.MOON)
        val before = controller.session!!

        val hinted = haptic { requestHint() }

        assertEquals("the hint counted", before.hintsUsed + 1, controller.session!!.hintsUsed)
        assertNull(hinted)
    }

    @Test
    fun `play again and a board with no session are silent`() {
        place(2, TangoCell.MOON)

        assertNull(haptic { playAgain() })
        assertNull(tangoMoveHaptic(null, controller.session))
        assertNull(tangoMoveHaptic(controller.session, null))
        assertNull(tangoMoveHaptic(controller.session, controller.session))
    }
}
