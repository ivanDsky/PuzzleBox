package com.ivandsky.puzzlebox.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the baseline profile from the critical journeys: cold start, all four tabs, and every
 * game's page and board. Run on demand with `connectedBenchmarkAndroidTest` and
 * `-Ppuzzlebox.baselineProfileGeneration=true`; its filtered output is the committed
 * `app/src/main/baseline-prof.txt` (command and filter in `benchmark/README.md`).
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = TARGET_PACKAGE, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        ensureToday()
        BenchmarkGame.entries.forEach { game ->
            skipTutorial(game)
            selectTab(BenchmarkTab.Games)
            openGamePage(game)
            startBoard()
            leaveGame()
        }
        selectTab(BenchmarkTab.Ranks)
        tap(RANKS_WHEN_CHIP)
        tap(By.desc("Close"))
        selectTab(BenchmarkTab.Community)
        selectTab(BenchmarkTab.Today)
    }
}
