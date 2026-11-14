package com.ivandsky.puzzlebox.gameui.replay

import com.ivandsky.puzzlebox.game.replay.Lead
import com.ivandsky.puzzlebox.game.replay.MoveLog
import com.ivandsky.puzzlebox.game.replay.ReplayMove
import com.ivandsky.puzzlebox.game.replay.ReplayOutcome
import com.ivandsky.puzzlebox.game.replay.Timeline
import com.ivandsky.puzzlebox.testing.replay.ReplayFixtures
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Spec D4, S7, S8: the headless replay player, driven by a manual frame time. */
class ReplayPlaybackTest {

    /** 12 moves, one a second; the last one solves it at 12 000. */
    private val solve = ReplayFixtures.timeline(ReplayFixtures.SHORT_MOVES)

    /** 12 moves, two a second; the last one solves it at 6 000. */
    private val fastSolve = ReplayFixtures.timeline(ReplayFixtures.SHORT_MOVES, stepMs = 500)

    /** 12 of 13 pieces, two a second; given up at 6 000. */
    private val fastGiveUp = ReplayFixtures.timeline(ReplayFixtures.SHORT_MOVES, stepMs = 500, outcome = ReplayOutcome.GaveUp)

    /** Moves at 20 000, 40 000 and 60 000: every gap is idle. */
    private val slowSolve = ReplayFixtures.timeline(moves = 3, stepMs = 20_000)

    @Test
    fun `opens paused at the end on the finished board`() {
        val state = ReplayPlayback(solve, theirs = null).state.value

        assertEquals(12_000L, state.positionMs)
        assertEquals(12_000L, state.durationMs)
        assertFalse(state.playing)
        assertEquals(1, state.speed)
        assertTrue(state.mineFrame!!.ended)
        assertTrue(state.mineFrame!!.facts.solved)
    }

    @Test
    fun `the first state has skip idle on`() {
        assertTrue(ReplayPlayback(solve, theirs = null).state.value.skipIdle)
    }

    @Test
    fun `the duration is the end of the longer side`() {
        assertEquals(12_000L, ReplayPlayback(solve, fastSolve).state.value.durationMs)
        assertEquals(12_000L, ReplayPlayback(fastSolve, solve).state.value.durationMs)
    }

    @Test
    fun `the shorter side freezes at its own end`() {
        val playback = ReplayPlayback(solve, fastSolve)

        playback.seekTo(9_000)

        val state = playback.state.value
        assertEquals(9_000L, state.mineFrame!!.atMs)
        assertFalse(state.mineFrame!!.ended)
        assertEquals(6_000L, state.theirsFrame!!.atMs)
        assertTrue(state.theirsFrame!!.ended)
    }

    @Test
    fun `in a compare the ticks are my move stamps`() {
        assertArrayEquals(solve.moveTimesMs, ReplayPlayback(solve, fastSolve).state.value.ticksMs)
    }

    @Test
    fun `with only their replay the ticks are theirs`() {
        assertArrayEquals(fastSolve.moveTimesMs, ReplayPlayback(mine = null, fastSolve).state.value.ticksMs)
    }

    @Test
    fun `with both replays each side has a frame`() {
        val state = ReplayPlayback(solve, fastSolve).state.value

        assertEquals(12, state.mineFrame!!.facts.correct)
        assertEquals(12, state.theirsFrame!!.facts.correct)
    }

    @Test
    fun `with only my replay their side has no frame and no lead`() {
        val state = ReplayPlayback(solve, theirs = null, finalLead = Lead.Mine).state.value

        assertEquals(12, state.mineFrame!!.facts.correct)
        assertNull(state.theirsFrame)
        assertNull(state.lead)
    }

    @Test
    fun `with only their replay my side has no frame and no lead`() {
        val state = ReplayPlayback(mine = null, fastSolve, finalLead = Lead.Theirs).state.value

        assertNull(state.mineFrame)
        assertEquals(12, state.theirsFrame!!.facts.correct)
        assertEquals(6_000L, state.durationMs)
        assertNull(state.lead)
    }

    @Test
    fun `with no replay the state is empty`() {
        val state = ReplayPlayback(mine = null, theirs = null, finalLead = Lead.Mine).state.value

        assertEquals(0L, state.positionMs)
        assertEquals(0L, state.durationMs)
        assertFalse(state.playing)
        assertEquals(1, state.speed)
        assertTrue(state.skipIdle)
        assertEquals(0, state.ticksMs.size)
        assertNull(state.mineFrame)
        assertNull(state.theirsFrame)
        assertNull(state.lead)
    }

    @Test
    fun `with no replay every call changes nothing`() {
        val playback = ReplayPlayback(mine = null, theirs = null)
        val opened = playback.state.value

        playback.play()
        playback.advance(16)
        playback.setSpeed(8)
        playback.setSkipIdle(false)
        playback.nextMove()
        playback.previousMove()
        playback.seekTo(500)
        playback.pause()

        assertSame(opened, playback.state.value)
    }

    @Test
    fun `play at the end restarts from zero`() {
        val playback = ReplayPlayback(solve, theirs = null)

        playback.play()

        val state = playback.state.value
        assertEquals(0L, state.positionMs)
        assertTrue(state.playing)
        assertEquals(0, state.mineFrame!!.facts.correct)
    }

    @Test
    fun `advance moves the position by the frame time`() {
        val playback = ReplayPlayback(solve, theirs = null)
        playback.play()

        playback.advance(16)
        playback.advance(16)

        assertEquals(32L, playback.state.value.positionMs)
    }

    @Test
    fun `advance shows the board at the new position`() {
        val playback = ReplayPlayback(solve, fastSolve)
        playback.play()

        playback.advance(2_500)

        assertEquals(2, playback.state.value.mineFrame!!.facts.correct)
        assertEquals(5, playback.state.value.theirsFrame!!.facts.correct)
    }

    @Test
    fun `advance while paused changes nothing`() {
        val playback = ReplayPlayback(solve, theirs = null)
        playback.seekTo(3_000)
        val paused = playback.state.value

        playback.advance(500)

        assertSame(paused, playback.state.value)
    }

    @Test
    fun `pause stops the clock where it is`() {
        val playback = ReplayPlayback(solve, theirs = null)
        playback.play()
        playback.advance(100)

        playback.pause()
        playback.advance(100)

        assertEquals(100L, playback.state.value.positionMs)
        assertFalse(playback.state.value.playing)
    }

    @Test
    fun `play before the end resumes from the same position`() {
        val playback = ReplayPlayback(solve, theirs = null)
        playback.seekTo(3_000)

        playback.play()

        assertEquals(3_000L, playback.state.value.positionMs)
        assertTrue(playback.state.value.playing)
    }

    @Test
    fun `reaching the end pauses there`() {
        val playback = ReplayPlayback(solve, theirs = null)
        playback.seekTo(11_900)
        playback.play()

        playback.advance(500)

        val state = playback.state.value
        assertEquals(12_000L, state.positionMs)
        assertFalse(state.playing)
        assertTrue(state.mineFrame!!.ended)
    }

    @Test
    fun `play while playing changes nothing`() {
        val playback = ReplayPlayback(solve, theirs = null)
        playback.play()
        playback.advance(100)
        val playing = playback.state.value

        playback.play()

        assertSame(playing, playback.state.value)
    }

    @Test
    fun `a negative frame time does not move the position below zero`() {
        val playback = ReplayPlayback(solve, theirs = null)
        playback.play()
        playback.advance(20)

        playback.advance(-50)

        assertEquals(0L, playback.state.value.positionMs)
        assertTrue(playback.state.value.playing)
    }

    @Test
    fun `a call that changes nothing keeps the same state object`() {
        val playback = ReplayPlayback(solve, theirs = null)
        val opened = playback.state.value

        playback.pause()
        playback.setSpeed(1)
        playback.setSkipIdle(true)
        playback.seekTo(12_000)

        assertSame(opened, playback.state.value)
    }

    @Test
    fun `the speed multiplies the frame time`() {
        val playback = ReplayPlayback(solve, theirs = null)
        playback.setSpeed(4)
        playback.play()

        playback.advance(100)

        assertEquals(400L, playback.state.value.positionMs)
    }

    @Test
    fun `a speed change mid-play keeps playing and applies from the next frame`() {
        val playback = ReplayPlayback(solve, theirs = null)
        playback.play()
        playback.advance(100)

        playback.setSpeed(8)

        assertEquals(100L, playback.state.value.positionMs)
        assertTrue(playback.state.value.playing)
        playback.advance(100)
        assertEquals(900L, playback.state.value.positionMs)
    }

    @Test
    fun `the speeds 1, 2, 4 and 8 are accepted`() {
        val playback = ReplayPlayback(solve, theirs = null)

        for (speed in intArrayOf(2, 4, 8, 1)) {
            playback.setSpeed(speed)
            assertEquals(speed, playback.state.value.speed)
        }
    }

    @Test
    fun `any other speed is ignored`() {
        val playback = ReplayPlayback(solve, theirs = null)
        playback.setSpeed(2)
        val atDouble = playback.state.value

        for (speed in intArrayOf(0, 3, 16, -2)) playback.setSpeed(speed)

        assertSame(atDouble, playback.state.value)
    }

    @Test
    fun `at 8x the playback reaches the end and pauses`() {
        val playback = ReplayPlayback(solve, theirs = null)
        playback.setSpeed(8)
        playback.play()

        // 16 ms frames cover 128 ms each: 93 of them stop at 11 904, the 94th passes 12 000.
        repeat(93) { playback.advance(16) }
        assertTrue(playback.state.value.playing)
        playback.advance(16)

        assertEquals(12_000L, playback.state.value.positionMs)
        assertFalse(playback.state.value.playing)
    }

    @Test
    fun `seek shows the board at that time`() {
        val playback = ReplayPlayback(solve, theirs = null)

        playback.seekTo(3_500)

        val state = playback.state.value
        assertEquals(3_500L, state.positionMs)
        assertEquals(3, state.mineFrame!!.facts.correct)
    }

    @Test
    fun `seek clamps into the replay`() {
        val playback = ReplayPlayback(solve, theirs = null)

        playback.seekTo(-5)
        assertEquals(0L, playback.state.value.positionMs)

        playback.seekTo(99_999)
        assertEquals(12_000L, playback.state.value.positionMs)
    }

    @Test
    fun `seek while paused stays paused`() {
        val playback = ReplayPlayback(solve, theirs = null)

        playback.seekTo(4_000)

        assertFalse(playback.state.value.playing)
    }

    @Test
    fun `seek while playing keeps playing`() {
        val playback = ReplayPlayback(solve, theirs = null)
        playback.play()

        playback.seekTo(4_000)

        assertTrue(playback.state.value.playing)
    }

    @Test
    fun `a seek to the end while playing pauses there`() {
        val playback = ReplayPlayback(solve, theirs = null)
        playback.play()

        playback.seekTo(12_000)

        assertEquals(12_000L, playback.state.value.positionMs)
        assertFalse(playback.state.value.playing)
    }

    @Test
    fun `play after a seek to the end restarts from zero`() {
        val playback = ReplayPlayback(solve, theirs = null)
        playback.play()
        playback.seekTo(99_999)

        playback.play()

        assertEquals(0L, playback.state.value.positionMs)
        assertTrue(playback.state.value.playing)
    }

    @Test
    fun `next move goes to the next tick and pauses`() {
        val playback = ReplayPlayback(solve, theirs = null)
        playback.seekTo(2_500)
        playback.play()

        playback.nextMove()

        assertEquals(3_000L, playback.state.value.positionMs)
        assertFalse(playback.state.value.playing)
    }

    @Test
    fun `next move at an exact tick goes to the following one`() {
        val playback = ReplayPlayback(solve, theirs = null)
        playback.seekTo(3_000)

        playback.nextMove()

        assertEquals(4_000L, playback.state.value.positionMs)
    }

    @Test
    fun `next move past the last tick goes to the end`() {
        val playback = ReplayPlayback(gaveUp(endMs = 9_000, 1_000, 2_000), theirs = null)
        playback.seekTo(2_000)

        playback.nextMove()

        assertEquals(9_000L, playback.state.value.positionMs)
    }

    @Test
    fun `previous move goes to the tick before and pauses`() {
        val playback = ReplayPlayback(solve, theirs = null)
        playback.seekTo(2_500)
        playback.play()

        playback.previousMove()

        assertEquals(2_000L, playback.state.value.positionMs)
        assertFalse(playback.state.value.playing)
    }

    @Test
    fun `previous move at an exact tick goes to the one before`() {
        val playback = ReplayPlayback(solve, theirs = null)
        playback.seekTo(3_000)

        playback.previousMove()

        assertEquals(2_000L, playback.state.value.positionMs)
    }

    @Test
    fun `previous move before the first tick goes to zero`() {
        val playback = ReplayPlayback(solve, theirs = null)
        playback.seekTo(1_000)

        playback.previousMove()

        assertEquals(0L, playback.state.value.positionMs)
    }

    @Test
    fun `in a compare the steps follow my moves, not theirs`() {
        val playback = ReplayPlayback(solve, fastSolve)
        playback.seekTo(1_000)

        playback.nextMove()
        assertEquals(2_000L, playback.state.value.positionMs)

        playback.previousMove()
        assertEquals(1_000L, playback.state.value.positionMs)
    }

    @Test
    fun `a replay with no moves steps between zero and the end`() {
        val playback = ReplayPlayback(gaveUp(endMs = 5_000), theirs = null)

        playback.previousMove()
        assertEquals(0L, playback.state.value.positionMs)

        playback.nextMove()
        assertEquals(5_000L, playback.state.value.positionMs)
    }

    @Test
    fun `skip idle jumps a 20 s gap to one second before the next move`() {
        val playback = ReplayPlayback(slowSolve, theirs = null)
        playback.play()

        playback.advance(16)

        assertEquals(19_000L, playback.state.value.positionMs)
        assertTrue(playback.state.value.playing)
    }

    @Test
    fun `after a jump the lead-in plays at frame time and the next gap jumps again`() {
        val playback = ReplayPlayback(slowSolve, theirs = null)
        playback.play()
        playback.advance(16)

        playback.advance(500)
        assertEquals(19_500L, playback.state.value.positionMs)

        playback.advance(500)
        assertEquals(20_000L, playback.state.value.positionMs)

        playback.advance(4_700)
        assertEquals(39_000L, playback.state.value.positionMs)
    }

    @Test
    fun `a gap after a move is not jumped before the move's animation has played`() {
        val playback = ReplayPlayback(slowSolve, theirs = null)
        playback.seekTo(20_000)
        playback.play()

        // The dwell is 4 700 ms: a 24-letter theme word, the longest a published Strands word may be (F23-R1).
        playback.advance(4_600)
        assertEquals(24_600L, playback.state.value.positionMs)

        playback.advance(100)
        assertEquals(39_000L, playback.state.value.positionMs)
    }

    @Test
    fun `the dwell is wall time, so it is longer in play time at speed 2`() {
        val playback = ReplayPlayback(slowSolve, theirs = null)
        playback.setSpeed(2)
        playback.seekTo(20_000)
        playback.play()

        playback.advance(2_900)
        assertEquals(25_800L, playback.state.value.positionMs)

        playback.seekTo(20_000)
        playback.advance(4_700)
        assertEquals(39_000L, playback.state.value.positionMs)
    }

    @Test
    fun `in a compare the other side's move restarts the dwell`() {
        val theirsAt22 = ReplayFixtures.timeline(moves = 1, stepMs = 22_000)
        val playback = ReplayPlayback(slowSolve, theirsAt22)
        playback.seekTo(26_500)
        playback.play()

        playback.advance(100)
        assertEquals(26_600L, playback.state.value.positionMs)

        playback.advance(100)
        assertEquals(39_000L, playback.state.value.positionMs)
    }

    @Test
    fun `with skip idle off the gap plays through`() {
        val playback = ReplayPlayback(slowSolve, theirs = null)
        playback.setSkipIdle(false)
        playback.play()

        playback.advance(16)

        assertFalse(playback.state.value.skipIdle)
        assertEquals(16L, playback.state.value.positionMs)
    }

    @Test
    fun `skip idle turned on mid-play jumps on the next frame`() {
        val playback = ReplayPlayback(slowSolve, theirs = null)
        playback.setSkipIdle(false)
        playback.play()
        playback.advance(16)

        playback.setSkipIdle(true)
        assertEquals(16L, playback.state.value.positionMs)

        playback.advance(16)
        assertEquals(19_000L, playback.state.value.positionMs)
    }

    @Test
    fun `idle is measured from the position, so a move exactly 6 s ahead plays through`() {
        val playback = ReplayPlayback(slowSolve, theirs = null)
        playback.seekTo(13_984)
        playback.play()

        playback.advance(16)

        assertEquals(14_000L, playback.state.value.positionMs)
    }

    @Test
    fun `a move 1 ms more than 6 s ahead is jumped to`() {
        val playback = ReplayPlayback(slowSolve, theirs = null)
        playback.seekTo(13_983)
        playback.play()

        playback.advance(16)

        assertEquals(19_000L, playback.state.value.positionMs)
    }

    @Test
    fun `skip idle waits for the nearer next move of their side`() {
        val theirsEveryFive = ReplayFixtures.timeline(moves = 4, stepMs = 5_000)
        val playback = ReplayPlayback(slowSolve, theirsEveryFive)
        playback.play()

        playback.advance(16)

        assertEquals(16L, playback.state.value.positionMs)
    }

    @Test
    fun `skip idle waits for the nearer next move of my side`() {
        val mineEveryFive = ReplayFixtures.timeline(moves = 4, stepMs = 5_000)
        val playback = ReplayPlayback(mineEveryFive, slowSolve)
        playback.play()

        playback.advance(16)

        assertEquals(16L, playback.state.value.positionMs)
    }

    @Test
    fun `skip idle jumps to their next move once mine are over`() {
        val mineOneMove = ReplayFixtures.timeline(moves = 1)
        val playback = ReplayPlayback(mineOneMove, slowSolve)
        playback.play()
        playback.seekTo(1_000)

        playback.advance(4_700)

        assertEquals(19_000L, playback.state.value.positionMs)
    }

    @Test
    fun `past the last move of both sides the tail plays to the end without a jump`() {
        val playback = ReplayPlayback(gaveUp(endMs = 30_000, 1_000, 2_000), gaveUp(endMs = 12_000, 1_000, 3_000))
        playback.seekTo(3_000)
        playback.play()

        playback.advance(1_000)
        assertEquals(4_000L, playback.state.value.positionMs)

        var frames = 1
        while (playback.state.value.playing && frames < 100) {
            playback.advance(1_000)
            frames++
        }
        assertEquals(27, frames)
        assertEquals(30_000L, playback.state.value.positionMs)
        assertFalse(playback.state.value.playing)
    }

    @Test
    fun `a seek while playing lands where asked, with skip idle on`() {
        val playback = ReplayPlayback(slowSolve, theirs = null)
        playback.play()

        playback.seekTo(5_000)

        assertEquals(5_000L, playback.state.value.positionMs)
    }

    @Test
    fun `during play the side with more correct pieces leads`() {
        val playback = ReplayPlayback(fastSolve, solve)

        playback.seekTo(0)
        assertEquals(Lead.Level, playback.state.value.lead)

        playback.seekTo(3_000)
        assertEquals(Lead.Mine, playback.state.value.lead)
    }

    @Test
    fun `a hint gives the lead away at its stamp`() {
        val mineWithHint = ReplayFixtures.timeline(ReplayFixtures.SHORT_MOVES, hintEvery = 4)
        val playback = ReplayPlayback(mineWithHint, solve)

        playback.seekTo(3_999)
        assertEquals(Lead.Level, playback.state.value.lead)

        playback.seekTo(4_000)
        assertEquals(Lead.Theirs, playback.state.value.lead)
    }

    @Test
    fun `a hint outweighs more correct pieces`() {
        val fastWithHint = ReplayFixtures.timeline(ReplayFixtures.SHORT_MOVES, stepMs = 500, hintEvery = 4)
        val playback = ReplayPlayback(fastWithHint, solve)

        playback.seekTo(1_500)
        assertEquals(Lead.Mine, playback.state.value.lead)

        playback.seekTo(2_000)
        assertEquals(Lead.Theirs, playback.state.value.lead)
    }

    @Test
    fun `a gave-up side leads on pieces until both ended, then the solved side leads`() {
        val playback = ReplayPlayback(solve, fastGiveUp)

        playback.seekTo(3_000)
        assertEquals(Lead.Theirs, playback.state.value.lead)

        playback.seekTo(6_000)
        assertTrue(playback.state.value.theirsFrame!!.ended)
        assertEquals(Lead.Theirs, playback.state.value.lead)

        playback.seekTo(11_999)
        assertEquals(Lead.Theirs, playback.state.value.lead)

        playback.seekTo(12_000)
        assertEquals(Lead.Mine, playback.state.value.lead)
    }

    @Test
    fun `once both ended level boards go to the faster side`() {
        assertEquals(Lead.Mine, ReplayPlayback(fastSolve, solve).state.value.lead)
        assertEquals(Lead.Theirs, ReplayPlayback(solve, fastSolve).state.value.lead)
    }

    @Test
    fun `the final lead replaces the computed one only once both ended`() {
        val playback = ReplayPlayback(solve, fastGiveUp, finalLead = Lead.Level)
        assertEquals(Lead.Level, playback.state.value.lead)

        playback.seekTo(6_000)
        assertEquals(Lead.Theirs, playback.state.value.lead)

        playback.seekTo(11_999)
        assertEquals(Lead.Theirs, playback.state.value.lead)

        playback.seekTo(12_000)
        assertEquals(Lead.Level, playback.state.value.lead)
    }

    @Test
    fun `a 4096-move timeline survives 10000 random seeks`() {
        val long = ReplayFixtures.timeline(ReplayFixtures.LONG_MOVES)
        val playback = ReplayPlayback(long, theirs = null)
        val random = Random(43)

        repeat(10_000) {
            val askedMs = random.nextLong(-5_000, long.endMs + 5_000)
            playback.seekTo(askedMs)

            val landedMs = askedMs.coerceIn(0, long.endMs)
            val state = playback.state.value
            assertEquals(landedMs, state.positionMs)
            assertEquals((landedMs / 1_000).toInt(), state.mineFrame!!.facts.correct)
        }
    }

    /**
     * A solve of place moves at [stampsMs], given up at [endMs]. The fixture builders end every
     * replay on its last move; the tail after the last move needs an end time of its own.
     */
    private fun gaveUp(endMs: Long, vararg stampsMs: Long): Timeline {
        val log = MoveLog.fresh(ReplayFixtures.GAME)
        val initial = ReplayFixtures.initial(total = stampsMs.size + 1)
        var session = initial
        for (atMs in stampsMs) session = log.play(ReplayFixtures.replay, session, PLACE, atMs)
        val bytes = checkNotNull(log.seal(ReplayOutcome.GaveUp, endMs))
        return checkNotNull(Timeline.open(ReplayFixtures.GAME, ReplayFixtures.replay, initial, bytes))
    }

    private companion object {
        /** Kind 0 is the fixture game's place move. */
        val PLACE = ReplayMove(kind = 0, args = IntArray(0))
    }
}
