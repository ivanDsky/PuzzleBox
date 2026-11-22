package com.ivandsky.puzzlebox.insights.stats.grouped

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import com.ivandsky.puzzlebox.insights.GOLDEN_PHONE
import com.ivandsky.puzzlebox.insights.GOLDEN_TABLET
import com.ivandsky.puzzlebox.insights.domain.recap.HintFreeRun
import com.ivandsky.puzzlebox.insights.domain.recap.SolveRef
import com.ivandsky.puzzlebox.insights.domain.recap.StreakRun
import com.ivandsky.puzzlebox.insights.domain.stats.RankHistoryStanding
import com.ivandsky.puzzlebox.insights.domain.stats.SectionState
import com.ivandsky.puzzlebox.insights.domain.stats.StatsRange
import com.ivandsky.puzzlebox.insights.domain.stats.StatsRecords
import com.ivandsky.puzzlebox.insights.domain.stats.StatsSection
import com.ivandsky.puzzlebox.insights.domain.stats.VsFriendsStanding
import com.ivandsky.puzzlebox.insights.settleAndCapture
import com.ivandsky.puzzlebox.insights.showGolden
import com.ivandsky.puzzlebox.insights.stats.StatsBody
import com.ivandsky.puzzlebox.insights.stats.StatsFixtures
import com.ivandsky.puzzlebox.insights.stats.StatsScreen
import com.ivandsky.puzzlebox.insights.stats.StatsUiState
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The grouped stats page (StatsFilters on) in its six states on a phone and the landscape tablet, light and dark: default
 * (one game, so rank history shows), any game, empty, loading, error, longest names and values, and locked friends.
 * Captures in `snapshots/stats-grouped/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class StatsGroupedGoldenScreenshots {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Config(qualifiers = GOLDEN_PHONE) @Test fun `default phone light`() = capture("default", "phone", false)
    @Config(qualifiers = GOLDEN_PHONE) @Test fun `default phone dark`() = capture("default", "phone", true)
    @Config(qualifiers = GOLDEN_TABLET) @Test fun `default tablet light`() = capture("default", "tablet", false)
    @Config(qualifiers = GOLDEN_TABLET) @Test fun `default tablet dark`() = capture("default", "tablet", true)

    @Config(qualifiers = GOLDEN_PHONE) @Test fun `anygame phone light`() = capture("anygame", "phone", false)
    @Config(qualifiers = GOLDEN_PHONE) @Test fun `anygame phone dark`() = capture("anygame", "phone", true)
    @Config(qualifiers = GOLDEN_TABLET) @Test fun `anygame tablet light`() = capture("anygame", "tablet", false)
    @Config(qualifiers = GOLDEN_TABLET) @Test fun `anygame tablet dark`() = capture("anygame", "tablet", true)

    @Config(qualifiers = GOLDEN_PHONE) @Test fun `empty phone light`() = capture("empty", "phone", false)
    @Config(qualifiers = GOLDEN_PHONE) @Test fun `empty phone dark`() = capture("empty", "phone", true)
    @Config(qualifiers = GOLDEN_TABLET) @Test fun `empty tablet light`() = capture("empty", "tablet", false)
    @Config(qualifiers = GOLDEN_TABLET) @Test fun `empty tablet dark`() = capture("empty", "tablet", true)

    @Config(qualifiers = GOLDEN_PHONE) @Test fun `loading phone light`() = capture("loading", "phone", false)
    @Config(qualifiers = GOLDEN_PHONE) @Test fun `loading phone dark`() = capture("loading", "phone", true)
    @Config(qualifiers = GOLDEN_TABLET) @Test fun `loading tablet light`() = capture("loading", "tablet", false)
    @Config(qualifiers = GOLDEN_TABLET) @Test fun `loading tablet dark`() = capture("loading", "tablet", true)

    @Config(qualifiers = GOLDEN_PHONE) @Test fun `error phone light`() = capture("error", "phone", false)
    @Config(qualifiers = GOLDEN_PHONE) @Test fun `error phone dark`() = capture("error", "phone", true)
    @Config(qualifiers = GOLDEN_TABLET) @Test fun `error tablet light`() = capture("error", "tablet", false)
    @Config(qualifiers = GOLDEN_TABLET) @Test fun `error tablet dark`() = capture("error", "tablet", true)

    @Config(qualifiers = GOLDEN_PHONE) @Test fun `longest phone light`() = capture("longest", "phone", false)
    @Config(qualifiers = GOLDEN_PHONE) @Test fun `longest phone dark`() = capture("longest", "phone", true)
    @Config(qualifiers = GOLDEN_TABLET) @Test fun `longest tablet light`() = capture("longest", "tablet", false)
    @Config(qualifiers = GOLDEN_TABLET) @Test fun `longest tablet dark`() = capture("longest", "tablet", true)

    @Config(qualifiers = GOLDEN_PHONE) @Test fun `locked phone light`() = capture("locked", "phone", false)
    @Config(qualifiers = GOLDEN_PHONE) @Test fun `locked phone dark`() = capture("locked", "phone", true)
    @Config(qualifiers = GOLDEN_TABLET) @Test fun `locked tablet light`() = capture("locked", "tablet", false)
    @Config(qualifiers = GOLDEN_TABLET) @Test fun `locked tablet dark`() = capture("locked", "tablet", true)

    private fun state(name: String): StatsUiState {
        val zip = StatsFixtures.zip
        val withLines = StatsFixtures.readyWithLines(range = StatsRange.Week, game = zip)
        val base = { s: StatsUiState -> s.copy(filters = true, since = StatsFixtures.since) }
        return when (name) {
            "default" -> base(withLines)
            "anygame" -> base(StatsFixtures.readyWithLines(range = StatsRange.Week, game = null))
            "empty" -> base(StatsUiState(StatsRange.Week, null, StatsBody.Empty))
            "loading" -> base(StatsUiState(StatsRange.Week, null, StatsBody.Restoring))
            "error" -> base(
                StatsFixtures.readyWithLines(
                    range = StatsRange.Week,
                    game = zip,
                    rankHistory = SectionState.Unavailable,
                    vsFriends = SectionState.Unavailable,
                ).replacing(StatsSection.Records(SectionState.Unavailable)),
            )
            "longest" -> base(withLines.replacing(StatsSection.Records(SectionState.Ready(longest))))
            "locked" -> base(
                StatsFixtures.readyWithLines(
                    range = StatsRange.Week,
                    game = zip,
                    rankHistory = SectionState.Ready(RankHistoryStanding.Locked(VsFriendsStanding.Unlock.SignIn)),
                    vsFriends = SectionState.Ready(VsFriendsStanding.Locked(VsFriendsStanding.Unlock.SignIn)),
                ),
            )
            else -> error(name)
        }
    }

    /** A year and a half of streak, a hint-free run of 1,284 solves and fastest times of 1:39:59. */
    private val longest = StatsRecords(
        fastest = StatsFixtures.records.fastest.map { it.copy(solve = it.solve.copy(timeMs = 5_999_000)) },
        longestStreak = StatsRecords.Streak(
            StreakRun(LocalDate.of(2025, 3, 30), LocalDate.of(2026, 11, 14), frozen = emptySet()),
            StatsFixtures.streakSolve,
        ),
        longestHintFreeRun = HintFreeRun(1_284, LocalDate.of(2025, 3, 30), SolveRef(StatsFixtures.queens, LocalDate.of(2026, 11, 15), puzzleNumber = 291, hasReplay = true)),
    )

    private fun StatsUiState.replacing(records: StatsSection.Records) = copy(
        body = StatsBody.Ready((body as StatsBody.Ready).sections.map { if (it is StatsSection.Records) records else it }),
    )

    private fun capture(name: String, device: String, dark: Boolean) {
        val state = state(name)
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
        // Error and locked are about the Friends group, which is below the fold: scroll it into view.
        if (name == "error" || name == "locked") rule.onNodeWithTag("stats.sections").performScrollToIndex(6) // header of the fourth group: items run header, body per group
        rule.settleAndCapture("stats-grouped", name, device, dark)
    }
}
