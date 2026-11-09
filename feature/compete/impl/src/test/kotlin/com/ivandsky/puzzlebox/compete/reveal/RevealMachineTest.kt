package com.ivandsky.puzzlebox.compete.reveal

import com.ivandsky.puzzlebox.compete.reveal.RevealMachine.Companion.DuelSpeedUp
import com.ivandsky.puzzlebox.compete.reveal.RevealMachine.Companion.IntroMs
import com.ivandsky.puzzlebox.compete.reveal.RevealMachine.Companion.LegMs
import com.ivandsky.puzzlebox.domain.LegBasis
import com.ivandsky.puzzlebox.domain.LegVerdict
import com.ivandsky.puzzlebox.gameui.DuelSideState
import com.ivandsky.puzzlebox.model.GameId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Drives [RevealMachine] on a virtual clock: every instant is reached by `advance`, never by a real timer. */
class RevealMachineTest {

    @Test
    fun phasesRunIntroThenEachPuzzleInOrderThenScoreThenEnd() {
        val run = steps(RevealMachine(mixedLegs.take(3), reduced = false))

        assertEquals(
            listOf(RevealPhase.VsIntro, RevealPhase.Leg(0), RevealPhase.Leg(1), RevealPhase.Leg(2), RevealPhase.Score, RevealPhase.End),
            run.map { it.phase }.distinctConsecutive(),
        )
        assertEquals(RevealPhase.VsIntro, stateAt(mixedLegs, IntroMs - 1).phase)
        assertEquals(RevealPhase.Leg(0), stateAt(mixedLegs, IntroMs).phase)
        assertEquals(RevealPhase.Leg(1), stateAt(mixedLegs, IntroMs + LegMs).phase)
    }

    @Test
    fun winnerTickerFinishesBeforeLoserAndLocksOnce() {
        val cases = listOf(
            leg(time(30_000), time(45_000), LegVerdict.Win(LegBasis.Time)) to Side.Mine,
            leg(time(45_000), time(30_000), LegVerdict.Loss(LegBasis.Time)) to Side.Theirs,
            // Hints decide: the winner was slower on the clock, yet its ticker still finishes and locks first.
            leg(time(50_000), time(40_000), LegVerdict.Win(LegBasis.Hints)) to Side.Mine,
        )
        for ((leg, winner) in cases) {
            val race = steps(RevealMachine(listOf(leg), reduced = false)).filter { it.phase == RevealPhase.Leg(0) }
            val mineFull = race.indexOfFirst { it.myTickMs == leg.mine.ms }
            val theirsFull = race.indexOfFirst { it.theirTickMs == leg.theirs.ms }
            val winnerFull = if (winner == Side.Mine) mineFull else theirsFull
            val loserFull = if (winner == Side.Mine) theirsFull else mineFull

            assertTrue("$leg: winner finishes first", winnerFull in 1 until loserFull)
            assertEquals("$leg: locks once, for the winner", listOf(null, winner), race.map { it.locked }.distinctConsecutive())
            assertEquals("$leg: locks as the winner's ticker finishes", winnerFull, race.indexOfFirst { it.locked != null })
        }
    }

    @Test
    fun winnerDurationIsClampedBetween1200And1900() {
        // 2400 x 1/100 would be 24 ms; 2400 x 99/100 would be 2376 ms; 2400 x 30/48 is 1500 ms, inside the clamp.
        val cases = listOf(1_000L to 1_200L, 99_000L to 1_900L, 30_000L to 1_500L)
        for ((winnerMs, runMs) in cases) {
            val loserMs = if (winnerMs == 30_000L) 48_000L else 100_000L
            val legs = listOf(leg(time(winnerMs), time(loserMs), LegVerdict.Win(LegBasis.Time)))

            assertTrue(stateAt(legs, IntroMs + runMs - 10).myTickMs < winnerMs)
            assertEquals(winnerMs, stateAt(legs, IntroMs + runMs).myTickMs)
            assertTrue(stateAt(legs, IntroMs + 2_390).theirTickMs < loserMs)
            assertEquals(loserMs, stateAt(legs, IntroMs + 2_400).theirTickMs)
        }
    }

    @Test
    fun scoreIncrementsOnlyAtTheLock() {
        val run = steps(RevealMachine(mixedLegs, reduced = false))

        run.zipWithNext().forEach { (before, after) ->
            val gained = (after.myScore - before.myScore) + (after.theirScore - before.theirScore)
            val locking = before.phase == after.phase && before.locked == null && after.locked != null
            assertEquals("$before -> $after", if (locking) 1 else 0, gained)
            if (locking) {
                assertEquals(after.locked == Side.Mine, after.myScore > before.myScore)
            }
        }
    }

    @Test
    fun finalScoresMatchTheLegVerdicts() {
        val run = steps(RevealMachine(mixedLegs, reduced = false))

        // Three wins (time, hints, no result from them), two losses; the draw and the void count for nobody.
        listOf(RevealPhase.Score, RevealPhase.End).forEach { phase ->
            val state = run.first { it.phase == phase }
            assertEquals(3 to 2, state.myScore to state.theirScore)
        }
    }

    @Test
    fun tieAndVoidLegsChangeNoScore() {
        val legs = listOf(
            leg(time(42_300), time(42_800), LegVerdict.Draw),
            leg(time(30_000), time(60_000), LegVerdict.Void),
        )
        val run = steps(RevealMachine(legs, reduced = false))

        assertTrue(run.all { it.locked == null && it.myScore == 0 && it.theirScore == 0 })
        assertEquals(RevealPhase.End, run.last().phase)
    }

    @Test
    fun aSideWithNoResultTicksForEighteenHundredMsAndLosesTheLeg() {
        val absent = listOf(DuelSideState.NotPlayed, DuelSideState.GaveUp, DuelSideState.Sealed)
        for (none in absent) {
            val cases = listOf(
                listOf(leg(time(40_000), none, LegVerdict.Win(LegBasis.Time))) to Side.Mine,
                listOf(leg(none, time(40_000), LegVerdict.Loss(LegBasis.Time))) to Side.Theirs,
            )
            for ((legs, winner) in cases) {
                val tick = { state: RevealState -> if (winner == Side.Mine) state.myTickMs else state.theirTickMs }
                val silent = { state: RevealState -> if (winner == Side.Mine) state.theirTickMs else state.myTickMs }
                val race = steps(RevealMachine(legs, reduced = false)).filter { it.phase == RevealPhase.Leg(0) }

                assertTrue("$none: no ticker for the side without a result", race.all { silent(it) == 0L })
                assertTrue(tick(stateAt(legs, IntroMs + 1_790)) < 40_000)
                assertNull(stateAt(legs, IntroMs + 1_790).locked)
                val locked = stateAt(legs, IntroMs + 1_800)
                assertEquals(40_000L, tick(locked))
                assertEquals(winner, locked.locked)
                assertEquals(if (winner == Side.Mine) 1 to 0 else 0 to 1, locked.myScore to locked.theirScore)
            }
        }
    }

    @Test
    fun tapSpeedsUpFourTimesAndStaysSticky() {
        val plain = RevealMachine(mixedLegs, reduced = false)
        val once = RevealMachine(mixedLegs, reduced = false).apply { tap() }
        val twice = RevealMachine(mixedLegs, reduced = false)
        twice.tap()
        twice.advance(Step)
        twice.tap()

        assertEquals(1, plain.state.value.speed)
        assertEquals(DuelSpeedUp, once.state.value.speed)
        // States include the starting one; the advances to End are one fewer.
        val plainAdvances = steps(plain).size - 1
        val onceAdvances = steps(once).size - 1
        assertEquals(plainAdvances.toDouble() / DuelSpeedUp, onceAdvances.toDouble(), 1.0)
        assertEquals(onceAdvances, steps(twice).size)
        assertTrue(steps(RevealMachine(mixedLegs, reduced = false).apply { tap() }).all { it.speed == DuelSpeedUp })
    }

    @Test
    fun skipJumpsToEndWithFinalScores() {
        val fromIntro = RevealMachine(mixedLegs, reduced = false).apply { advance(100) }
        val midPuzzle = RevealMachine(mixedLegs, reduced = false).apply { advance(IntroMs + LegMs + 500) }
        val fromScore = RevealMachine(mixedLegs, reduced = false)
        while (fromScore.state.value.phase != RevealPhase.Score) fromScore.advance(Step)

        for (machine in listOf(fromIntro, midPuzzle, fromScore)) {
            machine.skip()
            val end = machine.state.value
            assertEquals(RevealPhase.End, end.phase)
            assertEquals(3 to 2, end.myScore to end.theirScore)
            machine.advance(LegMs)
            assertEquals(end, machine.state.value)
        }
    }

    @Test
    fun reducedMotionStartsAtEndWithFinalScoresAndIgnoresAdvanceAndTap() {
        val machine = RevealMachine(mixedLegs, reduced = true)
        val start = machine.state.value

        assertEquals(RevealPhase.End, start.phase)
        assertEquals(3 to 2, start.myScore to start.theirScore)
        machine.advance(IntroMs + LegMs)
        machine.tap()
        assertEquals(start, machine.state.value)
    }

    @Test
    fun noTicksOrScoreChangesAfterEnd() {
        val machine = RevealMachine(mixedLegs, reduced = false)
        val end = steps(machine).last()

        assertEquals(RevealPhase.End, end.phase)
        repeat(50) { machine.advance(Step) }
        machine.advance(Long.MAX_VALUE)
        assertEquals(end, machine.state.value)
        assertEquals(0L to 0L, end.myTickMs to end.theirTickMs)
    }

    @Test
    fun anEmptyLegListGoesIntroThenScoreThenEnd() {
        val run = steps(RevealMachine(emptyList(), reduced = false))

        assertEquals(listOf(RevealPhase.VsIntro, RevealPhase.Score, RevealPhase.End), run.map { it.phase }.distinctConsecutive())
        assertEquals(0 to 0, run.last().myScore to run.last().theirScore)
    }

    @Test
    fun aMatchWithoutAVerdictPlaysEveryPuzzleHoldsScoreThenEnds() {
        // A cancelled, declined or unclaimed match: every puzzle is void, and the run still ends like any other.
        val legs = mixedLegs.map { it.copy(verdict = LegVerdict.Void) }
        val endMs = 600L + 3_000L * legs.size + 900L
        val run = steps(RevealMachine(legs, reduced = false))

        assertEquals(legs.indices.map { RevealPhase.Leg(it) }, run.map { it.phase }.filterIsInstance<RevealPhase.Leg>().distinct())
        assertEquals(RevealPhase.Score, stateAt(legs, endMs - 1).phase)
        val end = stateAt(legs, endMs)
        assertEquals(RevealPhase.End, end.phase)
        assertEquals(0 to 0, end.myScore to end.theirScore)
    }

    @Test
    fun scoreLastsNineHundredMsThenEnd() {
        val scoreStartMs = IntroMs + LegMs * mixedLegs.size

        assertEquals(RevealPhase.Score, stateAt(mixedLegs, scoreStartMs).phase)
        assertEquals(RevealPhase.Score, stateAt(mixedLegs, scoreStartMs + 899).phase)
        assertEquals(RevealPhase.End, stateAt(mixedLegs, scoreStartMs + 900).phase)

        // After a tap the same hold takes a quarter of the real time: 225 ms.
        val tapped = RevealMachine(mixedLegs, reduced = false).apply { tap() }
        tapped.advance(scoreStartMs / DuelSpeedUp)
        assertEquals(RevealPhase.Score, tapped.state.value.phase)
        tapped.advance(224)
        assertEquals(RevealPhase.Score, tapped.state.value.phase)
        tapped.advance(1)
        assertEquals(RevealPhase.End, tapped.state.value.phase)
    }

    private val DuelSideState.ms: Long get() = (this as DuelSideState.Time).timeMs

    private fun leg(mine: DuelSideState, theirs: DuelSideState, verdict: LegVerdict) = LegReveal(GameId("zip"), mine, theirs, verdict)

    private fun time(ms: Long) = DuelSideState.Time(ms, hints = 0)

    /** A best-of-7 mix: time win, loss, draw, hints win, void, loss, and a win over a side with no result. */
    private val mixedLegs = listOf(
        leg(time(30_000), time(48_000), LegVerdict.Win(LegBasis.Time)),
        leg(time(61_000), time(40_000), LegVerdict.Loss(LegBasis.Time)),
        leg(time(42_300), time(42_800), LegVerdict.Draw),
        leg(time(70_000), time(55_000), LegVerdict.Win(LegBasis.Hints)),
        leg(time(20_000), time(25_000), LegVerdict.Void),
        leg(time(90_000), time(80_000), LegVerdict.Loss(LegBasis.Time)),
        leg(time(33_000), DuelSideState.NotPlayed, LegVerdict.Win(LegBasis.Time)),
    )

    private fun stateAt(legs: List<LegReveal>, ms: Long): RevealState =
        RevealMachine(legs, reduced = false).apply { advance(ms) }.state.value

    /** The machine's states from now until `End`, one per [Step] of advance. */
    private fun steps(machine: RevealMachine): List<RevealState> {
        val states = mutableListOf(machine.state.value)
        while (states.last().phase != RevealPhase.End) {
            check(states.size < MaxSteps) { "the reveal never reached End" }
            machine.advance(Step)
            states += machine.state.value
        }
        return states
    }

    private fun <T> List<T>.distinctConsecutive(): List<T> = filterIndexed { i, value -> i == 0 || this[i - 1] != value }

    private companion object {
        const val Step = 10L
        const val MaxSteps = 100_000
    }
}
