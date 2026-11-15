package com.ivandsky.puzzlebox.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By

/** The bottom bar sits in the lowest tenth of the screen; the two-pane rail is 96 dp wide at the left. */
private const val BAR_TOP_SHARE = 0.88
private const val RAIL_WIDTH_DP = 96

/** The app's own rule (HomeLayout): two panes at Expanded width, 840 dp, held in landscape. */
private const val EXPANDED_WIDTH_DP = 840

private fun displayDensity(): Float =
    InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density

/** The two-pane rail's width in pixels. */
internal fun railWidthPx(): Float = RAIL_WIDTH_DP * displayDensity()

/**
 * True when the app shows its two-pane layout: a rail for the tabs, and a list beside its detail
 * on Games and Ranks. Journeys branch on this where the two layouts differ: there is no Back on a
 * game page that sits beside its list, and Ranks has no board dots.
 */
internal fun MacrobenchmarkScope.isTwoPane(): Boolean =
    device.displayWidth > device.displayHeight && device.displayWidth / displayDensity() >= EXPANDED_WIDTH_DP

/** Selects [tab] and waits for its root to compose. Safe when it is already selected. */
internal fun MacrobenchmarkScope.selectTab(tab: BenchmarkTab) {
    tapNavLabel(tab)
    waitFor(tab.ready)
}

/** Brings the app to a settled Today tab: the one every journey starts and ends on. */
internal fun MacrobenchmarkScope.ensureToday() {
    if (!isShown(BenchmarkTab.Today.ready)) selectTab(BenchmarkTab.Today)
}

/**
 * Taps the tab's label in the bar or rail. The same word is also a screen title ("Games"), so the
 * match must sit where the navigation is, not just carry the text.
 */
private fun MacrobenchmarkScope.tapNavLabel(tab: BenchmarkTab) {
    val barTop = device.displayHeight * BAR_TOP_SHARE
    val railRight = railWidthPx()
    poll("the ${tab.label} tab in the navigation") {
        device.findObjects(By.text(tab.label)).firstOrNull { label ->
            val center = label.visibleCenter
            center.y > barTop || center.x < railRight
        }?.also { it.click() }
    }
}
