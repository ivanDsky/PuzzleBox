package com.ivandsky.puzzlebox.train.practice

import com.ivandsky.puzzlebox.gameui.trainer.PracticeStatus
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import com.ivandsky.puzzlebox.train.practice.PracticeRig.Companion.LINE_LOCK
import com.ivandsky.puzzlebox.train.practice.PracticeRig.Companion.puzzle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The pause on a finished board: its countdown, the app in the background, the skip button, and the screen reader's wait for a tap. */
class PracticeHoldTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val device = listOf(puzzle(1), puzzle(2))

    private fun rig(screenReaderOn: Boolean = false) =
        PracticeRig(emptyList(), device = device, catalog = listOf("naked-single", "line-lock"), opens = LINE_LOCK, screenReaderOn = screenReaderOn)

    private fun advance(ms: Long) = main.testDispatcher.scheduler.apply {
        advanceTimeBy(ms)
        runCurrent()
    }

    private fun PracticeRig.end() {
        board.say(PracticeStatus.Done(null))
        advance(0)
    }

    /** Bug caught: a countdown that does not start at the full hold, or does not follow the clock, so the band's seconds are wrong. */
    @Test
    fun `the hold starts at one second and counts down with the clock`() {
        val rig = rig()
        assertNull(rig.vm.hold.value)

        rig.end()
        assertEquals(BoardHold(BoardEndHoldMillis, waitsForTap = false), rig.vm.hold.value)

        advance(400)
        assertEquals(BoardHold(600, waitsForTap = false), rig.vm.hold.value)
        assertEquals(1, rig.vm.chapter.value.board)
    }

    /** Bug caught: a hold that runs on while the app is in the background, so the player comes back to the next board. */
    @Test
    fun `the hold stands still in the background and goes on in the foreground`() {
        val rig = rig()
        rig.end()
        advance(400)

        rig.vm.setForeground(false)
        advance(60_000)
        assertEquals(1, rig.vm.chapter.value.board)
        assertEquals(600L, rig.vm.hold.value?.remainingMs)

        rig.vm.setForeground(true)
        advance(500)
        assertEquals(1, rig.vm.chapter.value.board)
        advance(100)
        assertEquals(2, rig.vm.chapter.value.board)
    }

    /** Bug caught: a Next board button that does nothing, or one that waits out the countdown. */
    @Test
    fun `skip opens the next board at once`() {
        val rig = rig()
        rig.end()
        advance(500)

        rig.vm.skip()
        advance(0)

        assertEquals(2, rig.vm.chapter.value.board)
        assertTrue(rig.state is PracticeState.Practicing)
    }

    /** Bug caught: skip on the last board going nowhere, or opening a sixth board. */
    @Test
    fun `skip on the last board opens the result`() {
        val rig = rig()
        repeat(4) { rig.end(); rig.vm.skip(); advance(0) }
        rig.end()
        assertTrue(rig.state is PracticeState.Practicing)

        rig.vm.skip()
        advance(0)

        assertTrue(rig.state is PracticeState.Result)
        assertEquals(5, rig.game.boards.size)
    }

    /** Bug caught: a late skip (no hold running) opening a board nobody finished. */
    @Test
    fun `skip with no finished board does nothing`() {
        val rig = rig()

        rig.vm.skip()
        advance(0)

        assertEquals(1, rig.vm.chapter.value.board)
        assertEquals(1, rig.game.boards.size)
    }

    /** Bug caught: a screen reader user hurried on by a timer, or a hold that does not say it waits for a tap. */
    @Test
    fun `with a screen reader the board stays until skip`() {
        val rig = rig(screenReaderOn = true)
        rig.end()

        advance(60_000)
        assertEquals(1, rig.vm.chapter.value.board)
        assertEquals(BoardHold(BoardEndHoldMillis, waitsForTap = true), rig.vm.hold.value)

        rig.vm.skip()
        advance(0)
        assertEquals(2, rig.vm.chapter.value.board)
    }

    @Test
    fun `with a screen reader the last board waits for the tap before the result`() {
        val rig = rig(screenReaderOn = true)
        repeat(4) { rig.end(); rig.vm.skip(); advance(0) }
        rig.end()

        advance(60_000)
        assertTrue(rig.state is PracticeState.Practicing)

        rig.vm.skip()
        advance(0)
        assertTrue(rig.state is PracticeState.Result)
    }
}
