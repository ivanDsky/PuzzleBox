package com.ivandsky.puzzlebox.gameui

import com.ivandsky.puzzlebox.domain.ResultDay
import com.ivandsky.puzzlebox.domain.ResultInsights
import com.ivandsky.puzzlebox.domain.ResultRank
import com.ivandsky.puzzlebox.domain.timeSpread
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.RankOrder
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every string of the Results hero copy table, for every state, and what the model shows or hides. */
class ResultHeroCopyTest {

    private val time = RankOrder.TimeThenHints
    private val hints = RankOrder.HintsThenTime
    private val saturday = LocalDate.of(2026, 11, 7)

    private fun line(
        verdict: RecordVerdict?,
        previous: BestScore?,
        order: RankOrder = time,
        timeMs: Long = 100_000L,
        hintsUsed: Int = 0,
    ) = heroBestLine(verdict, previous, order, timeMs, hintsUsed)

    // --- best line -------------------------------------------------------------------------

    @Test
    fun `first solve names the time to beat`() {
        assertEquals("First solve — this is your time to beat", line(RecordVerdict.FirstSolve, null))
        assertEquals("First solve — this is your time to beat", line(RecordVerdict.FirstSolve, null, hints))
    }

    @Test
    fun `a time-first new best names the gap and the best it beat`() {
        assertEquals(
            "12s faster than your best (1:52)",
            line(RecordVerdict.NewBest, BestScore(112_000L, 2), timeMs = 100_000L),
        )
    }

    @Test
    fun `the gap is minutes and seconds from a minute`() {
        assertEquals(
            "1:05 faster than your best (3:05)",
            line(RecordVerdict.NewBest, BestScore(185_000L, 0), timeMs = 120_000L),
        )
        assertEquals("59s", heroGap(160_000L, 101_000L))
        assertEquals("1:00", heroGap(160_000L, 100_000L))
    }

    @Test
    fun `the gap reads at the shown resolution`() {
        // 100.9 s and 112.1 s show as 1:40 and 1:52: twelve seconds, not eleven.
        assertEquals("12s", heroGap(112_100L, 100_900L))
    }

    @Test
    fun `a hints-first new best with fewer hints names the best it beat`() {
        assertEquals(
            "Fewer hints than your best (2 hints · 3:05)",
            line(RecordVerdict.NewBest, BestScore(185_000L, 2), hints, timeMs = 200_000L, hintsUsed = 1),
        )
        assertEquals(
            "Fewer hints than your best (1 hint · 3:05)",
            line(RecordVerdict.NewBest, BestScore(185_000L, 1), hints, timeMs = 200_000L, hintsUsed = 0),
        )
    }

    @Test
    fun `a hints-first new best on level hints is faster, so it names the gap`() {
        assertEquals(
            "5s faster than your best (3:05)",
            line(RecordVerdict.NewBest, BestScore(185_000L, 1), hints, timeMs = 180_000L, hintsUsed = 1),
        )
    }

    @Test
    fun `a time-first slower solve names the best and the gap off`() {
        assertEquals(
            "Your best is 1:52 · 8s off",
            line(RecordVerdict.NotBest, BestScore(112_000L, 0), timeMs = 120_000L),
        )
        assertEquals(
            "Your best is 1:52 · 1:08 off",
            line(RecordVerdict.NotBest, BestScore(112_000L, 0), timeMs = 180_000L),
        )
    }

    @Test
    fun `a hints-first slower solve names the best by its hints`() {
        assertEquals("Your best is Perfect", line(RecordVerdict.NotBest, BestScore(112_000L, 0), hints))
        assertEquals("Your best is 1 hint · 1:52", line(RecordVerdict.NotBest, BestScore(112_000L, 1), hints))
        assertEquals("Your best is 3 hints · 1:52", line(RecordVerdict.NotBest, BestScore(112_000L, 3), hints))
    }

    @Test
    fun `a tie, an unknown verdict and a missing best say nothing`() {
        assertNull(line(RecordVerdict.TiedBest, BestScore(100_000L, 0)))
        assertNull(line(null, BestScore(100_000L, 0)))
        assertNull(line(RecordVerdict.NewBest, null))
        assertNull(line(RecordVerdict.NotBest, null))
    }

    @Test
    fun `a replay names the best it did not move`() {
        assertEquals("Replay · Best 1:52", line(RecordVerdict.Replay, BestScore(112_000L, 0)))
        assertEquals("Replay · Best 2 hints · 1:52", line(RecordVerdict.Replay, BestScore(112_000L, 2), hints))
        assertEquals("Replay", line(RecordVerdict.Replay, null))
    }

    // --- pill ------------------------------------------------------------------------------

    @Test
    fun `the pill is the three record words`() {
        assertEquals("New best", heroPill(RecordVerdict.NewBest))
        assertEquals("Tied your best!", heroPill(RecordVerdict.TiedBest))
        assertEquals("First solve", heroPill(RecordVerdict.FirstSolve))
        assertNull(heroPill(RecordVerdict.NotBest))
        assertNull(heroPill(RecordVerdict.Replay))
        assertNull(heroPill(null))
    }

    // --- streak ----------------------------------------------------------------------------

    @Test
    fun `the streak title and sub line`() {
        assertEquals("1 day streak", heroStreakTitle(1))
        assertEquals("Day one. Come back tomorrow to keep it alive.", heroStreakSub(1))
        assertEquals("7 day streak", heroStreakTitle(7))
        assertEquals("Solve any puzzle tomorrow to make it 8.", heroStreakSub(7))
        assertEquals("365 day streak", heroStreakTitle(365))
        assertEquals("Solve any puzzle tomorrow to make it 366.", heroStreakSub(365))
    }

    // --- spread ----------------------------------------------------------------------------

    @Test
    fun `the spread heading and first-time line`() {
        assertEquals("Your times · Zip", heroSpreadHeading("Zip"))
        assertEquals("This is your first time — solve again to see how you spread out.", HERO_FIRST_TIME_LINE)
    }

    @Test
    fun `a faster solve says how many it beat and the average, the number in bold`() {
        val caption = heroSpreadCaption(time, best = false, beaten = 4, solves = 9, averageMs = 95_000L)

        assertEquals("Faster than 4 of your 9 solves. Average 1:35.", caption.text)
        assertEquals("4", caption.text.substring(caption.bold!!))
    }

    @Test
    fun `the fastest says so, in bold`() {
        val caption = heroSpreadCaption(time, best = true, beaten = 8, solves = 9, averageMs = 95_000L)

        assertEquals("Your fastest ever of your 9 solves.", caption.text)
        assertEquals("Your fastest ever", caption.text.substring(caption.bold!!))
    }

    @Test
    fun `a hints-first game says best yet and better than`() {
        assertEquals(
            "Your best yet of your 9 solves.",
            heroSpreadCaption(hints, best = true, beaten = 8, solves = 9, averageMs = 95_000L).text,
        )
        assertEquals(
            "Better than 4 of your 9 solves. Average 1:35.",
            heroSpreadCaption(hints, best = false, beaten = 4, solves = 9, averageMs = 95_000L).text,
        )
    }

    // --- rank, actions ---------------------------------------------------------------------

    @Test
    fun `the rank row names a top percent on a busy board and the place on a small one`() {
        assertEquals("Top 5%", heroRankValue(position = 11, count = 212, topPercent = 5))
        assertEquals("of players today", heroRankSub(212))
        assertEquals("#2", heroRankValue(position = 2, count = 3, topPercent = 67))
        assertEquals("of 3 players today", heroRankSub(3))
        assertEquals("Sign in to see where you rank today.", HERO_SIGN_IN_LINE)
    }

    // --- the model -------------------------------------------------------------------------

    private fun insights(
        earlier: List<BestScore> = listOf(BestScore(112_000L, 0), BestScore(90_000L, 0)),
        current: BestScore = BestScore(100_000L, 0),
        order: RankOrder = time,
        streak: Int = 3,
    ) = ResultInsights(
        streak = streak,
        week = (6 downTo 0).map { ResultDay(saturday.minusDays(it.toLong()), solved = it < 3, today = it == 0) },
        spread = timeSpread(earlier, current, order),
    )

    private fun facts(
        verdict: RecordVerdict? = RecordVerdict.NotBest,
        previous: BestScore? = BestScore(90_000L, 0),
        order: RankOrder = time,
        hintsUsed: Int = 0,
        practice: Boolean = false,
        replay: Boolean = false,
        insights: ResultInsights? = insights(),
        rank: ResultRank = ResultRank.Ranked(position = 14, count = 212, topPercent = 7),
        game: String = "Zip",
        number: Int? = 918,
    ) = ResultHeroFacts(
        timeMs = 100_000L,
        hintsUsed = hintsUsed,
        verdict = verdict,
        previousBest = previous,
        rankOrder = order,
        game = game,
        puzzleNumber = number,
        practice = practice,
        replay = replay,
        dateLine = "Today · Sat 7 Nov",
        doneToday = 2,
        total = 5,
        insights = insights,
        rank = rank,
        primary = ResultPrimaryAction.Done,
    )

    private fun model(facts: ResultHeroFacts) = resultHeroModel(facts, Locale.US)

    @Test
    fun `the default state is a slower solve with every card`() {
        val model = model(facts())

        assertEquals("Zip · #918", model.title)
        assertEquals("Today · Sat 7 Nov", model.dateLine)
        assertNull(model.pill)
        assertEquals("Your best is 1:30 · 10s off", model.bestLine)
        assertFalse(model.bestImproved)
        assertEquals(listOf("None" to "Hints", "2 of 5" to "Today", "1:41" to "Average"), model.chips.map { it.value to it.label })
        assertEquals("3 day streak", model.streak!!.title)
        assertEquals(listOf(false, false, false, false, true, true, true), model.streak!!.week.map { it.solved })
        assertTrue(model.streak!!.week.last().today)
        assertEquals("Your times · Zip", model.spread!!.heading)
        assertEquals("Faster than 1 of your 3 solves. Average 1:41.", model.spread!!.caption!!.text)
        assertEquals(HeroRank.Place("Top 7%", "of players today"), model.rank)
        assertFalse(model.celebrate)
        assertFalse(model.burst)
    }

    @Test
    fun `the week's labels are the weekday initials ending today`() {
        val week = model(facts()).streak!!.week

        assertEquals(listOf("S", "M", "T", "W", "T", "F", "S"), week.map { it.label })
    }

    @Test
    fun `a first solve has the pill, the ghost spread, day one and no average chip`() {
        val first = insights(earlier = emptyList(), streak = 1)

        val model = model(facts(verdict = RecordVerdict.FirstSolve, previous = null, insights = first))

        assertEquals("First solve", model.pill)
        assertEquals("First solve — this is your time to beat", model.bestLine)
        assertEquals(listOf("Hints", "Today"), model.chips.map { it.label })
        assertEquals("Day one. Come back tomorrow to keep it alive.", model.streak!!.sub)
        assertTrue(model.spread!!.bins.isEmpty())
        assertEquals("faster", model.spread!!.axisLow)
        assertEquals("slower", model.spread!!.axisHigh)
        assertEquals(HERO_FIRST_TIME_LINE, model.spread!!.firstTimeLine)
        assertNull(model.spread!!.caption)
        assertTrue(model.celebrate)
        assertFalse(model.burst)
    }

    @Test
    fun `a new best bursts and paints its line in the accent`() {
        val best = insights(current = BestScore(80_000L, 0))
        val facts = facts(verdict = RecordVerdict.NewBest, previous = BestScore(90_000L, 0), insights = best)

        val model = model(facts.copy(timeMs = 80_000L))

        assertEquals("New best", model.pill)
        assertEquals("10s faster than your best (1:30)", model.bestLine)
        assertTrue(model.bestImproved)
        assertTrue(model.celebrate)
        assertTrue(model.burst)
        assertEquals("Your fastest ever of your 3 solves.", model.spread!!.caption!!.text)
        assertEquals("1:17", model.spread!!.axisLow)
    }

    @Test
    fun `a tie keeps its pill and says no line`() {
        val model = model(facts(verdict = RecordVerdict.TiedBest, previous = BestScore(100_000L, 0)))

        assertEquals("Tied your best!", model.pill)
        assertNull(model.bestLine)
        assertFalse(model.celebrate)
    }

    @Test
    fun `hints show their count in the chip`() {
        assertEquals("3", model(facts(hintsUsed = 3)).chips.first().value)
        assertEquals("Hints", model(facts(hintsUsed = 3)).chips.first().label)
        assertEquals("Hint", model(facts(hintsUsed = 1)).chips.first().label)
    }

    @Test
    fun `a hints-first game ranks the line, caption and spread by hints`() {
        val strands = facts(
            verdict = RecordVerdict.NotBest,
            previous = BestScore(100_000L, 0),
            order = hints,
            hintsUsed = 2,
            insights = insights(
                earlier = listOf(BestScore(100_000L, 0), BestScore(90_000L, 3)),
                current = BestScore(100_000L, 2),
                order = hints,
            ),
            game = "Strands",
        )

        val model = model(strands)

        assertEquals("Your best is Perfect", model.bestLine)
        assertEquals("Better than 1 of your 3 solves. Average 1:35.", model.spread!!.caption!!.text)
    }

    @Test
    fun `a signed-out player is asked to sign in, and a missing rank hides the row`() {
        assertEquals(HeroRank.SignIn("Sign in to see where you rank today."), model(facts(rank = ResultRank.SignedOut)).rank)
        assertNull(model(facts(rank = ResultRank.Hidden)).rank)
    }

    @Test
    fun `a replay hides the pill, streak and rank and keeps the spread`() {
        val model = model(facts(verdict = RecordVerdict.NewBest, replay = true))

        assertNull(model.pill)
        assertNull(model.streak)
        assertNull(model.rank)
        assertNotNull(model.spread)
        assertEquals("Replay · Best 1:30", model.bestLine)
        assertFalse(model.burst)
        assertFalse(model.celebrate)
    }

    @Test
    fun `a practice puzzle hides the pill, streak, spread, rank and average`() {
        val model = model(facts(verdict = RecordVerdict.NewBest, practice = true, number = null, insights = null))

        assertEquals("Zip · Practice", model.title)
        assertNull(model.pill)
        assertNull(model.bestLine)
        assertNull(model.streak)
        assertNull(model.spread)
        assertNull(model.rank)
        assertEquals(listOf("Hints", "Today"), model.chips.map { it.label })
        assertFalse(model.celebrate)
    }

    @Test
    fun `a practice puzzle hides everything even when insights are handed in`() {
        val model = model(facts(practice = true, number = null))

        assertNull(model.streak)
        assertNull(model.spread)
        assertNull(model.rank)
        assertEquals(listOf("Hints", "Today"), model.chips.map { it.label })
    }

    @Test
    fun `cards wait for their data`() {
        val model = model(facts(insights = null, rank = ResultRank.Hidden))

        assertNull(model.streak)
        assertNull(model.spread)
        assertNull(model.rank)
        assertEquals(listOf("Hints", "Today"), model.chips.map { it.label })
    }

    @Test
    fun `a long streak and a long name keep their words`() {
        val long = insights(streak = 365)

        val model = model(facts(insights = long, game = "A very long game name"))

        assertEquals("365 day streak", model.streak!!.title)
        assertEquals("Solve any puzzle tomorrow to make it 366.", model.streak!!.sub)
        assertEquals("A very long game name · #918", model.title)
        assertEquals("Your times · A very long game name", model.spread!!.heading)
    }
}
