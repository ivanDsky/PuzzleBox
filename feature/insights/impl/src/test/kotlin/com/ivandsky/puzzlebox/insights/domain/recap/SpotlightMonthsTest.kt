package com.ivandsky.puzzlebox.insights.domain.recap

import com.ivandsky.puzzlebox.insights.domain.recap.SpotlightMonths.Pick
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The Spotlight rule over the port spec's fake 2025 (prototype 14, "The Spotlight rule"): each month's candidates in
 * candidate order, and the lead and also the spec's worked table gives, plus its empty and longest states.
 */
class SpotlightMonthsTest {

    private val queens = GameId("queens")
    private val tango = GameId("tango")
    private val zip = GameId("zip")
    private val mini = GameId("mini")
    private val strands = GameId("strands")

    private val averageMs = mapOf(queens to 24_000L, tango to 31_000L, zip to 19_000L, mini to 72_000L, strands to 168_000L)

    private fun day(month: Int, dayOfMonth: Int) = LocalDate.of(2025, month, dayOfMonth)

    private fun pb(game: GameId, month: Int, dayOfMonth: Int, seconds: Int, fastestAllYear: Boolean = false) =
        MonthHighlight.PersonalBest(game, day(month, dayOfMonth), seconds * 1_000L, fastestAllYear)

    private fun subX(game: GameId, month: Int, dayOfMonth: Int, seconds: Int) = MonthHighlight.FirstSubX(game, day(month, dayOfMonth), seconds * 1_000L)

    private fun streak(month: Int, dayOfMonth: Int, days: Int) = MonthHighlight.Streak(day(month, dayOfMonth), days)

    private fun sets(month: Int, dayOfMonth: Int, count: Int = 1) = MonthHighlight.FullSets(day(month, dayOfMonth), count)

    private fun under(game: GameId, percent: Int, seconds: Int, averages: Map<GameId, Long> = averageMs) =
        MonthHighlight.UnderAverage(game, percent, seconds * 1_000L, averages.getValue(game))

    private fun played(game: GameId, solved: Int, usual: Int) = MonthHighlight.BusiestMonth(game, solved, usual)

    private fun busy(month: Int, dayOfMonth: Int, solved: Int = 4) = MonthHighlight.BusiestDay(day(month, dayOfMonth), solved)

    // The fake year, one list per month in candidate order: Moments by day, the full sets, each game's under-average then busiest month in app order, the busiest day, a quiet month.
    private val january = listOf(
        streak(1, 7, 7), pb(queens, 1, 14, 11), streak(1, 30, 30), sets(1, 22),
        under(queens, 54, 11), under(tango, 32, 21), under(zip, 47, 10), under(mini, 19, 58), busy(1, 1),
    )
    private val february = listOf(
        under(queens, 25, 18), under(tango, 19, 25), under(zip, 16, 16), under(mini, 10, 65), busy(2, 6),
        MonthHighlight.QuietMonth(solved = 11, mostlyGame = queens),
    )
    private val march = listOf(
        streak(3, 7, 7), pb(tango, 3, 12, 19), pb(zip, 3, 19, 9), subX(zip, 3, 19, 10), streak(3, 30, 30),
        under(queens, 54, 11), under(tango, 39, 19), under(zip, 53, 9), under(mini, 19, 58), busy(3, 2),
    )
    private val april = listOf(
        pb(mini, 4, 9, 49), pb(strands, 4, 27, 52, fastestAllYear = true), subX(strands, 4, 27, 60), sets(4, 18),
        under(queens, 50, 12), under(tango, 35, 20), under(zip, 53, 9), under(mini, 32, 49), under(strands, 69, 52), busy(4, 16),
    )
    private val may = listOf(
        pb(queens, 5, 11, 9), subX(queens, 5, 11, 10), sets(5, 25),
        under(queens, 63, 9), under(tango, 39, 19), under(zip, 47, 10), under(mini, 32, 49), busy(5, 3),
    )
    private val june = listOf(
        streak(6, 8, 100), streak(6, 21, 7),
        under(queens, 58, 10), under(tango, 39, 19), under(zip, 53, 9), under(mini, 29, 51), busy(6, 7),
    )
    private val july = listOf(
        streak(7, 14, 30), pb(zip, 7, 18, 4, fastestAllYear = true), sets(7, 4),
        under(queens, 63, 9), under(tango, 39, 19), under(zip, 79, 4), played(zip, 30, 26), under(mini, 32, 49), busy(7, 1),
    )
    private val august = listOf(
        pb(tango, 8, 18, 12, fastestAllYear = true), pb(mini, 8, 22, 44), subX(mini, 8, 22, 45),
        under(queens, 54, 11), under(tango, 61, 12), under(zip, 53, 9), under(mini, 39, 44), busy(8, 1),
    )
    private val september = listOf(
        pb(queens, 9, 6, 8), streak(9, 22, 100), sets(9, 21),
        under(queens, 67, 8), under(tango, 52, 15), under(zip, 53, 9), under(mini, 36, 46), busy(9, 1),
    )
    private val october = listOf(
        pb(queens, 10, 15, 6, fastestAllYear = true), sets(10, 26),
        under(queens, 75, 6), under(tango, 55, 14), under(zip, 58, 8), under(mini, 38, 45), played(mini, 15, 12), busy(10, 1),
    )
    private val november = listOf(
        pb(mini, 11, 10, 37, fastestAllYear = true), sets(11, 23),
        under(queens, 67, 8), under(tango, 52, 15), under(zip, 58, 8), under(mini, 49, 37), played(mini, 15, 12), busy(11, 4),
    )
    private val december = listOf(
        sets(12, 24, count = 2),
        under(queens, 67, 8), under(tango, 58, 13), under(zip, 58, 8), under(mini, 44, 40), busy(12, 1),
    )

    private val year = listOf(january, february, march, april, may, june, july, august, september, october, november, december)

    private val table = listOf(
        Pick(pb(queens, 1, 14, 11), also = streak(1, 7, 7)),
        Pick(MonthHighlight.QuietMonth(11, queens), also = under(tango, 19, 25)),
        Pick(pb(tango, 3, 12, 19), also = pb(zip, 3, 19, 9)),
        Pick(pb(strands, 4, 27, 52, fastestAllYear = true), also = pb(mini, 4, 9, 49)),
        Pick(pb(queens, 5, 11, 9), also = sets(5, 25)),
        Pick(streak(6, 8, 100), also = under(zip, 53, 9)),
        Pick(pb(zip, 7, 18, 4, fastestAllYear = true), also = sets(7, 4)),
        Pick(pb(mini, 8, 22, 44), also = pb(tango, 8, 18, 12, fastestAllYear = true)),
        Pick(streak(9, 22, 100), also = pb(queens, 9, 6, 8)),
        Pick(pb(queens, 10, 15, 6, fastestAllYear = true), also = sets(10, 26)),
        Pick(pb(mini, 11, 10, 37, fastestAllYear = true), also = sets(11, 23)),
        Pick(sets(12, 24, count = 2), also = under(tango, 58, 13)),
    )

    @Test
    fun `the fake year gets the worked table's lead and also for every month`() {
        assertEquals(table, SpotlightMonths.pick(year))
    }

    @Test
    fun `every game leads at least once where best against average gave Queens nine months`() {
        val leadGames = SpotlightMonths.pick(year).mapNotNull { it.lead.game }

        assertEquals(setOf(queens, tango, zip, mini, strands), leadGames.toSet())
        assertEquals(3, leadGames.count { it == queens })
    }

    @Test
    fun `a sparse year leads with what most of a month was, or its busiest day, and never adds the busiest day as also`() {
        val averages = mapOf(queens to 42_000L, tango to 62_000L, zip to 21_000L)
        val sparse = listOf(
            listOf(busy(10, 2, solved = 1), MonthHighlight.Focus(zip, solved = 1, of = 1)),
            listOf(under(queens, 10, 38, averages), busy(11, 18, solved = 2)),
        )

        assertEquals(
            listOf(
                Pick(MonthHighlight.Focus(zip, solved = 1, of = 1), also = null),
                Pick(busy(11, 18, solved = 2), also = under(queens, 10, 38, averages)),
            ),
            SpotlightMonths.pick(sparse),
        )
    }

    @Test
    fun `the longest year leads September with Mini Sudoku's busiest month after August's Mini Sudoku lead`() {
        val longest = year.toMutableList().apply {
            this[3] = april.toMutableList().apply { add(indexOf(under(strands, 69, 52)) + 1, played(strands, 6, 4)) }
            this[8] = listOf(
                pb(mini, 9, 6, 3_723), subX(mini, 9, 6, 45), streak(9, 22, 100), sets(9, 21),
                under(queens, 67, 8), under(tango, 52, 15), under(zip, 53, 9), played(zip, 30, 26), played(mini, 30, 13),
                under(strands, 55, 75), busy(9, 7),
            )
            this[9] = october - played(mini, 15, 12)
            this[10] = november - played(mini, 15, 12)
        }

        assertEquals(
            table.toMutableList().apply {
                this[8] = Pick(played(mini, 30, 13), also = streak(9, 22, 100))
            },
            SpotlightMonths.pick(longest),
        )
    }
}
