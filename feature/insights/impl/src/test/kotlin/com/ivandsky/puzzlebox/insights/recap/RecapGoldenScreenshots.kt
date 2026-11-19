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

/** The recap story on a phone and on the landscape tablet, light and dark, plus its waiting state. Goldens in `snapshots/recap/`. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class RecapGoldenScreenshots {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `story phone light`() = capture("story", "phone", dark = false)

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `story phone dark`() = capture("story", "phone", dark = true)

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `story tablet light`() = capture("story", "tablet", dark = false)

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `story tablet dark`() = capture("story", "tablet", dark = true)

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `story second card phone light`() = capture("story-second-card", "phone", dark = false, initialPage = 1)

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `restoring phone light`() = capture("restoring", "phone", dark = false, state = RecapUiState.Restoring)

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `empty phone light`() = capture("empty", "phone", dark = false, state = RecapUiState.Empty)

    private fun capture(
        name: String,
        device: String,
        dark: Boolean,
        initialPage: Int = 0,
        state: RecapUiState = ready.copy(initialPage = initialPage),
    ) {
        rule.showGolden(dark = dark) {
            RecapScreen(state, "November 2026", gameName, onPage = {}, onClose = {})
        }
        rule.settleAndCapture("recap", name, device, dark)
    }
}
