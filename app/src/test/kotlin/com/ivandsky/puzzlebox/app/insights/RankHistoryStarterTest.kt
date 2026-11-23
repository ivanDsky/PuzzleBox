package com.ivandsky.puzzlebox.app.insights

import com.ivandsky.puzzlebox.insights.flags.InsightsFlag
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/** The filler starts once per process, and only while the stats page or the year recap is on. */
class RankHistoryStarterTest {

    private val scheduler = TestCoroutineScheduler()
    private val scope = CoroutineScope(StandardTestDispatcher(scheduler) + Job())
    private var started = 0

    @After
    fun stopScope() {
        scope.cancel()
    }

    private fun start(vararg on: InsightsFlag, times: Int = 1) {
        val starter = RankHistoryStarter(FakeReleaseFlags(*on), fill = { started++ }, scope = scope)
        repeat(times) { starter.start() }
        scheduler.advanceUntilIdle()
    }

    @Test
    fun `with every flag off the filler is not started`() {
        start()

        assertEquals(0, started)
    }

    @Test
    fun `the stats page starts it`() {
        start(InsightsFlag.StatsPage)

        assertEquals(1, started)
    }

    @Test
    fun `the year recap starts it`() {
        start(InsightsFlag.YearRecap)

        assertEquals(1, started)
    }

    @Test
    fun `starting twice starts it once`() {
        start(InsightsFlag.StatsPage, InsightsFlag.YearRecap, times = 2)

        assertEquals(1, started)
    }
}
