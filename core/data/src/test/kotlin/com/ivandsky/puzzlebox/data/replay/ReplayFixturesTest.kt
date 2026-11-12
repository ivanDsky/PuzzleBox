package com.ivandsky.puzzlebox.data.replay

import com.ivandsky.puzzlebox.game.replay.ReplayOutcome
import com.ivandsky.puzzlebox.testing.replay.ReplayFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplayFixturesTest {

    @Test
    fun `the short fixture opens with one tick per move and ends solved`() {
        val timeline = ReplayFixtures.timeline(ReplayFixtures.SHORT_MOVES)

        assertEquals(ReplayFixtures.SHORT_MOVES, timeline.moveTimesMs.size)
        assertTrue(timeline.at(timeline.endMs).facts.solved)
    }

    @Test
    fun `the long fixture opens with one tick per move and ends solved`() {
        val timeline = ReplayFixtures.timeline(ReplayFixtures.LONG_MOVES)

        assertEquals(ReplayFixtures.LONG_MOVES, timeline.moveTimesMs.size)
        assertTrue(timeline.at(timeline.endMs).facts.solved)
    }

    @Test
    fun `hints are counted but add no tick of their own`() {
        val timeline = ReplayFixtures.timeline(ReplayFixtures.SHORT_MOVES, hintEvery = 3)

        assertEquals(ReplayFixtures.SHORT_MOVES, timeline.moveTimesMs.size)
        assertEquals(4, timeline.at(timeline.endMs).facts.hints)
    }

    @Test
    fun `a gave-up fixture opens and ends unsolved`() {
        val timeline = ReplayFixtures.timeline(ReplayFixtures.SHORT_MOVES, outcome = ReplayOutcome.GaveUp)

        assertEquals(ReplayOutcome.GaveUp, timeline.outcome)
        assertFalse(timeline.at(timeline.endMs).facts.solved)
    }
}
