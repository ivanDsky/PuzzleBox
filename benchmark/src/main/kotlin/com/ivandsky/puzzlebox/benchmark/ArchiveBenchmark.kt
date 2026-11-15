package com.ivandsky.puzzlebox.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

private const val DAY_TAPS = 10
private const val MONTH_STEPS = 3
// Day discs read "#601"; the jump chip "#1" has one digit, and no game has fewer than 100 puzzles.
private val DayDisc = By.text(Pattern.compile("#\\d{3,}"))

/**
 * The Games tab's archive calendar on Zip's page: ten day taps and the month arrows back three
 * months and forward again. A past day is only selected, never opened.
 */
@RunWith(AndroidJUnit4::class)
class ArchiveBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun dayTapsAndMonthArrows() = rule.journey(
        setup = {
            selectTab(BenchmarkTab.Games)
            openGamePage(BenchmarkGame.Zip)
        },
    ) {
        repeat(DAY_TAPS) { tapDay(it) }
        repeat(MONTH_STEPS) { tap(By.desc("Previous month")) }
        tapDay(0)
        repeat(MONTH_STEPS) { tap(By.desc("Next month")) }
        // Back to the Games list and Today, so every run and the next setup start the same.
        tap(By.desc("Back"))
        waitFor(BenchmarkTab.Games.ready)
        selectTab(BenchmarkTab.Today)
    }

    /** Taps the [n]th day disc on screen, retrying when a recomposition swaps the node under it. */
    private fun MacrobenchmarkScope.tapDay(n: Int) {
        poll("a calendar day disc") {
            runCatching {
                val discs = device.findObjects(DayDisc)
                discs.getOrNull(n % discs.size.coerceAtLeast(1))?.also { it.click() }
            }.getOrNull()
        }
    }
}
