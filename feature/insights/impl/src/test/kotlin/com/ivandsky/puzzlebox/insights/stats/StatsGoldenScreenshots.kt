package com.ivandsky.puzzlebox.insights.stats

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.ivandsky.puzzlebox.insights.GOLDEN_PHONE
import com.ivandsky.puzzlebox.insights.GOLDEN_TABLET
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performScrollToNode
import com.ivandsky.puzzlebox.insights.domain.stats.GameStanding
import com.ivandsky.puzzlebox.insights.domain.stats.HintFreeRateSeries
import com.ivandsky.puzzlebox.insights.domain.stats.SectionState
import com.ivandsky.puzzlebox.insights.domain.stats.StatsGranularity
import com.ivandsky.puzzlebox.insights.domain.stats.StatsRange
import com.ivandsky.puzzlebox.insights.domain.stats.TimeTrendSeries
import com.ivandsky.puzzlebox.insights.domain.stats.VsFriendsStanding
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
    @Test fun `weekday and friends phone light`() = capture("weekday-friends", "phone", dark = false, state = StatsFixtures.readyWithLines(), scrollTo = "stats.weekday")

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `weekday and friends phone dark`() = capture("weekday-friends", "phone", dark = true, state = StatsFixtures.readyWithLines(), scrollTo = "stats.weekday")

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `friends signed out phone light`() = capture(
        "friends-signed-out", "phone", dark = false, scrollTo = "stats.vsFriends.locked",
        state = StatsFixtures.readyWithLines(vsFriends = SectionState.Ready(VsFriendsStanding.Locked(VsFriendsStanding.Unlock.SignIn))),
    )

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `friends unavailable phone dark`() = capture(
        "friends-unavailable", "phone", dark = true, scrollTo = "stats.vsFriends",
        state = StatsFixtures.readyWithLines(vsFriends = SectionState.Unavailable),
    )

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `friends one game phone light`() = capture(
        "friends-one-game", "phone", dark = false, scrollTo = "stats.vsFriends",
        state = StatsFixtures.readyWithLines(
            range = StatsRange.Week,
            game = StatsFixtures.queens,
            vsFriends = SectionState.Ready(VsFriendsStanding.Ranked(GameStanding(StatsFixtures.queens, 90.0, place = 2, players = 3), weakest = null)),
        ),
    )

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `unavailable phone light`() = capture("unavailable", "phone", dark = false, state = StatsFixtures.ready(state = SectionState.Unavailable))

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `empty phone light`() = capture("empty", "phone", dark = false, state = StatsUiState(StatsRange.Week, null, StatsBody.Empty))

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `restoring phone dark`() = capture("restoring", "phone", dark = true, state = StatsUiState(StatsRange.Month, null, StatsBody.Restoring))

    private fun capture(name: String, device: String, dark: Boolean, state: StatsUiState = StatsFixtures.ready(), scrollTo: String? = null) {
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
        if (scrollTo != null) {
            // The clock is frozen for the capture; scrolling needs it running.
            rule.mainClock.autoAdvance = true
            rule.onNodeWithTag("stats.sections").performScrollToNode(hasTestTag(scrollTo))
        }
        rule.settleAndCapture("stats", name, device, dark)
    }
}
