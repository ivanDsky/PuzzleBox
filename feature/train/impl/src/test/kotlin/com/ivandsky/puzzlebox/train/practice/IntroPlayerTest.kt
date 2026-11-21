package com.ivandsky.puzzlebox.train.practice

import com.ivandsky.puzzlebox.gameui.trainer.IntroVariant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The intro's playback in virtual time: the handoff's timing, resting on the last frame, seeking, and reduced motion. */
@OptIn(ExperimentalCoroutinesApi::class)
class IntroPlayerTest {

    private fun entries(count: Int = 2) = List(count) {
        IntroEntry(IntroVariant("V$it", "Rule.", "Spot.", "Do."), StandInIntroDemo(it, listOf("a", "b", "c", "d")))
    }

    private fun TestScope.advance(millis: Long) {
        advanceTimeBy(millis)
        runCurrent()
    }

    /** Bug caught: the demo not starting by itself, or its frames out of the handoff's timing (evidence 1.3 s, first square 1.8 s later, the rest 150 ms apart). */
    @Test
    fun `frames advance at the handoff timing and the variant rests on its last frame`() = runTest {
        val player = IntroPlayer(entries(1), reduced = false, scope = backgroundScope)
        runCurrent()
        assertEquals(0, player.frame.value)
        assertTrue(player.playing.value)

        advance(1_299)
        assertEquals(0, player.frame.value)
        advance(1)
        assertEquals(1, player.frame.value)
        advance(1_799)
        assertEquals(1, player.frame.value)
        advance(1)
        assertEquals(2, player.frame.value)
        advance(150)
        assertEquals(3, player.frame.value)
        assertFalse(player.playing.value)

        advance(60_000)
        assertEquals(3, player.frame.value)
    }

    /** Bug caught: the second variant never playing after the first, or starting with no rest on the first's last frame. */
    @Test
    fun `the next variant follows by itself after a rest`() = runTest {
        val player = IntroPlayer(entries(2), reduced = false, scope = backgroundScope)
        runCurrent()
        advance(3_250)
        assertEquals(0, player.variant.value)
        assertEquals(3, player.frame.value)

        advance(1_499)
        assertEquals(0, player.variant.value)
        advance(1)
        assertEquals(1, player.variant.value)
        assertEquals(0, player.frame.value)
    }

    /** Bug caught: a tapped variant chaining on to the next one, which would take the demo away from a player who is reading it. */
    @Test
    fun `a tapped variant plays alone`() = runTest {
        val player = IntroPlayer(entries(3), reduced = false, scope = backgroundScope)
        runCurrent()

        player.select(1)
        runCurrent()
        assertEquals(1, player.variant.value)
        assertEquals(0, player.frame.value)
        advance(3_250 + 60_000)

        assertEquals(1, player.variant.value)
        assertEquals(3, player.frame.value)
    }

    /** Bug caught: a seek that autoplay then plays over, so the frame the player picked is replaced a moment later. */
    @Test
    fun `seeking shows that frame and stops autoplay`() = runTest {
        val player = IntroPlayer(entries(2), reduced = false, scope = backgroundScope)
        runCurrent()
        advance(1_300)

        player.seek(2)
        advance(60_000)

        assertEquals(0, player.variant.value)
        assertEquals(2, player.frame.value)
        assertFalse(player.playing.value)
    }

    /** Bug caught: Replay under reduced motion tweening or playing at the animated timing, or autoplay under reduced motion at all. */
    @Test
    fun `reduced motion rests on the last frame and replay steps at 1,7 seconds`() = runTest {
        val player = IntroPlayer(entries(2), reduced = true, scope = backgroundScope)
        runCurrent()
        assertEquals(0, player.variant.value)
        assertEquals(3, player.frame.value)
        advance(60_000)
        assertEquals(0, player.variant.value)
        assertEquals(3, player.frame.value)

        player.replay()
        runCurrent()
        assertEquals(0, player.frame.value)
        advance(1_699)
        assertEquals(0, player.frame.value)
        advance(1)
        assertEquals(1, player.frame.value)
        advance(1_700)
        assertEquals(2, player.frame.value)
        advance(1_700)
        assertEquals(3, player.frame.value)
        advance(60_000)
        assertEquals(0, player.variant.value)
    }
}
