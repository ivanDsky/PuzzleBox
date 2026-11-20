package com.ivandsky.puzzlebox.insights.recap

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.ivandsky.puzzlebox.insights.GOLDEN_PHONE
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

/** Solved card renders on phone, light and dark. Goldens in `snapshots/recap/`. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class SolvedCardGoldenScreenshots {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `year so far totals phone light`() = capture("year-so-far", "phone", dark = false, state = readyYear("2026 so far", RecapFixtures.solved, RecapFixtures.march))

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `year so far totals phone dark`() = capture("year-so-far", "phone", dark = true, state = readyYear("2026 so far", RecapFixtures.solved, RecapFixtures.march))

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `year imported phone light`() = capture("year-imported", "phone", dark = false, state = readyYear("2025", RecapFixtures.solvedImported, RecapFixtures.march))

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
