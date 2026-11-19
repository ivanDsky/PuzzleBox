package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.common.log.FailureLog
import java.io.IOException
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The guard both provider chains share: an answer, or nothing, and never a failure past it. */
class GuardedAskTest {

    private val warnings = mutableListOf<Triple<String, String, Throwable>>()
    private val failures = FailureLog { tag, message, cause -> warnings += Triple(tag, message, cause) }

    @Test
    fun `an answer comes back`() = runTest {
        assertEquals("answer", guardedAsk(failures, "Chain", "a provider") { "answer" })
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun `nothing is nothing, and not a failure`() = runTest {
        assertNull(guardedAsk<String>(failures, "Chain", "a provider") { null })
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun `a throwing provider is nothing and logged once under its tag and name`() = runTest {
        val cause = IOException("board read failed")

        assertNull(guardedAsk<String>(failures, "Chain", "provider Slow") { throw cause })

        assertEquals(listOf(Triple("Chain", "provider Slow failed", cause as Throwable)), warnings)
    }

    @Test
    fun `a provider past the limit is nothing, with no log`() = runTest {
        assertNull(guardedAsk<String>(failures, "Chain", "a provider", timeout = 2.seconds) { delay(10.seconds); "late" })

        assertEquals(2_000L, currentTime)
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun `no limit lets a slow local provider finish`() = runTest {
        assertEquals("late", guardedAsk(failures, "Chain", "a provider") { delay(60.seconds); "late" })
    }

    @Test
    fun `a provider whose own work was cancelled is only nothing`() = runTest {
        assertNull(guardedAsk<String>(failures, "Chain", "a provider") { throw kotlinx.coroutines.CancellationException("inner") })
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun `the caller leaving cancels the guard`() = runTest {
        val started = CompletableDeferred<Unit>()
        val ask = async(start = CoroutineStart.UNDISPATCHED) {
            guardedAsk<String>(failures, "Chain", "a provider") { started.complete(Unit); awaitCancellation() }
        }
        started.await()

        ask.cancel()
        advanceUntilIdle()

        assertTrue(ask.isCancelled)
        assertTrue(warnings.isEmpty())
    }
}
