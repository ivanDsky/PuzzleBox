package com.ivandsky.puzzlebox.gameui.hint

import android.util.Log
import com.ivandsky.puzzlebox.designsystem.spotlight.Spotlight
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.ivandsky.puzzlebox.designsystem.spotlight.SpotlightGlyphs
import com.ivandsky.puzzlebox.designsystem.spotlight.SpotlightRole
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

/** Pins the press flow's counting, free repeat, show-only, stale-result and failure rules (spec A1, A4, A9, A10, A19). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
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
    fun `every press of the same key searches again, re-shows it for free and is never an input`() {
        game.step = "c"
        press()
        press()
        val capturesBefore = game.captures
        press()

        assertEquals(capturesBefore + 1, game.captures)
        assertEquals(listOf("c", "c", "c"), game.spotlighted)
        assertEquals(3, hints.spotlight?.playId)
        assertEquals(listOf(1), counts)
        assertTrue(game.spotlightSeenInInputMade.isEmpty())
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
    fun `a game without hook overrides works`() {
        val plain = object : StepHints.Game<String, String> {
            override fun capture(): () -> String = { "a" }
            override fun keyOf(step: String) = step
            override fun spotlight(step: String, playId: Int) = spotlight(playId)
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
        // A plain Job, not SupervisorJob: a failure escaping the press would cancel this scope.
        val strictScope = CoroutineScope(Job() + StandardTestDispatcher(scheduler, name = "main"))
        val strictHints = StepHints(HintMode.Stepwise(compute), strictScope, game, used = { used }, onCount = { counts += it; used = it })
        game.step = "a"
        game.throwing = true
        strictHints.press()
        scheduler.runCurrent()
        assertTrue(strictScope.isActive)
        assertNull(strictHints.spotlight)
        assertTrue(counts.isEmpty())

        game.throwing = false
        strictHints.press()
        scheduler.runCurrent()
        assertEquals(listOf(1), counts)
        assertEquals(1, strictHints.spotlight?.playId)
    }

    @Test
    fun `a compute that throws an Exception is logged with its throwable under the StepHints tag`() {
        game.step = "a"
        game.throwing = true
        press()

        val logged = ShadowLog.getLogsForTag("StepHints")
        assertEquals(1, logged.size)
        assertEquals(Log.WARN, logged.single().type)
        assertEquals("boom", logged.single().throwable?.message)
    }

    @Test
    fun `an Error from the compute is not turned into no hint and reaches the scope's exception handler`() {
        val escaped = mutableListOf<Throwable>()
        val handlerScope = CoroutineScope(
            SupervisorJob() + StandardTestDispatcher(scheduler, name = "main") +
                CoroutineExceptionHandler { _, e -> escaped += e },
        )
        val errorHints = StepHints(HintMode.Stepwise(compute), handlerScope, game, used = { used }, onCount = { counts += it; used = it })
        game.step = "a"
        game.failWith = StackOverflowError("deep")
        errorHints.press()
        scheduler.runCurrent()

        assertEquals(listOf<Class<*>>(StackOverflowError::class.java), escaped.map { it.javaClass })
        assertNull(errorHints.spotlight)
        assertTrue(counts.isEmpty())
        assertTrue(ShadowLog.getLogsForTag("StepHints").isEmpty())
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
        var failWith: Error? = null
        val nullKeys = mutableSetOf<String>()
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
            val failure = failWith
            return {
                blockInCompute = compute.running
                failure?.let { throw it }
                if (fail) error("boom")
                snapshot
            }
        }

        override fun keyOf(step: String): String? = step.takeUnless { it in nullKeys }
        override fun spotlight(step: String, playId: Int): Spotlight {
            spotlighted += step
            return spotlight(playId)
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
