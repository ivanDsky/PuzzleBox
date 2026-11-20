package com.ivandsky.puzzlebox.insights.domain.recap

import com.ivandsky.puzzlebox.domain.moments.Moment
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import com.ivandsky.puzzlebox.testing.game.TestGames.beta
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MonthsRecapFactProviderTest {

    private fun day(month: Int, dayOfMonth: Int) = LocalDate.of(2026, month, dayOfMonth)

    private fun best(day: LocalDate, timeMs: Long, averageMs: Long, monthSolves: Int, yearSolves: Int) =
        MonthFacts.MonthBest(day, timeMs, averageMs, monthSolves, yearSolves)

    /** A month whose quickest solve and best against the average are both alpha's, at 60 s; enough when only [byGame] matters. */
    private fun month(number: Int, byGame: Map<GameId, MonthFacts.MonthBest>) = MonthFacts(
        YearMonth.of(2026, number), byGame.values.sumOf { it.monthSolves }, alpha, 60_000,
        bestGame = alpha, bestTimeMs = 60_000, underAveragePercent = 0, byGame = byGame,
    )

    private val march = MonthFacts(
        YearMonth.of(2026, 3), solved = 12, fastestGame = alpha, fastestTimeMs = 58_000, bestGame = beta, bestTimeMs = 61_000, underAveragePercent = 23,
        byGame = mapOf(alpha to best(day(3, 4), 58_000, 60_000, 8, 8), beta to best(day(3, 9), 61_000, 79_000, 4, 5)),
    )
    private val may = MonthFacts(
        YearMonth.of(2026, 5), solved = 1, fastestGame = beta, fastestTimeMs = 141_000, bestGame = beta, bestTimeMs = 141_000, underAveragePercent = 0,
        byGame = mapOf(beta to best(day(5, 2), 141_000, 79_000, 1, 5)),
    )

    private fun input(
        months: List<MonthFacts>,
        moments: List<DatedMoment> = emptyList(),
        days: List<DayTally> = emptyList(),
        fastestByGame: Map<GameId, FastestSolve> = emptyMap(),
        period: RecapPeriod = RecapPeriod.Year(2026),
    ) = RecapInput(
        facts = emptyFacts(period).copy(
            solved = months.sumOf { it.solved }, fastestByGame = fastestByGame, moments = moments, months = months, dayTallies = days,
        ),
        games = listOf(alpha, beta),
    )

    private fun tally(day: LocalDate, solved: Int) = DayTally(day, solved, timeMs = solved * 60_000L, hintFree = solved)

    private suspend fun months(input: RecapInput) = MonthsRecapFactProvider.cards(input).map { it as RecapCard.Month }

    @Test
    fun `a year has one card per month with its count, its fastest solve, its Moments and its headline`() = runTest {
        val cards = MonthsRecapFactProvider.cards(
            input(
                listOf(march, may),
                moments = listOf(DatedMoment(Moment.PersonalBest, day(3, 4), alpha), DatedMoment(Moment.Streak(7), day(3, 20), alpha)),
                days = listOf(tally(day(3, 4), 2), tally(day(3, 9), 3), tally(day(5, 2), 1)),
                fastestByGame = mapOf(alpha to FastestSolve(day(3, 4), puzzleNumber = 120, timeMs = 58_000, hasReplay = true)),
            ),
        )

        assertEquals(
            listOf(
                RecapCard.Month(
                    YearMonth.of(2026, 3), solved = 12, bestGame = beta, bestTimeMs = 61_000, underAveragePercent = 23,
                    fastestGame = alpha, fastestTimeMs = 58_000,
                    lead = MonthHighlight.PersonalBest(alpha, day(3, 4), timeMs = 58_000, fastestAllYear = true),
                    also = MonthHighlight.Streak(day(3, 20), days = 7),
                    moments = listOf(
                        EarnedMoment(EarnedMoment.Kind.PersonalBest, day(3, 4), alpha),
                        EarnedMoment(EarnedMoment.Kind.Streak, day(3, 20), streakDays = 7),
                    ),
                ),
                RecapCard.Month(
                    YearMonth.of(2026, 5), solved = 1, bestGame = beta, bestTimeMs = 141_000, underAveragePercent = 0,
                    fastestGame = beta, fastestTimeMs = 141_000,
                    lead = MonthHighlight.Focus(beta, solved = 1, of = 1), also = null, moments = emptyList(),
                ),
            ),
            cards,
        )
    }

    @Test
    fun `a game that wins best against average every month still leaves the leads to at least three others`() = runTest {
        val year = (1..4).map { number ->
            month(number, mapOf(alpha to best(day(number, 1), 36_000, 60_000, 10, 40), beta to best(day(number, 2), 57_000, 60_000, 10, 40)))
        }
        val cards = months(
            input(
                year,
                moments = listOf(DatedMoment(Moment.PersonalBest, day(2, 10), beta), DatedMoment(Moment.Streak(7), day(3, 7), alpha)),
                days = (1..4).map { tally(day(it, 3), solved = 2) },
            ),
        )

        assertTrue(cards.all { it.bestGame == alpha })
        assertEquals(
            listOf(
                MonthHighlight.UnderAverage(alpha, percent = 40, bestMs = 36_000, averageMs = 60_000),
                MonthHighlight.PersonalBest(beta, day(2, 10), timeMs = null, fastestAllYear = false),
                MonthHighlight.Streak(day(3, 7), days = 7),
                MonthHighlight.BusiestDay(day(4, 3), solved = 2),
            ),
            cards.map { it.lead },
        )
        assertTrue(cards.map { it.lead.game ?: it.lead::class }.distinct().size >= 3)
    }

    @Test
    fun `a game's personal bests in a month come down to the one on its quickest solve of the month`() = runTest {
        val cards = months(
            input(
                listOf(month(3, mapOf(alpha to best(day(3, 10), 50_000, 52_000, 4, 4), beta to best(day(3, 1), 90_000, 91_000, 2, 2)))),
                moments = listOf(
                    DatedMoment(Moment.PersonalBest, day(3, 2), alpha),
                    DatedMoment(Moment.PersonalBest, day(3, 5), beta),
                    DatedMoment(Moment.PersonalBest, day(3, 10), alpha),
                ),
            ),
        )

        val card = cards.single()
        assertEquals(MonthHighlight.PersonalBest(beta, day(3, 5), timeMs = null, fastestAllYear = false), card.lead)
        assertEquals(MonthHighlight.PersonalBest(alpha, day(3, 10), timeMs = 50_000, fastestAllYear = false), card.also)
        assertEquals(3, card.moments.size)
    }

    @Test
    fun `full sets, a quiet month and a game's peak month are headlines, and a month with nothing else leads with what most of it was`() = runTest {
        val cards = months(
            input(
                listOf(
                    month(1, mapOf(alpha to best(day(1, 1), 60_000, 60_000, 10, 43), beta to best(day(1, 2), 60_000, 60_000, 10, 31))),
                    month(2, mapOf(alpha to best(day(2, 1), 60_000, 60_000, 10, 43), beta to best(day(2, 2), 60_000, 60_000, 10, 31))),
                    month(3, mapOf(alpha to best(day(3, 1), 60_000, 60_000, 3, 43), beta to best(day(3, 2), 60_000, 60_000, 1, 31))),
                    month(4, mapOf(alpha to best(day(4, 1), 60_000, 60_000, 20, 43), beta to best(day(4, 2), 60_000, 60_000, 10, 31))),
                ),
                moments = listOf(DatedMoment(Moment.FullSetDone(), day(1, 5), alpha), DatedMoment(Moment.FullSetDone(), day(1, 9), beta)),
            ),
        )

        assertEquals(
            listOf(
                SpotlightMonths.Pick(MonthHighlight.FullSets(day(1, 9), count = 2), also = null),
                SpotlightMonths.Pick(MonthHighlight.Focus(alpha, solved = 10, of = 20), also = null),
                SpotlightMonths.Pick(MonthHighlight.QuietMonth(solved = 4, mostlyGame = alpha), also = null),
                SpotlightMonths.Pick(MonthHighlight.BusiestMonth(alpha, solved = 20, usualSolved = 10), also = null),
            ),
            cards.map { SpotlightMonths.Pick(it.lead, it.also) },
        )
    }

    @Test
    fun `a year with no counted solve has no month card`() = runTest {
        assertEquals(emptyList<RecapCard>(), MonthsRecapFactProvider.cards(input(emptyList())))
    }

    @Test
    fun `a month period has none`() = runTest {
        assertEquals(emptyList<RecapCard>(), MonthsRecapFactProvider.cards(input(listOf(march), period = RecapPeriod.Month(YearMonth.of(2026, 3)))))
    }
}
