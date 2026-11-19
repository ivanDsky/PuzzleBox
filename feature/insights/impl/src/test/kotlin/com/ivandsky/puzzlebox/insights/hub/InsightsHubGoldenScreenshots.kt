package com.ivandsky.puzzlebox.insights.hub

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.ivandsky.puzzlebox.insights.GOLDEN_PHONE
import com.ivandsky.puzzlebox.insights.GOLDEN_TABLET
import com.ivandsky.puzzlebox.insights.domain.recap.RecapPeriod
import com.ivandsky.puzzlebox.insights.settleAndCapture
import com.ivandsky.puzzlebox.insights.showGolden
import java.time.YearMonth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Insights hub on a phone and on the landscape tablet, light and dark, plus its waiting states. Goldens in `snapshots/hub/`. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class InsightsHubGoldenScreenshots {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val months = listOf(
        RecapPeriod.Month(YearMonth.of(2026, 11)),
        RecapPeriod.Month(YearMonth.of(2026, 10)),
        RecapPeriod.Month(YearMonth.of(2026, 9)),
        RecapPeriod.Month(YearMonth.of(2026, 7)),
        RecapPeriod.Month(YearMonth.of(2025, 12)),
    )

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `list phone light`() = capture("list", "phone", dark = false)

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `list phone dark`() = capture("list", "phone", dark = true)

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `list tablet light`() = capture("list", "tablet", dark = false)

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `list tablet dark`() = capture("list", "tablet", dark = true)

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `restoring phone light`() = capture("restoring", "phone", dark = false, state = InsightsHubUiState.Restoring)

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `empty phone light`() = capture("empty", "phone", dark = false, state = InsightsHubUiState.Empty)

    private fun capture(name: String, device: String, dark: Boolean, state: InsightsHubUiState = InsightsHubUiState.Ready(months)) {
        rule.showGolden(dark = dark) { InsightsHubScreen(state, onOpen = {}, onBack = {}, onOpenStats = {}) }
        rule.settleAndCapture("hub", name, device, dark)
    }
}
