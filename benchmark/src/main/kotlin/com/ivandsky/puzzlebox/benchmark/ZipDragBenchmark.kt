package com.ivandsky.puzzlebox.benchmark

import android.graphics.Point
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val DRAG_STEPS = 30
private const val DRAGS = 3
private const val BOARD_PANE_SHARE = 0.7f

/**
 * Drags a path out of Zip's waypoint 1 toward waypoint 2 on the live board and clears it, three
 * times: the per-frame cost of board input. The path is an L between the two numbers, never a
 * full solution, so no puzzle is finished.
 */
@RunWith(AndroidJUnit4::class)
class ZipDragBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    private var tutorialSkipped = false

    @Test
    fun dragAndClear() = rule.journey(
        setup = {
            if (!tutorialSkipped) {
                skipTutorial(BenchmarkGame.Zip)
                tutorialSkipped = true
            }
            selectTab(BenchmarkTab.Games)
            openGamePage(BenchmarkGame.Zip)
            startBoard()
        },
    ) {
        repeat(DRAGS) {
            dragOneToTwo()
            tap(By.text("Clear"))
        }
        leaveGame()
        selectTab(BenchmarkTab.Today)
    }

    private fun MacrobenchmarkScope.dragOneToTwo() {
        val one = waypoint("1")
        val two = waypoint("2")
        device.swipe(arrayOf(Point(one.x, one.y), Point(two.x, one.y), Point(two.x, two.y)), DRAG_STEPS)
    }

    /**
     * The centre of the board's waypoint [number]. Two-pane puts a stats panel beside the board
     * (the board pane is the left 70% of the window; the game page is immersive, so no rail), and a
     * bare digit there is not a waypoint, so only a match inside the board pane counts.
     */
    private fun MacrobenchmarkScope.waypoint(number: String): Point {
        val boardRight = if (isTwoPane()) device.displayWidth * BOARD_PANE_SHARE else device.displayWidth.toFloat()
        return poll("the waypoint $number on the board") {
            device.findObjects(By.text(number)).map { it.visibleCenter }.firstOrNull { it.x < boardRight }
        }
    }
}
