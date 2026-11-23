package com.ivandsky.puzzlebox.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

private const val SEED_SETTLE_MS = 1_000L
private val ProfileButton = By.desc("Profile")
private val InsightsRow = By.text("Insights")
private val Back = By.desc("Back")

/** The hub's first row, on screen once its grid has composed. */
internal val HubReady = By.text("Stats for all games")

/**
 * Writes two years of solved dailies into the benchmark install's history through its benchmark-build
 * content provider (`BenchmarkSeedProvider`). Idempotent: days already stored are skipped. The call needs the
 * app process up, which [startActivityAndWait] has done.
 */
internal fun MacrobenchmarkScope.seedHistory() {
    val out = device.executeShellCommand("content call --uri content://$TARGET_PACKAGE.seed --method seed")
    check("written" in out || "Result" in out) { "History seed failed: $out" }
    Thread.sleep(SEED_SETTLE_MS)
}

/** From Today: Profile, then its Insights row, then the hub's first row. */
internal fun MacrobenchmarkScope.openInsightsHub() {
    tap(ProfileButton)
    tap(InsightsRow)
    waitFor(HubReady)
}

/** From the hub: Back out of it and out of Profile, onto Today. */
internal fun MacrobenchmarkScope.leaveInsightsHub() {
    tap(Back)
    device.wait(Until.gone(HubReady), WAIT_MS)
    tap(Back)
    waitFor(BenchmarkTab.Today.ready)
}

/**
 * Flings the hub's grid [times] down and then [times] up, from the screen's middle third so the swipe
 * starts on the grid and clears the bars. Each fling is one gesture at flick speed that the grid keeps scrolling
 * after the finger lifts.
 */
internal fun MacrobenchmarkScope.flingHub(times: Int) {
    val x = device.displayWidth / 2
    val top = (device.displayHeight * FLING_TOP_SHARE).toInt()
    val bottom = (device.displayHeight * FLING_BOTTOM_SHARE).toInt()
    repeat(times) { device.swipe(x, bottom, x, top, FLING_STEPS) }
    repeat(times) { device.swipe(x, top, x, bottom, FLING_STEPS) }
}

private const val FLING_TOP_SHARE = 0.25
private const val FLING_BOTTOM_SHARE = 0.8
private const val FLING_STEPS = 6
