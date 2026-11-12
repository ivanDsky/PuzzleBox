package com.ivandsky.puzzlebox.gameui.replay

import com.ivandsky.puzzlebox.game.GameSession
import com.ivandsky.puzzlebox.game.replay.Lead
import com.ivandsky.puzzlebox.game.replay.ReplayFacts
import com.ivandsky.puzzlebox.game.replay.ReplayFrame
import com.ivandsky.puzzlebox.game.replay.ReplayOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Spec S8: the words of the compare's lead line. */
class ReplayLeadLineTest {

    private val names = CompareNames(mine = "Maya", theirs = "Sam")
    private val session = object : GameSession {}

    private fun frame(hints: Int, correct: Int, solved: Boolean = false) =
        ReplayFrame(session, null, ReplayFacts(hints, correct, total = 8, solved = solved), atMs = 0, sinceMoveMs = 0, ended = false)

    private fun text(
        positionMs: Long,
        lead: Lead?,
        mine: ReplayFrame? = frame(0, 5),
        theirs: ReplayFrame? = frame(0, 4),
        mineOutcome: ReplayOutcome = ReplayOutcome.Solved,
        theirsOutcome: ReplayOutcome = ReplayOutcome.Solved,
        mineEndMs: Long = 130_000,
        theirsEndMs: Long = 161_000,
    ): String? {
        val state = ReplayPlaybackState(
            positionMs = positionMs,
            durationMs = maxOf(mineEndMs, theirsEndMs),
            playing = false,
            speed = 1,
            skipIdle = false,
            ticksMs = LongArray(0),
            mineFrame = mine,
            theirsFrame = theirs,
            lead = lead,
        )
        return leadLineText(state, mineOutcome, theirsOutcome, mineEndMs, theirsEndMs, names, "queens")
    }

    @Test
    fun `no lead is no line`() {
        assertNull(text(10_000, lead = null))
        assertNull(text(10_000, lead = Lead.Mine, theirs = null))
    }

    @Test
    fun `ahead on pieces names the leader and counts the leader first`() {
        assertEquals("Maya ahead · 5 vs 4 queens", text(10_000, Lead.Mine))
        assertEquals("Sam ahead · 5 vs 4 queens", text(10_000, Lead.Theirs, mine = frame(0, 4), theirs = frame(0, 5)))
    }

    @Test
    fun `ahead on hints wins over more pieces`() {
        assertEquals("Maya ahead · 1 hint vs 2 hints", text(10_000, Lead.Mine, mine = frame(1, 3), theirs = frame(2, 6)))
        assertEquals("Sam ahead · 0 hints vs 1 hint", text(10_000, Lead.Theirs, mine = frame(1, 5), theirs = frame(0, 4)))
    }

    @Test
    fun `a hint taken mid-way hands the lead over`() {
        assertEquals("Maya ahead · 5 vs 4 queens", text(10_000, Lead.Mine, mine = frame(0, 5), theirs = frame(0, 4)))
        assertEquals("Sam ahead · 0 hints vs 1 hint", text(20_000, Lead.Theirs, mine = frame(1, 6), theirs = frame(0, 4)))
    }

    @Test
    fun `level while both play`() {
        assertEquals("Level", text(10_000, Lead.Level, mine = frame(0, 4), theirs = frame(0, 4)))
    }

    @Test
    fun `a side that gave up says when, and the other side says where it stands`() {
        assertEquals(
            "Maya gave up at 2:25 · Sam at 6 queens",
            text(
                150_000, Lead.Theirs, mine = frame(0, 3), theirs = frame(0, 6),
                mineOutcome = ReplayOutcome.GaveUp, mineEndMs = 145_000, theirsEndMs = 200_000,
            ),
        )
        assertEquals(
            "Sam gave up at 2:25 · Maya at 6 queens",
            text(
                150_000, Lead.Mine, mine = frame(0, 6), theirs = frame(0, 3),
                theirsOutcome = ReplayOutcome.GaveUp, mineEndMs = 200_000, theirsEndMs = 145_000,
            ),
        )
    }

    @Test
    fun `a side that gave up later than the position still plays`() {
        assertEquals(
            "Maya ahead · 5 vs 4 queens",
            text(100_000, Lead.Mine, mineOutcome = ReplayOutcome.GaveUp, mineEndMs = 145_000),
        )
    }

    @Test
    fun `both ended states the outcome`() {
        val done = 200_000L
        assertEquals(
            "Maya won · 2:10 vs 2:41",
            text(done, Lead.Mine, frame(0, 8, true), frame(0, 8, true)),
        )
        assertEquals(
            "Sam won · 2:10 vs 2:41",
            text(done, Lead.Theirs, frame(0, 8, true), frame(0, 8, true), mineEndMs = 161_000, theirsEndMs = 130_000),
        )
        assertEquals("Level", text(done, Lead.Level, frame(0, 8, true), frame(0, 8, true), mineEndMs = 130_000, theirsEndMs = 130_000))
    }

    @Test
    fun `both ended with a winner over a side that gave up`() {
        assertEquals(
            "Maya won · Sam gave up at 2:41",
            text(
                200_000, Lead.Mine, frame(0, 8, true), frame(0, 6),
                theirsOutcome = ReplayOutcome.GaveUp, theirsEndMs = 161_000,
            ),
        )
    }

    @Test
    fun `both ended without a solve still separates the sides`() {
        val gaveUp = ReplayOutcome.GaveUp
        assertEquals(
            "Maya ahead · 5 vs 4 queens",
            text(200_000, Lead.Mine, mineOutcome = gaveUp, theirsOutcome = gaveUp),
        )
        assertEquals(
            "Maya gave up at 2:10 · Sam gave up at 2:41",
            text(
                200_000, Lead.Mine, frame(0, 4), frame(0, 4),
                mineOutcome = gaveUp, theirsOutcome = gaveUp,
            ),
        )
    }

    @Test
    fun `two ended sides the rule separates never read Level`() {
        val line = text(200_000, Lead.Theirs, frame(0, 8, true), frame(0, 8, true), mineEndMs = 161_000, theirsEndMs = 130_000)
        assertEquals(false, line == "Level")
    }

    @Test
    fun `a screen reader hears the line up to its figures`() {
        assertEquals("Maya ahead", leadAnnouncement("Maya ahead · 5 vs 4 queens"))
        assertEquals("Level", leadAnnouncement("Level"))
    }
}
