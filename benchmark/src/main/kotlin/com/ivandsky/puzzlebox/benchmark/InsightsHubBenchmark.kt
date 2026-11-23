package com.ivandsky.puzzlebox.benchmark

import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val FLINGS = 4

/**
 * The Insights hub's grid of year and month tiles over two years of solved history (`SeededHistory`): flung
 * down and up four times each. Two journeys: `scroll` measures the fling alone on a hub already open, and
 * `openAndScroll` includes the open, where the first screenful of tiles plays its entry motion.
 */
@RunWith(AndroidJUnit4::class)
class InsightsHubBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun scroll() = rule.journey(
        data = DataState.SeededHistory,
        setup = { openInsightsHub() },
    ) {
        flingHub(FLINGS)
        leaveInsightsHub()
    }

    @Test
    fun openAndScroll() = rule.journey(data = DataState.SeededHistory) {
        openInsightsHub()
        flingHub(FLINGS)
        leaveInsightsHub()
    }
}
