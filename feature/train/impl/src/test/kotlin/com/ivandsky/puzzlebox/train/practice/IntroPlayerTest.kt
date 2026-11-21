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

/** The intro's walk in virtual time: nothing plays by itself, Next step, the landing stagger, the next variant, and reduced motion. */
@OptIn(ExperimentalCoroutinesApi::class)
class IntroPlayerTest {

    /** Each demo: the opening, the evidence, then three squares landing (frames 2 to 4). */
    private fun entries(count: Int = 2) = List(count) {
        IntroEntry(IntroVariant("V$it", "Rule.", "Spot.", "Do."), StandInIntroDemo(it, listOf("a", "b", "c", "d", "e")))
    }

    private fun TestScope.advance(millis: Long) {
        advanceTimeBy(millis)
        runCurrent()
    }

    /** Bug caught: the demo moving on by itself (too fast to read, round 4), or opening on the plain position (round 5). */
    @Test
    fun `the intro opens on the evidence and stays there`() = runTest {
        val player = IntroPlayer(entries(2), reduced = false, scope = backgroundScope)
        advance(60_000)

        assertEquals(0, player.variant.value)
        assertEquals(EvidenceFrame, player.frame.value)
    }

    /** Bug caught: the landing squares asking for a tap each instead of landing as one step. */
    @Test
    fun `next step lands the squares one after another`() = runTest {
        val player = IntroPlayer(entries(1), reduced = false, scope = backgroundScope)

        player.next()
        runCurrent()
        assertEquals(2, player.frame.value)
        advance(LandingStaggerMillis)
        assertEquals(3, player.frame.value)
        advance(LandingStaggerMillis)
        assertEquals(4, player.frame.value)
        advance(60_000)
        assertEquals(4, player.frame.value)
    }

    /** Bug caught: a variant's end that is a dead end, or a last variant that wraps around to the first. */
    @Test
    fun `past a variant's last step next step opens the next variant, and the last has none`() = runTest {
        val player = IntroPlayer(entries(2), reduced = true, scope = backgroundScope)
        player.next()
        assertEquals(4, player.frame.value)
        assertTrue(hasNextStep(0, 4, frames = 5, count = 2))

        player.next()
        assertEquals(1, player.variant.value)
        assertEquals(EvidenceFrame, player.frame.value)

        player.next()
        assertFalse(hasNextStep(1, 4, frames = 5, count = 2))
        player.next()
        assertEquals(1, player.variant.value)
        assertEquals(4, player.frame.value)
    }

    /** Bug caught: reduced motion still staggering the landing squares. */
    @Test
    fun `under reduced motion the landing step shows every square at once`() = runTest {
        val player = IntroPlayer(entries(1), reduced = true, scope = backgroundScope)
        player.next()
        runCurrent()

        assertEquals(4, player.frame.value)
    }

    /** Bug caught: a seek or a tapped variant that a running landing then plays over, or Start over that keeps the frame. */
    @Test
    fun `seek, select and start over stop the landing and show their frame`() = runTest {
        val player = IntroPlayer(entries(3), reduced = false, scope = backgroundScope)
        player.next()
        runCurrent()

        player.seek(1)
        advance(60_000)
        assertEquals(1, player.frame.value)

        player.select(2)
        assertEquals(2, player.variant.value)
        assertEquals(EvidenceFrame, player.frame.value)

        player.next()
        player.replay()
        assertEquals(2, player.variant.value)
        assertEquals(EvidenceFrame, player.frame.value)
    }
}
