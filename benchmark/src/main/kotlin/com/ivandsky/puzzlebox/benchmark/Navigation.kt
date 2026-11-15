package com.ivandsky.puzzlebox.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiObject2

/** The bottom bar sits in the lowest tenth of the screen; the two-pane rail is 96 dp wide at the left. */
private const val BAR_TOP_SHARE = 0.88
private const val RAIL_WIDTH_DP = 96

/** Selects [tab] and waits for its root to compose. Safe when it is already selected. */
internal fun MacrobenchmarkScope.selectTab(tab: BenchmarkTab) {
    navLabel(tab).click()
    waitFor(tab.ready)
}

/** Brings the app to a settled Today tab: the one every journey starts and ends on. */
internal fun MacrobenchmarkScope.ensureToday() {
    if (!isShown(BenchmarkTab.Today.ready)) selectTab(BenchmarkTab.Today)
}

/**
 * The tab's label in the bar or rail. The same word is also a screen title ("Games"), so the
 * match must sit where the navigation is, not just carry the text.
 */
private fun MacrobenchmarkScope.navLabel(tab: BenchmarkTab): UiObject2 {
    val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
    val barTop = device.displayHeight * BAR_TOP_SHARE
    val railRight = RAIL_WIDTH_DP * density
    return poll("the ${tab.label} tab in the navigation") {
        device.findObjects(By.text(tab.label)).firstOrNull { label ->
            val center = label.visibleCenter
            center.y > barTop || center.x < railRight
        }
    }
}
