package com.ivandsky.puzzlebox.games.tangoui

import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.engine.TangoVerdict
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
import org.junit.Test

/**
 * S2 for Tango: a mark the player lands ticks, refused input buzzes, and history steps, hints and a fresh board are
 * silent. Each row is one real controller step, classified from the snapshot before it to the one after.
 */
class TangoMoveHapticTest {

    private val controller = TangoPlayController(
        PlayEnvironment(CoroutineScope(Dispatchers.Unconfined), onSessionChanged = {}, onWon = {}),
        hintMode = HintMode.Stepwise(Dispatchers.Unconfined),
    ).also {
        assertEquals(Adopt.Adopted, it.adoptToday(TangoPlayFixture.envelope(), TangoPlayFixture.ref(LocalDate.parse("2026-11-16"))))
    }

    private fun snapshot() = TangoPlaySnapshot(controller.session, controller.lastRejection)

    private fun haptic(step: TangoPlayController.() -> Unit): MoveHaptic? {
        val before = snapshot()
        controller.step()
        return tangoMoveHaptic(before, snapshot())
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
    fun `a new rejection that lands nothing buzzes`() {
        place(2, TangoCell.MOON)
        val before = snapshot()

        val refused = before.copy(lastRejection = TangoVerdict.Rejected(TangoVerdict.Reason.LockedGiven, 0))

        assertEquals(MoveHaptic.Rejected, tangoMoveHaptic(before, refused))
        assertNull("the same rejection, seen again", tangoMoveHaptic(refused, refused.copy()))
        // Rejected is a data class: a second refusal of the same cell equals the first and must still buzz.
        assertEquals(MoveHaptic.Rejected, tangoMoveHaptic(refused, refused.copy(lastRejection = refused.lastRejection!!.copy())))
    }

    @Test
    fun `a rejection that clears is silent`() {
        place(2, TangoCell.MOON)
        val refused = snapshot().copy(lastRejection = TangoVerdict.Rejected(TangoVerdict.Reason.LockedGiven, 0))

        assertNull(tangoMoveHaptic(refused, refused.copy(lastRejection = null)))
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
    fun `clear is silent`() {
        place(2, TangoCell.MOON)
        place(3, TangoCell.SUN)

        assertNull(haptic { clearBoard() })
        assertNull("clear is one undo unit", haptic { undo() })
    }

    @Test
    fun `a hint step is silent`() {
        place(2, TangoCell.MOON)
        val before = snapshot()

        val hinted = haptic { requestHint() }

        assertEquals("the hint counted", before.session!!.hintsUsed + 1, controller.session!!.hintsUsed)
        assertNull(hinted)
    }

    @Test
    fun `play again and a board with no session are silent`() {
        place(2, TangoCell.MOON)

        assertNull(haptic { playAgain() })
        assertNull(tangoMoveHaptic(TangoPlaySnapshot(null, null), snapshot()))
        assertNull(tangoMoveHaptic(snapshot(), TangoPlaySnapshot(null, null)))
        assertNull(tangoMoveHaptic(snapshot(), snapshot()))
    }
}
