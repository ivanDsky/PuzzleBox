package com.ivandsky.puzzlebox.insights.stats

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.insights.domain.recap.FastestSolve
import com.ivandsky.puzzlebox.insights.domain.recap.HintFreeRun
import com.ivandsky.puzzlebox.insights.domain.recap.SolveRef
import com.ivandsky.puzzlebox.insights.domain.recap.StreakRun
import com.ivandsky.puzzlebox.insights.domain.stats.GameStanding
import com.ivandsky.puzzlebox.insights.domain.stats.HintFreeRateSeries
import com.ivandsky.puzzlebox.insights.domain.stats.SectionState
import com.ivandsky.puzzlebox.insights.domain.stats.StatsGranularity
import com.ivandsky.puzzlebox.insights.domain.stats.StatsRange
import com.ivandsky.puzzlebox.insights.domain.stats.StatsRecords
import com.ivandsky.puzzlebox.insights.domain.stats.StatsSection
import com.ivandsky.puzzlebox.insights.domain.stats.TimeTrendSeries
import com.ivandsky.puzzlebox.insights.domain.stats.VsFriendsStanding
import com.ivandsky.puzzlebox.insights.domain.stats.WeekdayPattern
import com.ivandsky.puzzlebox.model.GameId
import java.time.DayOfWeek
import java.time.LocalDate

// The stats page, light and dark: every game's month with its trend, records and hint-free rate, one game's week, an empty range and a records section that cannot load.

private val Zip = GameId("zip")
private val Queens = GameId("queens")
private val Games = listOf(Zip, Queens)

private fun name(game: GameId): String = if (game == Zip) "Zip" else "Queens"

private fun day(dayOfMonth: Int) = LocalDate.of(2026, 11, dayOfMonth)

private val Records = StatsRecords(
    fastest = listOf(
        StatsRecords.Fastest(Zip, FastestSolve(day(3), puzzleNumber = 412, timeMs = 41_000, hasReplay = true)),
        StatsRecords.Fastest(Queens, FastestSolve(day(9), puzzleNumber = null, timeMs = 95_000, hasReplay = false)),
    ),
    longestStreak = StatsRecords.Streak(
        StreakRun(day(1), day(14), frozen = setOf(day(6))),
        SolveRef(Zip, day(14), puzzleNumber = 423, hasReplay = true),
    ),
    longestHintFreeRun = HintFreeRun(9, day(7), SolveRef(Queens, day(15), puzzleNumber = 291, hasReplay = true)),
)

private fun ready(state: SectionState<StatsRecords> = SectionState.Ready(Records), range: StatsRange = StatsRange.Month, game: GameId? = null) =
    StatsUiState(range, game, StatsBody.Ready(listOf(StatsSection.Records(state))))

@Composable
private fun Page(state: StatsUiState) = PuzzleBoxTheme {
    Surface(color = MaterialTheme.colorScheme.background) {
        StatsScreen(state, Games, ::name, onSelectRange = {}, onSelectGame = {}, onOpenSolve = {}, onBack = {})
    }
}

@PuzzleBoxPreviews
@Composable
private fun StatsScreenPreview() = Page(ready())

@PuzzleBoxPreviews
@Composable
private fun StatsScreenOneGameWeekPreview() = Page(ready(range = StatsRange.Week, game = Zip))

@PuzzleBoxPreviews
@Composable
private fun StatsScreenEmptyPreview() = Page(StatsUiState(StatsRange.Week, null, StatsBody.Empty))

@PuzzleBoxPreviews
@Composable
private fun StatsScreenRecordsUnavailablePreview() = Page(ready(state = SectionState.Unavailable))

private val Weeks = listOf(day(2), day(9), day(16), day(23))

private val Trend = TimeTrendSeries(
    StatsGranularity.Week,
    listOf(95_000L, 88_000L, 102_000L, 76_000L).mapIndexed { index, ms -> TimeTrendSeries.Point(Weeks[index], ms, solved = 5) },
)

private val HintFreeRate = HintFreeRateSeries(
    StatsGranularity.Week,
    listOf(2, 3, 3, 5).mapIndexed { index, free -> HintFreeRateSeries.Point(Weeks[index], hintFree = free, solved = 5) },
)

private val Weekday = WeekdayPattern(
    listOf(95_000L, 88_000L, 102_000L, 76_000L, 71_000L, 64_000L).mapIndexed { index, ms -> WeekdayPattern.Day(DayOfWeek.of(index + 1), ms, solved = 4) },
)

private val Ranked = VsFriendsStanding.Ranked(
    best = GameStanding(Zip, points = 1_340.0, place = 1, players = 6),
    weakest = GameStanding(Queens, points = 212.0, place = 4, players = 5),
)

private fun withLines(
    trend: TimeTrendSeries = Trend,
    hintFree: HintFreeRateSeries = HintFreeRate,
    vsFriends: SectionState<VsFriendsStanding> = SectionState.Ready(Ranked),
) = StatsUiState(
    StatsRange.Month,
    null,
    StatsBody.Ready(
        listOf(
            StatsSection.TimeTrend(SectionState.Ready(trend)),
            StatsSection.Records(SectionState.Ready(Records)),
            StatsSection.HintFreeRate(SectionState.Ready(hintFree)),
            StatsSection.Weekday(SectionState.Ready(Weekday)),
            StatsSection.VsFriends(vsFriends),
        ),
    ),
)

@PuzzleBoxPreviews
@Composable
private fun StatsScreenLinesPreview() = Page(withLines())

@PuzzleBoxPreviews
@Composable
private fun StatsScreenSinglePointsPreview() = Page(
    withLines(
        trend = TimeTrendSeries(StatsGranularity.Week, Trend.points.take(1)),
        hintFree = HintFreeRateSeries(StatsGranularity.Week, HintFreeRate.points.take(1)),
    ),
)

@PuzzleBoxPreviews
@Composable
private fun StatsScreenFriendsSignedOutPreview() = Page(withLines(vsFriends = SectionState.Ready(VsFriendsStanding.Locked(VsFriendsStanding.Unlock.SignIn))))

@PuzzleBoxPreviews
@Composable
private fun StatsScreenFriendsUnavailablePreview() = Page(withLines(vsFriends = SectionState.Unavailable))
