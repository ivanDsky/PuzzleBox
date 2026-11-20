package com.ivandsky.puzzlebox.insights.recap

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.ivandsky.puzzlebox.insights.GOLDEN_PHONE
import com.ivandsky.puzzlebox.insights.GOLDEN_TABLET
import com.ivandsky.puzzlebox.insights.recap.RecapFixtures.gameName
import com.ivandsky.puzzlebox.insights.recap.RecapFixtures.ready
import com.ivandsky.puzzlebox.insights.settleAndCapture
import com.ivandsky.puzzlebox.insights.showGolden
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** TotalTime card renders on phone and tablet, light and dark. Goldens in `snapshots/recap/`. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class TotalTimeCardGoldenScreenshots {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `total-time phone light`() = capture("total-time", "phone", dark = false, state = readyOf(RecapFixtures.totalTime))

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `total-time phone dark`() = capture("total-time", "phone", dark = true, state = readyOf(RecapFixtures.totalTime))

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `total-time tablet light`() = capture("total-time", "tablet", dark = false, state = readyOf(RecapFixtures.solved, RecapFixtures.totalTime).copy(initialPage = 1))

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `total-time tablet dark`() = capture("total-time", "tablet", dark = true, state = readyOf(RecapFixtures.solved, RecapFixtures.totalTime).copy(initialPage = 1))

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `total-time-year phone light`() = capture("total-time-year", "phone", dark = false, state = readyOf(RecapFixtures.totalTimeYear))

    private fun capture(
        name: String,
        device: String,
        dark: Boolean,
        initialPage: Int = 0,
        state: RecapUiState = ready.copy(initialPage = initialPage),
    ) {
        rule.showGolden(dark = dark) {
            RecapScreen(state, "November 2026", gameName, onPage = {}, onWatchReplay = {}, onClose = {})
        }
        rule.settleAndCapture("recap", name, device, dark)
    }
}
