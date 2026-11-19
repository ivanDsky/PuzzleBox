package com.ivandsky.puzzlebox.benchmark

import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.Direction
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val BOARD_SWIPES = 2
private const val SWIPE_STEPS = 20

/**
 * Ranks as a signed-out player sees it: sign-in prompt in place of boards. Opening it, the day
 * arrows, the When sheet, and swiping between boards. Real-board flows (scroll, refresh,
 * replay) need an account and stay out of the benchmark install.
 */
@RunWith(AndroidJUnit4::class)
class RanksBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    /**
     * Swipes to the next or previous board. The phone has a pager with dots, swiped across the
     * screen's middle (the indicator strip is too thin to grab). Two-pane has no dots: a swipe
     * anywhere in the right pane re-ranks the board, and the left pane (calendar and list) takes
     * none, so the swipe stays between 60% and 90% of the width, right of the left pane's edge.
     */
    private fun MacrobenchmarkScope.swipeBoards(direction: Direction) {
        val twoPane = isTwoPane()
        if (twoPane) waitFor(BenchmarkTab.Ranks.ready) else waitFor(By.descStartsWith("Ranks page "))
        val width = device.displayWidth
        val y = device.displayHeight / 2
        val (low, high) = if (twoPane) width * 3 / 5 to width * 9 / 10 else width / 5 to width * 4 / 5
        val (from, to) = if (direction == Direction.LEFT) high to low else low to high
        device.swipe(from, y, to, y, SWIPE_STEPS)
    }

    @Test
    fun open() = rule.journey {
        selectTab(BenchmarkTab.Ranks)
        selectTab(BenchmarkTab.Today)
    }

    @Test
    fun scopePeriodAndBoards() = rule.journey(setup = { selectTab(BenchmarkTab.Ranks) }) {
        // Each pick closes the sheet; the window starts on Day, so the row tapped is never also the chip's text.
        listOf("Week", "Month", "Day").forEach {
            tap(RANKS_WHEN_CHIP)
            tap(By.text(it))
        }
        repeat(2) { tap(By.desc("Back")) }
        repeat(2) { tap(By.desc("Forward")) }
        repeat(BOARD_SWIPES) { swipeBoards(Direction.LEFT) }
        repeat(BOARD_SWIPES) { swipeBoards(Direction.RIGHT) }
        selectTab(BenchmarkTab.Today)
    }
}
