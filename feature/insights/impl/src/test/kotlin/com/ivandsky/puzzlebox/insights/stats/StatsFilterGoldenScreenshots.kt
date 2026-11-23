package com.ivandsky.puzzlebox.insights.stats

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.ivandsky.puzzlebox.insights.GOLDEN_PHONE
import com.ivandsky.puzzlebox.insights.GOLDEN_TABLET
import com.ivandsky.puzzlebox.insights.domain.stats.StatsRange
import com.ivandsky.puzzlebox.insights.settleAndCapture
import com.ivandsky.puzzlebox.insights.showGolden
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The stats page with the filters flag on, on a phone and the landscape tablet, light and dark, no game picked and one picked (the sheets are in a separate window the capture does not take; `StatsFilterBarTest` covers them). Captures in `snapshots/stats-filters/`. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class StatsFilterGoldenScreenshots {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Config(qualifiers = GOLDEN_PHONE) @Test fun `unset phone light`() = capture("unset", "phone", dark = false)

    @Config(qualifiers = GOLDEN_PHONE) @Test fun `unset phone dark`() = capture("unset", "phone", dark = true)

    @Config(qualifiers = GOLDEN_PHONE) @Test fun `set phone light`() = capture("set", "phone", dark = false, game = true)

    @Config(qualifiers = GOLDEN_PHONE) @Test fun `set phone dark`() = capture("set", "phone", dark = true, game = true)

    @Config(qualifiers = GOLDEN_TABLET) @Test fun `unset tablet light`() = capture("unset", "tablet", dark = false)

    @Config(qualifiers = GOLDEN_TABLET) @Test fun `unset tablet dark`() = capture("unset", "tablet", dark = true)

    @Config(qualifiers = GOLDEN_TABLET) @Test fun `set tablet light`() = capture("set", "tablet", dark = false, game = true)

    @Config(qualifiers = GOLDEN_TABLET) @Test fun `set tablet dark`() = capture("set", "tablet", dark = true, game = true)

    private fun capture(name: String, device: String, dark: Boolean, game: Boolean = false) {
        val state = StatsFixtures.ready(
            range = if (game) StatsRange.Month else StatsRange.Week,
            game = StatsFixtures.zip.takeIf { game },
            since = StatsFixtures.since,
        )
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
        rule.settleAndCapture("stats-filters", name, device, dark)
    }
}
