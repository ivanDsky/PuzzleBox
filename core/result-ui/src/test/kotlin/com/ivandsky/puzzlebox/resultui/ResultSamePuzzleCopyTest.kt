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

/** The same-puzzle comparison: its compare columns and how the hero model picks between them and the game-wide wording. */
class ResultSamePuzzleCopyTest {

    private val time = RankOrder.TimeThenHints
    private val hints = RankOrder.HintsThenTime

    private fun solves(previousMs: Long, bestMs: Long, previousHints: Int = 0, bestHints: Int = 0) =
        SamePuzzleSolves(BestScore(previousMs, previousHints), best = BestScore(bestMs, bestHints))

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

        assertNull(model.bestLine)
        assertEquals(HeroCompareColumn("PREVIOUS", "1:54"), model.compareColumns!!.previous)
        assertEquals(HeroCompareColumn("BEST", "1:41"), model.compareColumns!!.best)
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

        assertEquals(HeroCompareColumn("PREVIOUS", "1:54"), model.compareColumns!!.previous)
        assertNull(model.pill)
    }

    @Test
    fun `no earlier solve keeps today's first-solve wording`() {
        val first = resultHeroModel(facts(SamePuzzleSolves.None, verdict = RecordVerdict.FirstSolve, previous = null))

        assertEquals("First solve — this is your time to beat", first.bestLine)
        assertNull(first.compareColumns)
        assertTrue(first.compare)
    }

    @Test
    fun `before the earlier solves are read the hero says nothing about them`() {
        val model = resultHeroModel(facts(SamePuzzleSolves.Unread))

        assertNull(model.bestLine)
        assertNull(model.compareColumns)
        assertTrue(model.compare)
    }

    @Test
    fun `without the same-puzzle facts the hero is what it was`() {
        val model = resultHeroModel(facts(samePuzzle = null))

        assertEquals("Your best is 1:00 · 42s off", model.bestLine)
        assertFalse(model.compare)
    }

    @Test
    fun `a play again compares with the earlier solve and has no pill`() {
        val model = resultHeroModel(facts(earlier, playAgain = true))

        assertNull(model.bestLine)
        assertEquals("1:54", model.compareColumns!!.previous.value)
        assertNull(model.pill)
    }

    @Test
    fun `a practice puzzle says nothing`() {
        val model = resultHeroModel(facts(earlier, practice = true))

        assertNull(model.bestLine)
        assertNull(model.compareColumns)
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
        SamePuzzleSolves(BestScore(previousMs, previousHints), linkedIn, BestScore(bestMs, bestHints)),
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
    fun `a time-first game ignores hints`() {
        val level = compare(previousMs = 114_000L, timeMs = 102_000L, previousHints = 3, hintsUsed = 0)

        assertEquals("1:54", level.previous.value)
        assertEquals(HeroGapTag("\u221212s", HeroGapTone.Better), level.tag)
    }

    @Test
    fun `a first solve has no columns`() {
        assertNull(heroSamePuzzleCompare(SamePuzzleSolves.None, time, 102_000L, 0))
    }
}
