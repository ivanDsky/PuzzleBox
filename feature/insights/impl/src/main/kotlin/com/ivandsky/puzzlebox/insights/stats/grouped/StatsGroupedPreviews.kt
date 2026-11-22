package com.ivandsky.puzzlebox.insights.stats.grouped

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
import com.ivandsky.puzzlebox.insights.domain.stats.RankHistoryStanding
import com.ivandsky.puzzlebox.insights.domain.stats.SectionState
import com.ivandsky.puzzlebox.insights.domain.stats.StatsGranularity
import com.ivandsky.puzzlebox.insights.domain.stats.StatsRange
import com.ivandsky.puzzlebox.insights.domain.stats.StatsRecords
import com.ivandsky.puzzlebox.insights.domain.stats.StatsSection
import com.ivandsky.puzzlebox.insights.domain.stats.StatsSince
import com.ivandsky.puzzlebox.insights.domain.stats.TimeTrendSeries
import com.ivandsky.puzzlebox.insights.domain.stats.VsFriendsStanding
import com.ivandsky.puzzlebox.insights.domain.stats.WeekdayPattern
import com.ivandsky.puzzlebox.insights.stats.StatsBody
import com.ivandsky.puzzlebox.insights.stats.StatsScreen
import com.ivandsky.puzzlebox.insights.stats.StatsUiState
import com.ivandsky.puzzlebox.model.GameId
import java.time.DayOfWeek
import java.time.LocalDate

// The grouped stats page (StatsFilters on), light and dark on a phone and a tablet: default, empty, loading, error, longest names and values, and locked friends.

private val Zip = GameId("zip")
private val Queens = GameId("queens")
private val Tango = GameId("tango")
private val Games = listOf(Zip, Queens, Tango)

private fun name(game: GameId): String = game.value.replaceFirstChar(Char::uppercase)

private fun day(dayOfMonth: Int) = LocalDate.of(2026, 11, dayOfMonth)

private val Since = StatsSince(today = LocalDate.of(2026, 11, 19), firstSolve = LocalDate.of(2025, 3, 30))

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

private val Records = StatsRecords(
    fastest = listOf(
        StatsRecords.Fastest(Zip, FastestSolve(day(3), puzzleNumber = 412, timeMs = 41_000, hasReplay = true)),
        StatsRecords.Fastest(Queens, FastestSolve(day(9), puzzleNumber = 280, timeMs = 95_000, hasReplay = false)),
        StatsRecords.Fastest(Tango, FastestSolve(day(12), puzzleNumber = 133, timeMs = 128_000, hasReplay = true)),
    ),
    longestStreak = StatsRecords.Streak(
        StreakRun(day(1), day(14), frozen = setOf(day(6))),
        SolveRef(Zip, day(14), puzzleNumber = 423, hasReplay = true),
    ),
    longestHintFreeRun = HintFreeRun(9, day(7), SolveRef(Queens, day(15), puzzleNumber = 291, hasReplay = true)),
)

private val Ranked = VsFriendsStanding.Ranked(
    best = GameStanding(Zip, points = 1_340.0, place = 1, players = 6),
    weakest = GameStanding(Tango, points = 212.0, place = 4, players = 5),
)

private val Kept = RankHistoryStanding.Kept(
    listOf(142.5, 98.0, 171.25, 203.0).mapIndexed { index, points -> RankHistoryStanding.Kept.Week(Weeks[index], points) },
)

private fun page(
    game: GameId? = null,
    records: SectionState<StatsRecords> = SectionState.Ready(Records),
    rankHistory: SectionState<RankHistoryStanding> = SectionState.Ready(Kept),
    vsFriends: SectionState<VsFriendsStanding> = SectionState.Ready(Ranked),
    longestRecords: StatsRecords? = null,
) = StatsUiState(
    range = StatsRange.Week,
    game = game,
    body = StatsBody.Ready(
        buildList {
            add(StatsSection.TimeTrend(SectionState.Ready(Trend)))
            add(StatsSection.Records(if (longestRecords != null) SectionState.Ready(longestRecords) else records))
            add(StatsSection.HintFreeRate(SectionState.Ready(HintFreeRate)))
            add(StatsSection.Weekday(SectionState.Ready(Weekday)))
            if (game != null) add(StatsSection.RankHistory(rankHistory))
            add(StatsSection.VsFriends(vsFriends))
        },
    ),
    filters = true,
    since = Since,
)

private fun waiting(body: StatsBody) = StatsUiState(StatsRange.Week, null, body, filters = true, since = Since)

@Composable
private fun Page(state: StatsUiState) = PuzzleBoxTheme {
    Surface(color = MaterialTheme.colorScheme.background) {
        StatsScreen(state, Games, ::name, onSelectRange = {}, onSelectGame = {}, onOpenSolve = {}, onBack = {})
    }
}

@PuzzleBoxPreviews
@StatsTabletPreviews
@Composable
private fun StatsGroupedDefaultPreview() = Page(page(game = Zip))

@PuzzleBoxPreviews
@StatsTabletPreviews
@Composable
private fun StatsGroupedAnyGamePreview() = Page(page())

@PuzzleBoxPreviews
@StatsTabletPreviews
@Composable
private fun StatsGroupedEmptyPreview() = Page(waiting(StatsBody.Empty))

@PuzzleBoxPreviews
@StatsTabletPreviews
@Composable
private fun StatsGroupedLoadingPreview() = Page(waiting(StatsBody.Restoring))

@PuzzleBoxPreviews
@StatsTabletPreviews
@Composable
private fun StatsGroupedErrorPreview() = Page(
    page(game = Zip, records = SectionState.Unavailable, rankHistory = SectionState.Unavailable, vsFriends = SectionState.Unavailable),
)

@PuzzleBoxPreviews
@StatsTabletPreviews
@Composable
private fun StatsGroupedLongestPreview() = Page(
    page(
        game = Zip,
        longestRecords = StatsRecords(
            fastest = Records.fastest.map { it.copy(solve = it.solve.copy(timeMs = 5_999_000)) },
            longestStreak = StatsRecords.Streak(
                StreakRun(LocalDate.of(2025, 3, 30), LocalDate.of(2026, 11, 14), frozen = emptySet()),
                SolveRef(Zip, LocalDate.of(2026, 11, 14), puzzleNumber = 423, hasReplay = true),
            ),
            longestHintFreeRun = HintFreeRun(1_284, LocalDate.of(2025, 3, 30), SolveRef(Queens, LocalDate.of(2026, 11, 15), puzzleNumber = 291, hasReplay = true)),
        ),
    ),
)

@PuzzleBoxPreviews
@StatsTabletPreviews
@Composable
private fun StatsGroupedLockedPreview() = Page(
    page(
        game = Zip,
        rankHistory = SectionState.Ready(RankHistoryStanding.Locked(VsFriendsStanding.Unlock.SignIn)),
        vsFriends = SectionState.Ready(VsFriendsStanding.Locked(VsFriendsStanding.Unlock.SignIn)),
    ),
)
