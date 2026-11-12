package com.ivandsky.puzzlebox.gameui

import com.ivandsky.puzzlebox.domain.ResultDay
import com.ivandsky.puzzlebox.domain.ResultInsights
import com.ivandsky.puzzlebox.domain.ResultRank
import com.ivandsky.puzzlebox.domain.SpreadScope
import com.ivandsky.puzzlebox.domain.TimeSpread
import com.ivandsky.puzzlebox.domain.WeekdaySpread
import com.ivandsky.puzzlebox.domain.timeSpread
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.RankOrder
import java.time.DayOfWeek
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
    fun `a play-again names the best it did not move`() {
        assertEquals("Practice · Best 1:52", line(RecordVerdict.PlayAgain, BestScore(112_000L, 0)))
        assertEquals("Practice · Best 2 hints · 1:52", line(RecordVerdict.PlayAgain, BestScore(112_000L, 2), hints))
        assertEquals("Practice", line(RecordVerdict.PlayAgain, null))
    }

    // --- pill ------------------------------------------------------------------------------

    @Test
    fun `the pill is the three record words`() {
        assertEquals("New best", heroPill(RecordVerdict.NewBest))
        assertEquals("Tied your best!", heroPill(RecordVerdict.TiedBest))
        assertEquals("First solve", heroPill(RecordVerdict.FirstSolve))
        assertNull(heroPill(RecordVerdict.NotBest))
        assertNull(heroPill(RecordVerdict.PlayAgain))
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

    private val all = SpreadScope.All
    private val weekday = SpreadScope.Weekday

    private fun spreadOf(earlier: List<BestScore>, current: BestScore = BestScore(100_000L, 0), order: RankOrder = time) =
        timeSpread(earlier, current, order)

    private fun verdict(
        scope: SpreadScope,
        spread: TimeSpread,
        order: RankOrder = time,
        playAgain: Boolean = false,
    ) = heroSpreadVerdict(scope, order, spread, "Zip", "Monday", playAgain)

    @Test
    fun `the heading names the count, the scope word as the chip and the game`() {
        assertEquals(HeroHeading("Your 12 ", "any day", " Zip times"), heroSpreadHeading(all, 12, "Zip", "Monday", switchable = true))
        assertEquals(HeroHeading("Your 5 ", "Monday", " Zip times"), heroSpreadHeading(weekday, 5, "Zip", "Monday", switchable = true))
        assertEquals("Your 5 Monday Zip times", heroSpreadHeading(weekday, 5, "Zip", "Monday", switchable = true).text)
    }

    @Test
    fun `scopes holding the same solves have no chip and no switch hint`() {
        assertEquals(HeroHeading("Your first Zip time", null, ""), heroSpreadHeading(all, 1, "Zip", "Monday", switchable = false))
        assertEquals("Your 4 Zip times", heroSpreadHeading(all, 4, "Zip", "Monday", switchable = false).text)
        assertFalse(heroSpreadCaption(all, first = true, weekday = "Monday", switchable = false).contains("Tap the highlighted"))
        assertFalse(heroSpreadCaption(all, first = false, weekday = "Monday", switchable = false).contains("switch"))
    }

    @Test
    fun `the chip shows only when the scopes differ in solve count`() {
        val same = model(facts(insights = insights(earlier = listOf(BestScore(90_000L, 0)), weekdayEarlier = listOf(BestScore(90_000L, 0)))))
        val differ = model(facts(insights = insights(earlier = listOf(BestScore(90_000L, 0), BestScore(95_000L, 0)), weekdayEarlier = listOf(BestScore(90_000L, 0)))))

        assertFalse(same.spread!!.switchable)
        assertNull(same.spread!!.views.getValue(all).heading.chip)
        assertTrue(differ.spread!!.switchable)
        assertEquals("any day", differ.spread!!.views.getValue(all).heading.chip)
        assertEquals("Saturday", differ.spread!!.views.getValue(weekday).heading.chip)
    }

    @Test
    fun `a first solve in scope is Your first time`() {
        assertEquals("Your first Monday Zip time", heroSpreadHeading(weekday, 1, "Zip", "Monday", switchable = true).text)
        assertEquals("Your first any day Zip time", heroSpreadHeading(all, 1, "Zip", "Monday", switchable = true).text)
    }

    @Test
    fun `the caption explains what a bar counts, per scope, and how to switch`() {
        assertEquals(
            "Each bar counts how many of your solves landed in that time range, across every day of the week. " +
                "Taller means more solves. Tap the highlighted word to switch.",
            heroSpreadCaption(all, first = false, weekday = "Monday", switchable = true),
        )
        assertEquals(
            "The one bar is today's solve. Each bar counts solves in a time range — your own solves only, " +
                "no other players. Tap the highlighted word to switch.",
            heroSpreadCaption(all, first = true, weekday = "Monday", switchable = true),
        )
        assertEquals(
            "Same chart, Mondays only. Daily puzzles get easier or harder through the week, so a Monday is " +
                "compared with your other Mondays. Tap the highlighted word to switch.",
            heroSpreadCaption(weekday, first = false, weekday = "Monday", switchable = true),
        )
        assertEquals(
            "The one bar is today's Monday. Play more Mondays and the bars show how your Monday times spread out. " +
                "Tap the highlighted word to switch.",
            heroSpreadCaption(weekday, first = true, weekday = "Monday", switchable = true),
        )
    }

    @Test
    fun `a first solve in all days invites another and has nothing bold`() {
        val line = verdict(all, spreadOf(emptyList()))

        assertEquals("Your first Zip time — solve again and the bars show how your times spread out.", line.text)
        assertNull(line.bold)
    }

    @Test
    fun `a first solve on a weekday says nothing to compare yet, the first in bold`() {
        val line = verdict(weekday, spreadOf(emptyList()))

        assertEquals("First Monday — no earlier Monday Zip to compare with yet.", line.text)
        assertEquals("First Monday", line.text.substring(line.bold!!))
    }

    @Test
    fun `the fastest says so with the average of the earlier solves`() {
        val spread = spreadOf(listOf(BestScore(112_000L, 0), BestScore(90_000L, 0)), BestScore(80_000L, 0))

        val line = verdict(all, spread)

        assertEquals("Fastest ever of your 3 Zip. Average 1:41.", line.text)
        assertEquals("Fastest ever", line.text.substring(line.bold!!))
        assertEquals("Fastest Monday of your 3 Monday Zip. Monday average 1:41.", verdict(weekday, spread).text)
    }

    @Test
    fun `a middle solve is its rank, in bold, of the solves in scope`() {
        val spread = spreadOf(listOf(BestScore(112_000L, 0), BestScore(90_000L, 0), BestScore(121_000L, 0)))

        val line = verdict(all, spread)

        assertEquals("2nd fastest of your 4 Zip. Average 1:47.", line.text)
        assertEquals("2nd fastest", line.text.substring(line.bold!!))
        assertEquals("2nd fastest of your 4 Monday Zip. Monday average 1:47.", verdict(weekday, spread).text)
    }

    @Test
    fun `ordinals take their suffix`() {
        val earlier = List(10) { BestScore(10_000L * (it + 1), 0) }

        assertEquals("11th fastest of your 11 Zip. Average 0:55.", verdict(all, spreadOf(earlier, BestScore(200_000L, 0))).text)
        assertEquals("3rd fastest", verdict(all, spreadOf(earlier, BestScore(25_000L, 0))).text.substringBefore(" of"))
    }

    @Test
    fun `a play-again appends that its bar is dashed and not saved`() {
        val spread = spreadOf(listOf(BestScore(112_000L, 0), BestScore(90_000L, 0)))

        assertEquals("2nd fastest of your 3 Zip. Average 1:41. Dashed = this run, not saved.", verdict(all, spread, playAgain = true).text)
        assertEquals("Your first Zip time — solve again and the bars show how your times spread out. Dashed = this run, not saved.",
            verdict(all, spreadOf(emptyList()), playAgain = true).text)
    }

    @Test
    fun `a hints-first game says best and ranks by hints`() {
        val spread = spreadOf(listOf(BestScore(100_000L, 0), BestScore(90_000L, 3)), BestScore(100_000L, 2), hints)
        val best = spreadOf(listOf(BestScore(100_000L, 2)), BestScore(100_000L, 0), hints)

        assertEquals("2nd best of your 3 Zip. Average 1:35.", verdict(all, spread, hints).text)
        assertEquals("Best ever of your 2 Zip. Average 1:40.", verdict(all, best, hints).text)
        assertEquals("Best Monday of your 2 Monday Zip. Monday average 1:40.", verdict(weekday, best, hints).text)
    }

    @Test
    fun `a hints-first game with several solves says rank counts hints first`() {
        val several = spreadOf(listOf(BestScore(100_000L, 0)), order = hints)

        assertEquals(
            "Rank counts hints first, then time — the bars show time only.",
            heroRankNote(hints, several)!!.text,
        )
    }

    @Test
    fun `no rank note for a first solve or a time-first game`() {
        assertNull(heroRankNote(hints, spreadOf(emptyList(), order = hints)))
        assertNull(heroRankNote(time, spreadOf(listOf(BestScore(100_000L, 0)))))
    }

    @Test
    fun `weekday and all days compare their averages once both have two earlier solves`() {
        val everyday = spreadOf(listOf(BestScore(100_000L, 0), BestScore(100_000L, 0), BestScore(140_000L, 0)))
        val mondays = spreadOf(listOf(BestScore(80_000L, 0), BestScore(100_000L, 0)))

        assertEquals(
            "Mondays run faster for you — average 1:30 vs 1:53 across all days.",
            heroWeekdayNote(everyday, mondays, "Monday", fastest = false)!!.text,
        )
        assertEquals(
            "Mondays run slower for you — average 1:53 vs 1:30 across all days.",
            heroWeekdayNote(mondays, everyday, "Monday", fastest = false)!!.text,
        )
    }

    @Test
    fun `the fastest weekday is named in bold`() {
        val everyday = spreadOf(listOf(BestScore(100_000L, 0), BestScore(100_000L, 0), BestScore(140_000L, 0)))
        val mondays = spreadOf(listOf(BestScore(80_000L, 0), BestScore(100_000L, 0)))

        val line = heroWeekdayNote(everyday, mondays, "Monday", fastest = true)!!

        assertEquals("Mondays are your fastest day — average 1:30 vs 1:53 across all days.", line.text)
        assertEquals("Mondays are your fastest day", line.text.substring(line.bold!!))
    }

    @Test
    fun `no weekday line until both scopes have two earlier solves or when the averages match`() {
        val everyday = spreadOf(listOf(BestScore(100_000L, 0), BestScore(100_000L, 0), BestScore(140_000L, 0)))
        val oneMonday = spreadOf(listOf(BestScore(80_000L, 0)))
        val level = spreadOf(listOf(BestScore(100_000L, 0), BestScore(100_000L, 0)))

        assertNull(heroWeekdayNote(everyday, oneMonday, "Monday", fastest = false))
        assertNull(heroWeekdayNote(level, level, "Monday", fastest = false))
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
        weekdayEarlier: List<BestScore> = emptyList(),
        streak: Int = 3,
    ) = ResultInsights(
        streak = streak,
        week = (6 downTo 0).map { ResultDay(saturday.minusDays(it.toLong()), solved = it < 3, today = it == 0) },
        spread = timeSpread(earlier, current, order),
        weekday = WeekdaySpread(DayOfWeek.SATURDAY, timeSpread(weekdayEarlier, current, order), fastest = false),
    )

    private fun facts(
        verdict: RecordVerdict? = RecordVerdict.NotBest,
        previous: BestScore? = BestScore(90_000L, 0),
        order: RankOrder = time,
        hintsUsed: Int = 0,
        practice: Boolean = false,
        playAgain: Boolean = false,
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
        playAgain = playAgain,
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
        val spread = model.spread!!
        assertEquals(SpreadScope.All, spread.defaultScope)
        assertFalse(spread.playAgain)
        val view = spread.views.getValue(all)
        assertEquals("Your 3 any day Zip times", view.heading.text)
        assertEquals("2nd fastest of your 3 Zip. Average 1:41.", view.verdict.text)
        assertEquals(listOf("1:30–1:40", "1:40–1:50", "1:50–2:00"), view.bars.map { it.range })
        assertEquals(listOf("1:30", "1:40", "1:50", "2:00"), view.edges)
        assertEquals(1, view.youBin)
        assertEquals("Your 3 any day Zip times. 3 time ranges, counts per range. Today 1:40.", view.description)
        val day = spread.views.getValue(weekday)
        assertEquals("Your first Saturday Zip time", day.heading.text)
        assertEquals(1, day.bars.size)
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
    fun `a first solve has the pill, one bar, day one and no average chip`() {
        val first = insights(earlier = emptyList(), streak = 1)

        val model = model(facts(verdict = RecordVerdict.FirstSolve, previous = null, insights = first))

        assertEquals("First solve", model.pill)
        assertEquals("First solve — this is your time to beat", model.bestLine)
        assertEquals(listOf("Hints", "Today"), model.chips.map { it.label })
        assertEquals("Day one. Come back tomorrow to keep it alive.", model.streak!!.sub)
        val view = model.spread!!.views.getValue(all)
        assertEquals(1, view.bars.size)
        assertEquals("Your first Zip time", view.heading.text)
        assertNull(view.heading.chip)
        assertFalse(model.spread!!.switchable)
        assertEquals(all, model.spread!!.defaultScope)
        assertFalse(view.caption.contains("Tap the highlighted word"))
        assertEquals(1, view.bars.single().count)
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
        assertEquals("Fastest ever of your 3 Zip. Average 1:41.", model.spread!!.views.getValue(all).verdict.text)
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
        val view = model.spread!!.views.getValue(all)
        assertEquals("2nd best of your 3 Strands. Average 1:35.", view.verdict.text)
        assertEquals(listOf("Rank counts hints first, then time — the bars show time only."), view.notes.map { it.text })
    }

    @Test
    fun `a signed-out player is asked to sign in, and a missing rank hides the row`() {
        assertEquals(HeroRank.SignIn("Sign in to see where you rank today."), model(facts(rank = ResultRank.SignedOut)).rank)
        assertNull(model(facts(rank = ResultRank.Hidden)).rank)
    }

    @Test
    fun `connections add their combined place under the unchanged PuzzleBox rank`() {
        val withConnections = ResultRank.Ranked(14, 212, 7, ResultRank.WithConnections(position = 3, count = 12))

        assertEquals(
            HeroRank.Place("Top 7%", "of players today", connections = "3rd of 12 with connections"),
            model(facts(rank = withConnections)).rank,
        )
    }

    @Test
    fun `the connections place reads as an English ordinal`() {
        fun line(position: Int) = (model(facts(rank = ResultRank.Ranked(14, 212, 7, ResultRank.WithConnections(position, 120)))).rank as HeroRank.Place).connections

        assertEquals(
            listOf("1st", "2nd", "3rd", "4th", "11th", "12th", "13th", "21st", "22nd", "23rd", "101st", "111th").map { "$it of 120 with connections" },
            listOf(1, 2, 3, 4, 11, 12, 13, 21, 22, 23, 101, 111).map(::line),
        )
    }

    @Test
    fun `a rank without connections has no connections line`() {
        assertNull((model(facts()).rank as HeroRank.Place).connections)
    }

    @Test
    fun `a play-again hides the pill, streak and rank and keeps the spread`() {
        val model = model(facts(verdict = RecordVerdict.NewBest, playAgain = true))

        assertNull(model.pill)
        assertNull(model.streak)
        assertNull(model.rank)
        assertNotNull(model.spread)
        assertEquals("Practice · Best 1:30", model.bestLine)
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
        assertEquals("Your 3 any day A very long game name times", model.spread!!.views.getValue(all).heading.text)
    }
}
