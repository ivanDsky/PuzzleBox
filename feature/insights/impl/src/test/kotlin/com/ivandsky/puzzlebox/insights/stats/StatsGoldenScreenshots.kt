package com.ivandsky.puzzlebox.insights.stats

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.ivandsky.puzzlebox.insights.GOLDEN_PHONE
import com.ivandsky.puzzlebox.insights.GOLDEN_TABLET
import com.ivandsky.puzzlebox.insights.domain.stats.HintFreeRateSeries
import com.ivandsky.puzzlebox.insights.domain.stats.SectionState
import com.ivandsky.puzzlebox.insights.domain.stats.StatsGranularity
import com.ivandsky.puzzlebox.insights.domain.stats.StatsRange
import com.ivandsky.puzzlebox.insights.domain.stats.TimeTrendSeries
import com.ivandsky.puzzlebox.insights.settleAndCapture
import com.ivandsky.puzzlebox.insights.showGolden
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The stats page on a phone and on the landscape tablet, light and dark, plus its empty and unavailable states. Goldens in `snapshots/stats/`. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class StatsGoldenScreenshots {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `records phone light`() = capture("records", "phone", dark = false)

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `records phone dark`() = capture("records", "phone", dark = true)

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `records tablet light`() = capture("records", "tablet", dark = false)

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `records tablet dark`() = capture("records", "tablet", dark = true)

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `one game week phone light`() = capture("one-game-week", "phone", dark = false, state = StatsFixtures.ready(StatsRange.Week, StatsFixtures.zip))

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `one game all tablet dark`() = capture("one-game-all", "tablet", dark = true, state = StatsFixtures.ready(StatsRange.All, StatsFixtures.queens))

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `lines phone light`() = capture("lines", "phone", dark = false, state = StatsFixtures.readyWithLines())

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `lines phone dark`() = capture("lines", "phone", dark = true, state = StatsFixtures.readyWithLines())

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `lines tablet dark`() = capture("lines", "tablet", dark = true, state = StatsFixtures.readyWithLines(StatsRange.All))

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `single points phone light`() = capture(
        "single-points", "phone", dark = false,
        state = StatsFixtures.readyWithLines(
            StatsRange.Week,
            trend = TimeTrendSeries(StatsGranularity.Day, StatsFixtures.trend.points.take(1)),
            hintFree = HintFreeRateSeries(StatsGranularity.Day, StatsFixtures.hintFreeRate.points.take(1)),
        ),
    )

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `unavailable phone light`() = capture("unavailable", "phone", dark = false, state = StatsFixtures.ready(state = SectionState.Unavailable))

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `empty phone light`() = capture("empty", "phone", dark = false, state = StatsUiState(StatsRange.Week, null, StatsBody.Empty))

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `restoring phone dark`() = capture("restoring", "phone", dark = true, state = StatsUiState(StatsRange.Month, null, StatsBody.Restoring))

    private fun capture(name: String, device: String, dark: Boolean, state: StatsUiState = StatsFixtures.ready()) {
        rule.showGolden(dark = dark) {
            StatsScreen(
                state = state,
                games = StatsFixtures.games,
                gameName = StatsFixtures::gameName,
                onSelectRange = {},
                onSelectGame = {},
                onOpenSolve = {},
                onBack = {},
            )
        }
        rule.settleAndCapture("stats", name, device, dark)
    }
}
