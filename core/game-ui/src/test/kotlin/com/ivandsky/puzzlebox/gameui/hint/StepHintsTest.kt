package com.ivandsky.puzzlebox.gameui.hint

import com.ivandsky.puzzlebox.designsystem.spotlight.Spotlight
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.ivandsky.puzzlebox.designsystem.spotlight.SpotlightGlyphs
import com.ivandsky.puzzlebox.designsystem.spotlight.SpotlightRole
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pins the press flow's counting, free repeat, culprit erase and stale-result rules (spec A1, A4, A9, A10, A19). */
class StepHintsTest {

    private val scheduler = TestCoroutineScheduler()
    private val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(scheduler, name = "main"))
    private val compute = FlaggingDispatcher(StandardTestDispatcher(scheduler, name = "compute"))
    private val game = FakeGame()
    private var used = 0
    private val counts = mutableListOf<Int>()
    private val hints = StepHints(HintMode.Stepwise(compute), scope, game, used = { used }, onCount = { counts += it; used = it })

    /** Presses and lets the search and delivery run. */
    private fun press() {
        hints.press()
        scheduler.runCurrent()
    }

    @Test
    fun `a new key counts once and shows playId 1`() {
        game.step = "a"
        press()

        assertEquals(listOf(1), counts)
        assertEquals(1, hints.spotlight?.playId)
    }

    @Test
    fun `a repeat is free and replays with a bumped playId`() {
        game.step = "a"
        press()
        press()

        assertEquals(listOf(1), counts)
        assertEquals(2, hints.spotlight?.playId)
    }

    @Test
    fun `a second new key counts again and a counted key after another is free`() {
        game.step = "a"
        press()
        game.step = "b"
        press()
        game.step = "a"
        press()

        assertEquals(listOf(1, 2), counts)
        assertEquals(3, hints.spotlight?.playId)
    }

    @Test
    fun `a culprit pressed again erases once, clears, is free, and the next press is never a second erase`() {
        game.step = "c"
        game.culprits += "c"
        press()
        assertEquals(listOf(1), counts)
        assertTrue(game.erased.isEmpty())
        assertEquals(1, hints.spotlight?.playId)

        press()
        assertEquals(listOf("c"), game.erased)
        assertNull(hints.spotlight)
        assertEquals(listOf(1), counts)

        val capturesBefore = game.captures
        press()
        assertEquals(capturesBefore + 1, game.captures)
        assertEquals(listOf("c"), game.erased)
        assertEquals(2, hints.spotlight?.playId)
        assertEquals(listOf(1), counts)
    }

    @Test
    fun `input before delivery drops the result`() {
        game.step = "a"
        hints.press()
        hints.inputMade()
        scheduler.runCurrent()

        assertNull(hints.spotlight)
        assertTrue(counts.isEmpty())
    }

    @Test
    fun `a second press before the first delivers shows and counts only the second step`() {
        game.step = "a"
        hints.press()
        game.step = "b"
        hints.press()
        scheduler.runCurrent()

        assertEquals(listOf(1), counts)
        assertEquals(listOf("b"), game.spotlighted)
    }

    @Test
    fun `a null key clears a showing spotlight, counts nothing and leaves the ledger untouched`() {
        game.step = "a"
        press()
        game.step = "none"
        game.nullKeys += "none"
        press()
        assertNull(hints.spotlight)
        assertEquals(listOf(1), counts)

        game.step = "b"
        press()
        assertEquals(listOf(1, 2), counts)
        assertEquals(2, hints.spotlight?.playId)
    }

    @Test
    fun `counted keys survive inputMade, which clears the spotlight in the same call`() {
        game.step = "a"
        press()
        hints.inputMade()
        assertNull(hints.spotlight)

        press()
        assertEquals(listOf(1), counts)
        assertEquals(2, hints.spotlight?.playId)
    }

    @Test
    fun `a culprit shown then inputMade is a plain free show, not an erase`() {
        game.step = "c"
        game.culprits += "c"
        press()
        hints.inputMade()
        press()

        assertTrue(game.erased.isEmpty())
        assertEquals(2, hints.spotlight?.playId)
        assertEquals(listOf(1), counts)
    }

    @Test
    fun `reset clears the spotlight and forgets counted keys`() {
        game.step = "a"
        press()
        used = 0
        hints.reset()
        assertNull(hints.spotlight)

        press()
        assertEquals(listOf(1, 1), counts)
    }

    @Test
    fun `game hooks run once, after the spotlight clear`() {
        game.step = "a"
        press()
        hints.inputMade()
        assertEquals(listOf<Spotlight?>(null), game.spotlightSeenInInputMade)
        assertEquals(0, game.resets)

        game.step = "b"
        press()
        hints.reset()
        assertEquals(1, game.resets)
        assertNull(game.spotlightSeenInReset)
        assertEquals(1, game.spotlightSeenInInputMade.size)
    }

    @Test
    fun `a culprit erase calls game inputMade once`() {
        game.step = "c"
        game.culprits += "c"
        press()
        press()

        assertEquals(1, game.spotlightSeenInInputMade.size)
    }

    @Test
    fun `a game without hook overrides works`() {
        val plain = object : StepHints.Game<String, String> {
            override fun capture(): () -> String = { "a" }
            override fun keyOf(step: String) = step
            override fun isCulprit(step: String) = false
            override fun spotlight(step: String, playId: Int) = spotlight(playId)
            override fun eraseCulprit(step: String) = Unit
        }
        val plainHints = StepHints(HintMode.Stepwise(compute), scope, plain, used = { used }, onCount = { counts += it })

        plainHints.press()
        scheduler.runCurrent()
        plainHints.inputMade()
        plainHints.reset()

        assertEquals(listOf(1), counts)
    }

    @Test
    fun `at the ceiling a new key is not counted but still shows`() {
        used = MAX_HINTS_USED
        game.step = "a"
        press()

        assertTrue(counts.isEmpty())
        assertEquals(1, hints.spotlight?.playId)
    }

    @Test
    fun `capture runs on the calling thread and the block on the compute dispatcher`() {
        game.step = "a"
        val caller = Thread.currentThread()
        hints.press()
        assertEquals(caller, game.captureThread)
        assertFalse(game.captureInCompute)

        scheduler.runCurrent()
        assertTrue(game.blockInCompute)
    }

    @Test
    fun `a throwing compute is no hint and the next press searches again`() {
        game.step = "a"
        game.throwing = true
        press()
        assertTrue(scope.isActive)
        assertNull(hints.spotlight)
        assertTrue(counts.isEmpty())

        game.throwing = false
        press()
        assertEquals(listOf(1), counts)
        assertEquals(1, hints.spotlight?.playId)
    }

    private class FlaggingDispatcher(private val inner: CoroutineDispatcher) : CoroutineDispatcher() {
        @Volatile
        var running = false

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            inner.dispatch(context, Runnable {
                running = true
                try {
                    block.run()
                } finally {
                    running = false
                }
            })
        }
    }

    private inner class FakeGame : StepHints.Game<String, String> {
        var step = ""
        var throwing = false
        val culprits = mutableSetOf<String>()
        val nullKeys = mutableSetOf<String>()
        val erased = mutableListOf<String>()
        val spotlighted = mutableListOf<String>()
        val spotlightSeenInInputMade = mutableListOf<Spotlight?>()
        var spotlightSeenInReset: Spotlight? = Spotlight(0, 0, emptyMap(), null, NoGlyphs, 0)
        var resets = 0
        var captures = 0
        var captureThread: Thread? = null
        var captureInCompute = false
        var blockInCompute = false

        override fun capture(): () -> String {
            captures++
            captureThread = Thread.currentThread()
            captureInCompute = compute.running
            val snapshot = step
            val fail = throwing
            return {
                blockInCompute = compute.running
                if (fail) error("boom")
                snapshot
            }
        }

        override fun keyOf(step: String): String? = step.takeUnless { it in nullKeys }
        override fun isCulprit(step: String) = step in culprits
        override fun spotlight(step: String, playId: Int): Spotlight {
            spotlighted += step
            return spotlight(playId)
        }

        override fun eraseCulprit(step: String) {
            erased += step
        }

        override fun inputMade() {
            spotlightSeenInInputMade += hints.spotlight
        }

        override fun reset() {
            resets++
            spotlightSeenInReset = hints.spotlight
        }
    }

    private fun spotlight(playId: Int) = Spotlight(1, 1, emptyMap(), null, NoGlyphs, playId)
}

private object NoGlyphs : SpotlightGlyphs {
    override fun DrawScope.draw(cell: Int, role: SpotlightRole, size: Size, color: Color) = Unit
}
