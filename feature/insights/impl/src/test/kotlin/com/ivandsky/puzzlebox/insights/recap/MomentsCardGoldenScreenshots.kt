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

/** Moments card renders on phone and tablet, light and dark. Goldens in `snapshots/recap/`. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class MomentsCardGoldenScreenshots {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `moments phone light`() = capture("moments", "phone", dark = false, state = readyOf(RecapFixtures.moments))

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `moments phone dark`() = capture("moments", "phone", dark = true, state = readyOf(RecapFixtures.moments))

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `moments tablet light`() = capture("moments", "tablet", dark = false, state = readyOf(RecapFixtures.solved, RecapFixtures.moments).copy(initialPage = 1))

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `moments tablet dark`() = capture("moments", "tablet", dark = true, state = readyOf(RecapFixtures.solved, RecapFixtures.moments).copy(initialPage = 1))

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
