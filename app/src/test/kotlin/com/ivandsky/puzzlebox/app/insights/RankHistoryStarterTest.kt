package com.ivandsky.puzzlebox.app.insights

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/** The filler starts once per process. */
class RankHistoryStarterTest {

    private val scheduler = TestCoroutineScheduler()
    private val scope = CoroutineScope(StandardTestDispatcher(scheduler) + Job())
    private var started = 0

    @After
    fun stopScope() {
        scope.cancel()
    }

    private fun start(times: Int = 1) {
        val starter = RankHistoryStarter(fill = { started++ }, scope = scope)
        repeat(times) { starter.start() }
        scheduler.advanceUntilIdle()
    }

    @Test
    fun `starting launches the filler`() {
        start()

        assertEquals(1, started)
    }

    @Test
    fun `starting twice starts it once`() {
        start(times = 2)

        assertEquals(1, started)
    }
}
