package com.ivandsky.puzzlebox.benchmark

import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Today to Games to Ranks to Create and back to Today, each tab waited on until its root composes. */
@RunWith(AndroidJUnit4::class)
class TabSwitchBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun allTabs() = rule.journey {
        selectTab(BenchmarkTab.Games)
        selectTab(BenchmarkTab.Ranks)
        selectTab(BenchmarkTab.Create)
        selectTab(BenchmarkTab.Today)
    }

    @Test
    fun todayAndGames() = rule.journey {
        selectTab(BenchmarkTab.Games)
        selectTab(BenchmarkTab.Today)
        selectTab(BenchmarkTab.Games)
        selectTab(BenchmarkTab.Today)
    }

    @Test
    fun createOpen() = rule.journey {
        selectTab(BenchmarkTab.Create)
        selectTab(BenchmarkTab.Today)
    }
}
