package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.repository.DailyEntry
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.game.ScoreModel
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.game.TestGames.Ref
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import com.ivandsky.puzzlebox.testing.game.TestGames.beta
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Results page's streak, week, average and spread for one solve. The test games' zone is
 * UTC, so the puzzle day at the fixed clock is Nov 10 (the same setup as [ObserveGameStatsTest]).
 */
class ObserveResultInsightsTest {

    private val today = LocalDate.of(2026, 11, 10)
    private val clock = Clock.fixed(Instant.parse("2026-11-10T12:00:00Z"), ZoneOffset.UTC)
    private val registry = GameRegistry(listOf(TestGames.plugin(alpha), TestGames.plugin(beta)))
    private val completions = FakeCompletionRepository()
    private val daily = FakeDailyRepository()

    private fun insights(game: GameId, daysAgo: Long, solve: BestScore, counted: Boolean) =
        ObserveResultInsights(
            registry = registry,
            history = ObserveGameHistory(registry, daily, completions),
            computeStreak = ComputeStreak(GetPuzzleDay(registry, clock)),
            puzzleDay = GetPuzzleDay(registry, clock),
        )(game, today.minusDays(daysAgo), solve, counted)

    private fun record(game: GameId, daysAgo: Long, time: Long, hints: Int = 0, puzzleNumber: Int? = 100) =
        CompletionRecord(
            puzzleRef = Ref(game, PuzzleKey.Daily(today.minusDays(daysAgo), puzzleNumber)),
            time = time,
            hints = hints,
            stats = RecordStats(schemaVersion = 1, blob = "{}"),
            isRecord = true,
        )

    private fun captured(game: GameId, daysAgo: Long, number: Int? = 100, archived: Boolean = false) =
        DailyEntry(game, today.minusDays(daysAgo), number, "v", fetchedAtMs = 1L, archived = archived)

    private val now = BestScore(timeMs = 50_000L, hints = 0)

    @Test
    fun `a counted solve is in the streak and week before its write lands`() = runTest {
        completions.setRecords(listOf(record(alpha, 1, 60_000L), record(alpha, 2, 90_000L)))
        daily.addEntry(captured(alpha, 0))

        val result = insights(alpha, 0, now, counted = true).first()!!

        assertEquals(3, result.streak)
        assertEquals(7, result.week.size)
        assertEquals(today.minusDays(6), result.week.first().date)
        assertEquals(today, result.week.last().date)
        assertEquals(listOf(false, false, false, false, true, true, true), result.week.map { it.solved })
        assertEquals(listOf(false, false, false, false, false, false, true), result.week.map { it.today })
        assertEquals(75_000L, result.averageMs)
        assertEquals(3, result.spread.solves)
        assertTrue(result.spread.best)
    }

    @Test
    fun `the same numbers hold after its write lands`() = runTest {
        completions.setRecords(listOf(record(alpha, 1, 60_000L), record(alpha, 2, 90_000L)))
        daily.addEntry(captured(alpha, 0))
        val before = insights(alpha, 0, now, counted = true).first()

        completions.setRecords(
            listOf(record(alpha, 0, now.timeMs), record(alpha, 1, 60_000L), record(alpha, 2, 90_000L)),
        )

        assertEquals(before, insights(alpha, 0, now, counted = true).first())
    }

    @Test
    fun `any game's solve keeps the week and streak`() = runTest {
        completions.setRecords(
            listOf(record(beta, 1, 40_000L), record(alpha, 2, 90_000L), record(beta, 3, 10_000L)),
        )
        daily.addEntry(captured(alpha, 0))

        val result = insights(alpha, 0, now, counted = true).first()!!

        assertEquals(4, result.streak)
        // Only alpha's own solves feed its spread.
        assertEquals(2, result.spread.solves)
        assertEquals(90_000L, result.averageMs)
    }

    @Test
    fun `another game's solve today keeps today's dot when this slot's row is left out`() = runTest {
        completions.setRecords(listOf(record(beta, 0, 20_000L), record(alpha, 0, now.timeMs)))
        daily.addEntry(captured(alpha, 0))

        val result = insights(alpha, 0, now, counted = true).first()!!

        assertEquals(1, result.streak)
        assertTrue(result.week.last().solved)
        assertTrue(result.spread.isFirstSolve)
        assertNull(result.averageMs)
    }

    @Test
    fun `archived days count in the spread but not the streak or week`() = runTest {
        completions.setRecords(listOf(record(alpha, 1, 60_000L)))
        daily.addEntry(captured(alpha, 1, archived = true))
        daily.addEntry(captured(alpha, 0))

        val result = insights(alpha, 0, now, counted = true).first()!!

        assertEquals(1, result.streak)
        assertFalse(result.week[5].solved)
        assertEquals(2, result.spread.solves)
    }

    @Test
    fun `a hints-first game ranks the spread by its rank order`() = runTest {
        val strands = ObserveResultInsights(
            registry = GameRegistry(listOf(TestGames.plugin(alpha), hintsFirst(TestGames.plugin(beta)))),
            history = ObserveGameHistory(registry, daily, completions),
            computeStreak = ComputeStreak(GetPuzzleDay(registry, clock)),
            puzzleDay = GetPuzzleDay(registry, clock),
        )
        completions.setRecords(listOf(record(beta, 1, 10_000L, hints = 1), record(beta, 2, 90_000L, hints = 0)))
        daily.addEntry(captured(beta, 0))

        val result = strands(beta, today, BestScore(60_000L, 0), counted = true).first()!!

        // 60 s with no hints beats both: fewer hints than the 10 s solve, faster than the 90 s one.
        assertEquals(2, result.spread.beaten)
        assertTrue(result.spread.best)
    }

    @Test
    fun `a play-again gets the spread and leaves streak and week as history stands`() = runTest {
        completions.setRecords(
            listOf(record(alpha, 0, 45_000L), record(alpha, 3, 80_000L), record(alpha, 5, 100_000L)),
        )
        daily.addEntry(captured(alpha, 0))
        daily.addEntry(captured(alpha, 3))

        val result = insights(alpha, 3, BestScore(70_000L, 0), counted = false).first()!!

        assertEquals(1, result.streak)
        assertEquals(listOf(false, true, false, true, false, false, true), result.week.map { it.solved })
        assertEquals(3, result.spread.solves)
        assertEquals(1, result.spread.beaten)
        assertEquals(72_500L, result.averageMs)
    }

    @Test
    fun `the weekday spread holds only solves of the same weekday`() = runTest {
        // Today is a Tuesday: 7 and 14 days ago are Tuesdays, 1 and 3 days ago are not.
        completions.setRecords(
            listOf(record(alpha, 7, 40_000L), record(alpha, 14, 100_000L), record(alpha, 1, 10_000L), record(alpha, 3, 20_000L)),
        )

        val result = insights(alpha, 0, now, counted = true).first()!!

        assertEquals(DayOfWeek.TUESDAY, result.weekday.day)
        assertEquals(3, result.weekday.spread.solves)
        assertEquals(70_000L, result.weekday.spread.averageEarlierMs)
        assertEquals(1, result.weekday.spread.beaten)
        assertEquals(2, result.weekday.spread.rank)
        // All days still sees every solve.
        assertEquals(5, result.spread.solves)
        assertEquals(4, result.spread.rank)
    }

    @Test
    fun `the default scope is all days until two earlier solves share the weekday`() = runTest {
        assertEquals(SpreadScope.All, insights(alpha, 0, now, counted = true).first()!!.defaultScope)

        completions.setRecords(listOf(record(alpha, 7, 40_000L)))
        val one = insights(alpha, 0, now, counted = true).first()!!
        assertEquals(2, one.weekday.spread.solves)
        assertEquals(SpreadScope.All, one.defaultScope)

        completions.setRecords(listOf(record(alpha, 7, 40_000L), record(alpha, 14, 50_000L)))
        val two = insights(alpha, 0, now, counted = true).first()!!
        assertEquals(SpreadScope.Weekday, two.defaultScope)
    }

    @Test
    fun `a first solve has a one-solve weekday spread`() = runTest {
        val result = insights(alpha, 0, now, counted = true).first()!!

        assertTrue(result.weekday.spread.isFirstSolve)
        assertFalse(result.weekday.fastest)
    }

    @Test
    fun `a play-again of another weekday uses that weekday`() = runTest {
        // Monday: 1, 8 and 15 days ago.
        completions.setRecords(
            listOf(record(alpha, 8, 60_000L), record(alpha, 15, 90_000L), record(alpha, 7, 10_000L), record(alpha, 0, 45_000L)),
        )

        val result = insights(alpha, 1, now, counted = false).first()!!

        assertEquals(DayOfWeek.MONDAY, result.weekday.day)
        assertEquals(3, result.weekday.spread.solves)
        assertEquals(2, result.weekday.spread.beaten)
    }

    @Test
    fun `today is the fastest weekday when its earlier average is the lowest among weekdays with two solves`() = runTest {
        // Tuesdays 40 s, Mondays 80 s.
        completions.setRecords(
            listOf(record(alpha, 7, 40_000L), record(alpha, 14, 40_000L), record(alpha, 1, 80_000L), record(alpha, 8, 80_000L)),
        )

        assertTrue(insights(alpha, 0, now, counted = true).first()!!.weekday.fastest)
    }

    @Test
    fun `today is not the fastest weekday when another weekday averages lower`() = runTest {
        completions.setRecords(
            listOf(record(alpha, 7, 90_000L), record(alpha, 14, 90_000L), record(alpha, 1, 30_000L), record(alpha, 8, 30_000L)),
        )

        assertFalse(insights(alpha, 0, now, counted = true).first()!!.weekday.fastest)
    }

    @Test
    fun `a weekday with one solve does not compete for fastest`() = runTest {
        // Tuesdays 40 s and Mondays 80 s, plus a lone, quicker Saturday.
        completions.setRecords(
            listOf(record(alpha, 7, 40_000L), record(alpha, 14, 40_000L), record(alpha, 1, 80_000L), record(alpha, 8, 80_000L), record(alpha, 3, 10_000L)),
        )

        assertTrue(insights(alpha, 0, now, counted = true).first()!!.weekday.fastest)
    }

    @Test
    fun `a weekday with one earlier solve is not the fastest`() = runTest {
        completions.setRecords(listOf(record(alpha, 7, 20_000L), record(alpha, 1, 80_000L), record(alpha, 8, 80_000L)))

        assertFalse(insights(alpha, 0, now, counted = true).first()!!.weekday.fastest)
    }

    @Test
    fun `a practice puzzle has no insights`() = runTest {
        daily.addEntry(captured(alpha, 0, number = null))

        assertNull(insights(alpha, 0, now, counted = true).first())
    }

    private fun <P> hintsFirst(
        plugin: GamePlugin<P, TestGames.Session, TestGames.Model>,
    ): GamePlugin<P, TestGames.Session, TestGames.Model> = GamePlugin(
        definition = plugin.definition,
        rules = plugin.rules,
        session = plugin.session,
        score = object : ScoreModel<TestGames.Session> {
            override fun completion(session: TestGames.Session) = plugin.score.completion(session)
            override val rankOrder = RankOrder.HintsThenTime
        },
        stateOf = plugin.stateOf,
        initialSession = plugin.initialSession,
        refOf = plugin.refOf,
        playAgainSession = plugin.playAgainSession,
        rendererModel = plugin.rendererModel,
    )
}
