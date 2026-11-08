package com.ivandsky.puzzlebox.profile.board

import com.ivandsky.puzzlebox.model.GameId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The selection rule (A2, A10): pick, else latest solved today, else first game; undecided until the default is known. */
class ProfileBoardSelectionTest {
    private val zip = GameId("zip")
    private val tango = GameId("tango")
    private val strands = GameId("strands")
    private val games = listOf(zip, tango, strands)

    @Test
    fun `an explicit pick wins over the default, even before completions emit`() {
        assertEquals(strands, selectedBoardGame(strands, LatestSolved.Known(tango), games))
        assertEquals(strands, selectedBoardGame(strands, LatestSolved.Pending, games))
    }

    @Test
    fun `no pick follows the latest solved today`() {
        assertEquals(tango, selectedBoardGame(null, LatestSolved.Known(tango), games))
    }

    @Test
    fun `no pick and nothing solved falls back to the first game`() {
        assertEquals(zip, selectedBoardGame(null, LatestSolved.Known(null), games))
    }

    @Test
    fun `no pick is undecided until the first completions emission`() {
        assertNull(selectedBoardGame(null, LatestSolved.Pending, games))
    }

    @Test
    fun `a pick or default that is not a registered game is ignored`() {
        assertEquals(tango, selectedBoardGame(GameId("gone"), LatestSolved.Known(tango), games))
        assertEquals(zip, selectedBoardGame(null, LatestSolved.Known(GameId("gone")), games))
        assertNull(selectedBoardGame(null, LatestSolved.Known(null), emptyList()))
    }
}
