package com.ivandsky.puzzlebox.games.strandsui

import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.gameui.MoveHaptic
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** Each row drives the real controller: the snapshot before, one input, the snapshot after. */
class StrandsMoveHapticTest {

    private lateinit var controller: StrandsPlayController

    @Before
    fun adopt() {
        controller = StrandsPlayController(PlayEnvironment(CoroutineScope(Dispatchers.Unconfined), {}, {}))
        assertEquals(Adopt.Adopted, controller.adoptToday(StrandsUiFixtures.envelope(), StrandsUiFixtures.ref))
    }

    /** The haptic the effect would play for [input]: the snapshots either side of it. */
    private fun haptic(input: () -> Unit): MoveHaptic? {
        val before = controller.moveSnapshot()
        input()
        return strandsMoveHaptic(before, controller.moveSnapshot())
    }

    private fun drag(path: List<Coord>) {
        controller.onTrace(TraceInput.dragStart(path.first()))
        path.drop(1).forEach { controller.onTrace(TraceInput.dragTo(controller.trace, it)) }
    }

    private fun lift() = controller.onTrace(TraceInput.dragEnd(controller.trace))

    private val pies = StrandsUiFixtures.row(0)
    private val bake = StrandsUiFixtures.row(1)
    private val pike = listOf(Coord(0, 0), Coord(0, 1), Coord(1, 2), Coord(1, 3))
    private val pik = listOf(Coord(0, 0), Coord(0, 1), Coord(1, 2))

    @Test
    fun `a letter tapped onto an empty board is placed`() {
        assertEquals(MoveHaptic.Placed, haptic { controller.tap(Coord(0, 0)) })
    }

    @Test
    fun `a letter joining the drag is placed`() {
        controller.onTrace(TraceInput.dragStart(Coord(0, 0)))
        assertEquals(MoveHaptic.Placed, haptic { controller.onTrace(TraceInput.dragTo(controller.trace, Coord(0, 1))) })
    }

    @Test
    fun `backtracking one letter is null`() {
        drag(listOf(Coord(0, 0), Coord(0, 1)))
        assertNull(haptic { controller.onTrace(TraceStep.Updated(Trace(listOf(Coord(0, 0))))) })
    }

    @Test
    fun `backtracking and joining another letter in one read is placed`() {
        drag(listOf(Coord(0, 0), Coord(0, 1), Coord(0, 2)))
        assertEquals(
            MoveHaptic.Placed,
            haptic { controller.onTrace(TraceStep.Updated(Trace(listOf(Coord(0, 0), Coord(0, 1), Coord(1, 2))))) },
        )
    }

    @Test
    fun `the drag leaving the board is null`() {
        drag(listOf(Coord(0, 0), Coord(0, 1)))
        assertNull(haptic { controller.onTrace(TraceStep.Cleared) })
    }

    @Test
    fun `a theme word found is placed`() {
        drag(pies)
        assertEquals(MoveHaptic.Placed, haptic { lift() })
    }

    @Test
    fun `the spangram found is placed`() {
        drag(bake)
        assertEquals(MoveHaptic.Placed, haptic { lift() })
    }

    @Test
    fun `a valid non-theme word found is placed`() {
        drag(pike)
        assertEquals(MoveHaptic.Placed, haptic { lift() })
    }

    @Test
    fun `a word too short is rejected`() {
        drag(pik)
        assertEquals(MoveHaptic.Rejected, haptic { lift() })
    }

    @Test
    fun `the same refused word again is rejected again`() {
        drag(pik)
        lift()
        drag(pik)
        assertEquals(MoveHaptic.Rejected, haptic { lift() })
    }

    @Test
    fun `the solving lift is null, the solve confirm owns it`() {
        (0 until 3).forEach { drag(StrandsUiFixtures.row(it)); lift() }
        drag(StrandsUiFixtures.row(3))
        assertNull(haptic { lift() })
    }

    @Test
    fun `a hint leaves the snapshot as it was, so it is null`() {
        val before = controller.moveSnapshot()
        controller.pressHint()
        assertEquals(before, controller.moveSnapshot())
        assertNull(strandsMoveHaptic(before, controller.moveSnapshot()))
    }
}
