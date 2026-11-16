package com.ivandsky.puzzlebox.games.queensui

import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.designsystem.spotlight.SpotlightRole
import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.games.queens.QueensSession
import com.ivandsky.puzzlebox.games.queens.QueensSessionStep
import com.ivandsky.puzzlebox.games.queensui.hint.QueensSpotlightMapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B4, ticket 44: the Queens UI module reads `HintFirstMove` when it builds the hint mode, and the mode's first-move
 * option reaches the stepper. The play fixture opens on a one-cell region, so the ladder order points at that queen
 * while a first move points at a row the player has to work out.
 */
class QueensFirstMoveHintTest {

    private val on = MutableStateFlow(emptySet<ReleaseFlag>())
    private val flags = object : ReleaseFlags {
        override val enabled = on
    }

    /** The play registration as [queensUiModule] builds it, computing on the calling thread. */
    private val ui = QueensPlayUi(InMemoryQueensSettings()) { queensHintMode(flags, Dispatchers.Unconfined) }

    private val fresh = QueensSession(ref = null, puzzle = QueensPlayFixtures.puzzle())
    private val solution = QueensPlayFixtures.solution.toSet()

    private fun roles(firstMove: Boolean): Map<Int, SpotlightRole> =
        QueensSpotlightMapper.map(QueensSessionStep.next(fresh, solution, firstMove), fresh.puzzle, playId = 0).roles

    /** Opens today's board on a new controller and presses the hint once. */
    private fun firstPress(): Map<Int, SpotlightRole> {
        val controller = ui.createController(PlayEnvironment(CoroutineScope(Dispatchers.Unconfined), {}, {})) as QueensPlayController
        assertEquals(Adopt.Adopted, controller.adoptToday(QueensPlayFixtures.envelope(), QueensPlayFixtures.ref()))
        controller.requestHint()
        return checkNotNull(checkNotNull(controller.board).spotlight).roles
    }

    @Test
    fun `the fixture tells the two orders apart`() {
        assertNotEquals(roles(firstMove = false), roles(firstMove = true))
    }

    @Test
    fun `with the flag off the first press shows the ladder order's step`() {
        assertEquals(roles(firstMove = false), firstPress())
    }

    @Test
    fun `with the flag on the first press shows the first-move step`() {
        on.value = setOf(ReleaseFlag.HintFirstMove)
        assertEquals(roles(firstMove = true), firstPress())
    }

    @Test
    fun `a flag turned on applies to the next board opened`() {
        assertEquals(roles(firstMove = false), firstPress())
        on.value = setOf(ReleaseFlag.HintFirstMove)
        assertEquals(roles(firstMove = true), firstPress())
    }

    @Test
    fun `the mode takes its option from the flag and keeps the compute dispatcher`() {
        assertFalse(queensHintMode(flags, Dispatchers.Unconfined).firstMove)
        on.value = setOf(ReleaseFlag.BoardTokens)
        assertFalse("another flag turned first move on", queensHintMode(flags, Dispatchers.Unconfined).firstMove)
        on.value = setOf(ReleaseFlag.HintFirstMove)
        val mode = queensHintMode(flags, Dispatchers.Unconfined)
        assertTrue(mode.firstMove)
        assertSame(Dispatchers.Unconfined, mode.compute)
    }

    @Test
    fun `without release flags bound the ladder order stands`() {
        assertFalse(queensHintMode(flags = null, compute = Dispatchers.Unconfined).firstMove)
    }
}
