package com.ivandsky.puzzlebox.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the baseline profile from the critical journeys: cold start, all four tabs, and every
 * game's page and board. Wired to :app through the baselineprofile plugin and run on demand with
 * `./gradlew :app:generateBaselineProfile`. No profile is committed yet: shipping it is the S-01
 * fix, so its delta is measured against the profile-less baseline this module records today.
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
        tap(By.desc("Tune ranks"))
        tap(By.desc("Close sheet"))
        selectTab(BenchmarkTab.Create)
        selectTab(BenchmarkTab.Today)
    }
}
