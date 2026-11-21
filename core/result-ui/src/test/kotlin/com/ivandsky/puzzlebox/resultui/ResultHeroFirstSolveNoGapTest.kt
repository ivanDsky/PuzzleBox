package com.ivandsky.puzzlebox.resultui

import com.ivandsky.puzzlebox.domain.ResultDay
import com.ivandsky.puzzlebox.domain.ResultInsights
import com.ivandsky.puzzlebox.domain.ResultRank
import com.ivandsky.puzzlebox.domain.WeekdaySpread
import com.ivandsky.puzzlebox.domain.timeSpread
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.RecordVerdict
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** #1762: a first solve of this puzzle that is not a new best leads with its rank, not the gap to the best. */
class ResultHeroFirstSolveNoGapTest {

    private val time = RankOrder.TimeThenHints
    private val hints = RankOrder.HintsThenTime
    private val saturday = LocalDate.of(2026, 11, 7)

    private fun insights(earlier: List<BestScore>, current: BestScore, order: RankOrder) = ResultInsights(
        streak = 3,
        week = (6 downTo 0).map { ResultDay(saturday.minusDays(it.toLong()), solved = it < 3, today = it == 0) },
        spread = timeSpread(earlier, current, order),
        weekday = WeekdaySpread(DayOfWeek.SATURDAY, timeSpread(emptyList(), current, order), fastest = false),
    )

    private val timeInsights = insights(listOf(BestScore(112_000L, 0), BestScore(90_000L, 0)), BestScore(100_000L, 0), time)
    private val hintsInsights = insights(listOf(BestScore(112_000L, 2), BestScore(90_000L, 0)), BestScore(100_000L, 1), hints)

    private fun facts(
        verdict: RecordVerdict? = RecordVerdict.NotBest,
        order: RankOrder = time,
        previous: BestScore? = BestScore(90_000L, 0),
        samePuzzle: SamePuzzleSolves? = SamePuzzleSolves.None,
        insights: ResultInsights? = if (order == time) timeInsights else hintsInsights,
        playAgain: Boolean = false,
        practice: Boolean = false,
    ) = ResultHeroFacts(
        timeMs = 100_000L,
        hintsUsed = if (order == time) 0 else 1,
        verdict = verdict,
        previousBest = previous,
        rankOrder = order,
        game = if (order == time) "Zip" else "Mini Sudoku",
        puzzleNumber = 918,
        practice = practice,
        playAgain = playAgain,
        dateLine = "Today · Sat 7 Nov",
        doneToday = 2,
        total = 5,
        insights = insights,
        rank = ResultRank.Hidden,
        primary = ResultPrimaryAction.Done,
        moments = emptyList(),
        challenge = null,
        samePuzzle = samePuzzle,
    )

    private fun bestLine(facts: ResultHeroFacts) = resultHeroModel(facts, Locale.US).bestLine

    @Test
    fun `a time-first not-best first solve leads with its rank`() {
        assertEquals("2nd fastest of your 3 Zip", bestLine(facts()))
    }

    @Test
    fun `a hints-first not-best first solve leads with its rank`() {
        assertEquals("2nd best of your 3 Mini Sudoku", bestLine(facts(order = hints)))
    }

    @Test
    fun `with no rank to name there is no compare line`() {
        assertNull(bestLine(facts(insights = null)))
        assertNull(bestLine(facts(insights = insights(emptyList(), BestScore(100_000L, 0), time))))
        assertNull(heroRankLine(time, timeSpread(listOf(BestScore(112_000L, 0)), BestScore(100_000L, 0), time), "Zip"))
    }

    @Test
    fun `the lines the ticket keeps are unchanged`() {
        assertEquals("10s faster than your best (1:30)", bestLine(facts(verdict = RecordVerdict.NewBest)))
        assertEquals("First solve — this is your time to beat", bestLine(facts(verdict = RecordVerdict.FirstSolve, previous = null)))
        assertNull(bestLine(facts(verdict = RecordVerdict.TiedBest)))
        assertEquals("${PlayAgainCopy.LABEL} · Best 1:30", bestLine(facts(verdict = RecordVerdict.NotBest, playAgain = true)))
    }

    @Test
    fun `an earlier solve of this puzzle still compares columns, and a duel leg without them keeps the gap`() {
        val earlier = SamePuzzleSolves(BestScore(114_000L, 0), best = BestScore(101_000L, 0))

        assertNull(bestLine(facts(samePuzzle = earlier)))
        assertEquals("Your best is 1:30 · 10s off", bestLine(facts(samePuzzle = null)))
        assertNull(bestLine(facts(samePuzzle = SamePuzzleSolves.Unread)))
        assertNull(bestLine(facts(practice = true)))
    }
}
