package com.ivandsky.puzzlebox.insights.stats

import com.ivandsky.puzzlebox.insights.domain.recap.FastestSolve
import com.ivandsky.puzzlebox.insights.domain.recap.HintFreeRun
import com.ivandsky.puzzlebox.insights.domain.recap.SolveRef
import com.ivandsky.puzzlebox.insights.domain.recap.StreakRun
import com.ivandsky.puzzlebox.insights.domain.stats.HintFreeRateSeries
import com.ivandsky.puzzlebox.insights.domain.stats.RankHistoryStanding
import com.ivandsky.puzzlebox.insights.domain.stats.SectionState
import com.ivandsky.puzzlebox.insights.domain.stats.StatsGranularity
import com.ivandsky.puzzlebox.insights.domain.stats.StatsRange
import com.ivandsky.puzzlebox.insights.domain.stats.StatsRecords
import com.ivandsky.puzzlebox.insights.domain.stats.StatsSince
import com.ivandsky.puzzlebox.insights.domain.stats.StatsSection
import com.ivandsky.puzzlebox.insights.domain.stats.GameStanding
import com.ivandsky.puzzlebox.insights.domain.stats.TimeTrendSeries
import com.ivandsky.puzzlebox.insights.domain.stats.VsFriendsStanding
import com.ivandsky.puzzlebox.insights.domain.stats.WeekdayPattern
import com.ivandsky.puzzlebox.model.GameId
import java.time.DayOfWeek
import java.time.LocalDate

/** A stats page's states for the screen tests and the goldens: three games, one of them with an imported fastest solve. */
internal object StatsFixtures {
    val zip = GameId("zip")
    val queens = GameId("queens")
    val tango = GameId("tango")
    val games = listOf(zip, queens, tango)

    fun gameName(game: GameId): String = when (game) {
        zip -> "Zip"
        queens -> "Queens"
        tango -> "Tango"
        else -> game.value
    }

    private fun day(dayOfMonth: Int) = LocalDate.of(2026, 11, dayOfMonth)

    val zipFastest = StatsRecords.Fastest(zip, FastestSolve(day(3), puzzleNumber = 412, timeMs = 41_000, hasReplay = true))
    val queensFastest = StatsRecords.Fastest(queens, FastestSolve(day(9), puzzleNumber = 280, timeMs = 95_000, hasReplay = false))
    val tangoFastest = StatsRecords.Fastest(tango, FastestSolve(day(12), puzzleNumber = 133, timeMs = 128_000, hasReplay = true))

    val streakSolve = SolveRef(zip, day(14), puzzleNumber = 423, hasReplay = true)
    val hintFreeLast = SolveRef(queens, day(15), puzzleNumber = 291, hasReplay = false)

    val records = StatsRecords(
        fastest = listOf(zipFastest, queensFastest, tangoFastest),
        longestStreak = StatsRecords.Streak(StreakRun(day(1), day(14), frozen = setOf(day(6))), streakSolve),
        longestHintFreeRun = HintFreeRun(solves = 9, start = day(7), last = hintFreeLast),
    )

    fun ready(
        range: StatsRange = StatsRange.Month,
        game: GameId? = null,
        state: SectionState<StatsRecords> = SectionState.Ready(records),
        filters: Boolean = false,
        since: StatsSince? = null,
    ) = StatsUiState(range, game, StatsBody.Ready(listOf(StatsSection.Records(state))), filters, since)

    /** The puzzle day the filter bar's Since rows are worded from: Nov 19, the first solve on Mar 30, 2025. */
    val since = StatsSince(today = LocalDate.of(2026, 11, 19), firstSolve = LocalDate.of(2025, 3, 30))

    private val weeks = listOf(day(2), day(9), day(16), day(23))

    /** Four weeks of average times, the line the Month range draws. */
    val trend = TimeTrendSeries(
        StatsGranularity.Week,
        listOf(95_000L, 88_000L, 102_000L, 76_000L).mapIndexed { index, ms -> TimeTrendSeries.Point(weeks[index], ms, solved = 5) },
    )

    /** Four weeks of hint-free shares. */
    val hintFreeRate = HintFreeRateSeries(
        StatsGranularity.Week,
        listOf(2, 3, 3, 5).mapIndexed { index, free -> HintFreeRateSeries.Point(weeks[index], hintFree = free, solved = 5) },
    )

    /** A week of average times with Sunday missing: no solve was made on a Sunday, so it has no bar. */
    val weekday = WeekdayPattern(
        listOf(95_000L, 88_000L, 102_000L, 76_000L, 71_000L, 64_000L).mapIndexed { index, ms ->
            WeekdayPattern.Day(DayOfWeek.of(index + 1), ms, solved = 4)
        },
    )

    /** Zip is the viewer's best game among friends, Tango the weakest. */
    val vsFriends = VsFriendsStanding.Ranked(
        best = GameStanding(zip, points = 1_340.0, place = 1, players = 6),
        weakest = GameStanding(tango, points = 212.0, place = 4, players = 5),
    )

    /** Four kept weeks of Friends points per day. */
    val rankHistory = RankHistoryStanding.Kept(
        listOf(142.5, 98.0, 171.25, 203.0).mapIndexed { index, points -> RankHistoryStanding.Kept.Week(weeks[index], points) },
    )

    /** Every section the page has so far, in its order: trend, Records, hint-free rate, weekday, rank history, vs friends. */
    fun readyWithLines(
        range: StatsRange = StatsRange.Month,
        game: GameId? = null,
        trend: TimeTrendSeries = this.trend,
        hintFree: HintFreeRateSeries = hintFreeRate,
        rankHistory: SectionState<RankHistoryStanding> = SectionState.Ready(this.rankHistory),
        vsFriends: SectionState<VsFriendsStanding> = SectionState.Ready(this.vsFriends),
    ) = StatsUiState(
        range,
        game,
        StatsBody.Ready(
            listOf(
                StatsSection.TimeTrend(SectionState.Ready(trend)),
                StatsSection.Records(SectionState.Ready(records)),
                StatsSection.HintFreeRate(SectionState.Ready(hintFree)),
                StatsSection.Weekday(SectionState.Ready(weekday)),
                StatsSection.VsFriends(vsFriends),
            ).let { sections -> if (game == null) sections else sections.toMutableList().apply { add(size - 1, StatsSection.RankHistory(rankHistory)) } },
        ),
    )
}
