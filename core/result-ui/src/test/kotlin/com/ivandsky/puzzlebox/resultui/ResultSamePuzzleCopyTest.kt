package com.ivandsky.puzzlebox.resultui

import com.ivandsky.puzzlebox.domain.ResultRank
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.RecordVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The same-puzzle comparison line, its second line and how the hero model picks between them and the game-wide wording. */
class ResultSamePuzzleCopyTest {

    private val time = RankOrder.TimeThenHints
    private val hints = RankOrder.HintsThenTime

    private fun line(
        previousMs: Long,
        timeMs: Long = 102_000L,
        linkedIn: Boolean = false,
        order: RankOrder = time,
        previousHints: Int = 0,
        hintsUsed: Int = 0,
    ) = heroSamePuzzleLine(BestScore(previousMs, previousHints), linkedIn, order, timeMs, hintsUsed)

    @Test
    fun `a faster solve names the gap and last time`() {
        assertEquals("12s faster than last time (1:54)", line(previousMs = 114_000L, timeMs = 102_000L))
    }

    @Test
    fun `a slower solve names the gap and last time`() {
        assertEquals("12s slower than last time (1:54)", line(previousMs = 114_000L, timeMs = 126_000L))
    }

    @Test
    fun `a gap of a minute or more reads m colon ss`() {
        assertEquals("1:05 faster than last time (2:59)", line(previousMs = 179_000L, timeMs = 114_000L))
    }

    @Test
    fun `the same second is the same time`() {
        assertEquals("Same time as last time", line(previousMs = 102_400L, timeMs = 102_900L))
    }

    @Test
    fun `a LinkedIn previous solve says so`() {
        assertEquals("12s faster than your LinkedIn time (1:54)", line(previousMs = 114_000L, timeMs = 102_000L, linkedIn = true))
        assertEquals("12s slower than your LinkedIn time (1:54)", line(previousMs = 114_000L, timeMs = 126_000L, linkedIn = true))
        assertEquals("Same as your LinkedIn time (1:54)", line(previousMs = 114_000L, timeMs = 114_000L, linkedIn = true))
    }

    @Test
    fun `a hints-first game with fewer hints says so`() {
        assertEquals(
            "Fewer hints than last time (2 hints · 3:05)",
            line(previousMs = 185_000L, timeMs = 200_000L, order = hints, previousHints = 2, hintsUsed = 0),
        )
    }

    @Test
    fun `a hints-first game with more hints says so`() {
        assertEquals(
            "More hints than last time (Perfect · 4:12)",
            line(previousMs = 252_000L, timeMs = 200_000L, order = hints, previousHints = 0, hintsUsed = 1),
        )
        assertEquals(
            "More hints than your LinkedIn time (1 hint · 4:12)",
            line(previousMs = 252_000L, timeMs = 200_000L, order = hints, previousHints = 1, hintsUsed = 3, linkedIn = true),
        )
    }

    @Test
    fun `a hints-first game with level hints falls to the time gap`() {
        assertEquals(
            "10s faster than last time (3:30)",
            line(previousMs = 210_000L, timeMs = 200_000L, order = hints, previousHints = 1, hintsUsed = 1),
        )
    }

    @Test
    fun `a time-first game ignores hints`() {
        assertEquals(
            "12s faster than last time (1:54)",
            line(previousMs = 114_000L, timeMs = 102_000L, previousHints = 3, hintsUsed = 0),
        )
    }

    // --- the second line -------------------------------------------------------------------

    private fun solves(previousMs: Long, bestMs: Long, previousHints: Int = 0, bestHints: Int = 0) =
        SamePuzzleSolves(BestScore(previousMs, previousHints), best = BestScore(bestMs, bestHints))

    @Test
    fun `a best that differs from the previous solve gets its own line`() {
        assertEquals("Best on this puzzle 1:41", heroSamePuzzleBest(solves(previousMs = 114_000L, bestMs = 101_000L), time))
    }

    @Test
    fun `a best that is the previous solve adds nothing`() {
        assertNull(heroSamePuzzleBest(solves(previousMs = 114_000L, bestMs = 114_000L), time))
        assertNull(heroSamePuzzleBest(solves(previousMs = 114_400L, bestMs = 114_900L), time))
    }

    @Test
    fun `no previous solve has no second line`() {
        assertNull(heroSamePuzzleBest(SamePuzzleSolves.None, time))
        assertNull(heroSamePuzzleBest(SamePuzzleSolves.Unread, time))
    }

    @Test
    fun `a hints-first best shows as the game shows a best`() {
        val same = solves(previousMs = 185_000L, bestMs = 150_000L, previousHints = 2, bestHints = 0)
        assertEquals("Best on this puzzle Perfect · 2:30", heroSamePuzzleBest(same, hints))
    }

    // --- the model -------------------------------------------------------------------------

    private fun facts(
        samePuzzle: SamePuzzleSolves?,
        verdict: RecordVerdict? = RecordVerdict.NotBest,
        previous: BestScore? = BestScore(60_000L, 0),
        playAgain: Boolean = false,
        practice: Boolean = false,
        order: RankOrder = time,
        timeMs: Long = 102_000L,
    ) = ResultHeroFacts(
        timeMs = timeMs,
        hintsUsed = 0,
        verdict = verdict,
        previousBest = previous,
        rankOrder = order,
        game = "Zip",
        puzzleNumber = 918,
        practice = practice,
        playAgain = playAgain,
        dateLine = "Today · Sat 7 Nov",
        doneToday = 2,
        total = 5,
        insights = null,
        rank = ResultRank.Hidden,
        primary = ResultPrimaryAction.Done,
        moments = emptyList(),
        challenge = null,
        samePuzzle = samePuzzle,
    )

    private val earlier = solves(previousMs = 114_000L, bestMs = 101_000L)

    @Test
    fun `with an earlier solve the hero compares with it, not with the game's best`() {
        val model = resultHeroModel(facts(earlier))

        assertEquals("12s faster than last time (1:54)", model.bestLine)
        assertEquals("Best on this puzzle 1:41", model.bestSubLine)
        assertTrue(model.compare)
    }

    @Test
    fun `the pill stays the game-wide verdict`() {
        val model = resultHeroModel(facts(earlier, verdict = RecordVerdict.NewBest))

        assertEquals("New best", model.pill)
        assertTrue(model.bestImproved)
    }

    @Test
    fun `the comparison survives an unknown verdict`() {
        val model = resultHeroModel(facts(earlier, verdict = null, previous = null))

        assertEquals("12s faster than last time (1:54)", model.bestLine)
        assertNull(model.pill)
    }

    @Test
    fun `no earlier solve keeps today's first-solve and not-best wording`() {
        val first = resultHeroModel(facts(SamePuzzleSolves.None, verdict = RecordVerdict.FirstSolve, previous = null))
        val notBest = resultHeroModel(facts(SamePuzzleSolves.None, verdict = RecordVerdict.NotBest, previous = BestScore(90_000L, 0)))

        assertEquals("First solve — this is your time to beat", first.bestLine)
        assertEquals("Your best is 1:30 · 12s off", notBest.bestLine)
        assertNull(notBest.bestSubLine)
        assertTrue(first.compare)
    }

    @Test
    fun `before the earlier solves are read the hero says nothing about them`() {
        val model = resultHeroModel(facts(SamePuzzleSolves.Unread))

        assertNull(model.bestLine)
        assertNull(model.bestSubLine)
        assertTrue(model.compare)
    }

    @Test
    fun `without the same-puzzle facts the hero is what it was`() {
        val model = resultHeroModel(facts(samePuzzle = null))

        assertEquals("Your best is 1:00 · 42s off", model.bestLine)
        assertNull(model.bestSubLine)
        assertFalse(model.compare)
    }

    @Test
    fun `a play again compares with the earlier solve under its practice label`() {
        val model = resultHeroModel(facts(earlier, playAgain = true))

        assertEquals("Practice · 12s faster than last time (1:54)", model.bestLine)
        assertEquals("Best on this puzzle 1:41", model.bestSubLine)
        assertNull(model.pill)
    }

    @Test
    fun `a practice puzzle says nothing`() {
        val model = resultHeroModel(facts(earlier, practice = true))

        assertNull(model.bestLine)
        assertNull(model.bestSubLine)
    }

    // --- the compare columns ---------------------------------------------------------------

    private fun compare(
        previousMs: Long,
        timeMs: Long = 102_000L,
        bestMs: Long = previousMs,
        linkedIn: Boolean = false,
        order: RankOrder = time,
        previousHints: Int = 0,
        bestHints: Int = previousHints,
        hintsUsed: Int = 0,
    ) = heroSamePuzzleCompare(
        SamePuzzleSolves(BestScore(previousMs, previousHints), linkedIn, BestScore(bestMs, bestHints), columns = true),
        order, timeMs, hintsUsed,
    )!!

    @Test
    fun `a faster solve tags the gap with a minus in the game's colour`() {
        val compare = compare(previousMs = 114_000L, timeMs = 102_000L, bestMs = 101_000L)

        assertEquals(HeroCompareColumn("PREVIOUS", "1:54"), compare.previous)
        assertEquals(HeroGapTag("\u221212s", HeroGapTone.Better), compare.tag)
        assertEquals(HeroCompareColumn("BEST", "1:41"), compare.best)
    }

    @Test
    fun `a slower solve tags the gap with a plus on the neutral container`() {
        assertEquals(HeroGapTag("+1s", HeroGapTone.Worse), compare(previousMs = 101_000L, timeMs = 102_000L).tag)
    }

    @Test
    fun `the same second reads same`() {
        assertEquals(HeroGapTag("same", HeroGapTone.Level), compare(previousMs = 102_400L, timeMs = 102_900L).tag)
    }

    @Test
    fun `a column gap of a minute or more reads m colon ss`() {
        assertEquals(HeroGapTag("+1:12", HeroGapTone.Worse), compare(previousMs = 100_000L, timeMs = 172_000L).tag)
        assertEquals(HeroGapTag("\u22121:05", HeroGapTone.Better), compare(previousMs = 179_000L, timeMs = 114_000L).tag)
    }

    @Test
    fun `a LinkedIn previous solve is labelled LINKEDIN`() {
        assertEquals("LINKEDIN", compare(previousMs = 114_000L, linkedIn = true).previous.label)
    }

    @Test
    fun `a best equal to the previous solve has no BEST column`() {
        assertNull(compare(previousMs = 114_000L, bestMs = 114_400L).best)
    }

    @Test
    fun `a hints-first game with different hints counts hints`() {
        val fewer = compare(previousMs = 185_000L, timeMs = 200_000L, order = hints, previousHints = 2, hintsUsed = 1, bestMs = 150_000L, bestHints = 0)

        assertEquals(HeroCompareColumn("PREVIOUS", "2 hints"), fewer.previous)
        assertEquals(HeroGapTag("\u22121 hint", HeroGapTone.Better), fewer.tag)
        assertEquals(HeroCompareColumn("BEST", "Perfect"), fewer.best)

        val more = compare(previousMs = 185_000L, timeMs = 200_000L, order = hints, previousHints = 1, hintsUsed = 2)
        assertEquals(HeroCompareColumn("PREVIOUS", "1 hint"), more.previous)
        assertEquals(HeroGapTag("+1 hint", HeroGapTone.Worse), more.tag)
        assertNull(more.best)
    }

    @Test
    fun `a hints-first game with level hints compares the time`() {
        val level = compare(previousMs = 210_000L, timeMs = 200_000L, order = hints, previousHints = 1, hintsUsed = 1)

        assertEquals("3:30", level.previous.value)
        assertEquals(HeroGapTag("\u221210s", HeroGapTone.Better), level.tag)
    }

    @Test
    fun `a first solve has no columns`() {
        assertNull(heroSamePuzzleCompare(SamePuzzleSolves.None.copy(columns = true), time, 102_000L, 0))
    }

    @Test
    fun `the model swaps the sentence and the best line for the columns, and a first solve keeps its wording`() {
        val columns = resultHeroModel(facts(earlier.copy(columns = true)))

        assertNull(columns.bestLine)
        assertNull(columns.bestSubLine)
        assertEquals(HeroCompareColumn("BEST", "1:41"), columns.compareColumns!!.best)

        val first = resultHeroModel(facts(SamePuzzleSolves.None.copy(columns = true), verdict = RecordVerdict.FirstSolve, previous = null))
        assertEquals("First solve — this is your time to beat", first.bestLine)
        assertNull(first.compareColumns)
    }

    @Test
    fun `a play again shows the same columns and a practice puzzle none`() {
        assertEquals("1:54", resultHeroModel(facts(earlier.copy(columns = true), playAgain = true)).compareColumns!!.previous.value)
        assertNull(resultHeroModel(facts(earlier.copy(columns = true), practice = true)).compareColumns)
    }

    @Test
    fun `with the columns off the sentence stays`() {
        val model = resultHeroModel(facts(earlier))

        assertNull(model.compareColumns)
        assertEquals("12s faster than last time (1:54)", model.bestLine)
    }
}
