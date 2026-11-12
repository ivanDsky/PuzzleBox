package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.domain.SolveReplayRef
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.play.api.ReplayKey
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.replay.ReplayFixtures
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The key's ids name a typed solve through the plugin, or nothing: a bad id is an empty page, never a throw. */
class ReplayRefsTest {

    private val games = replayCapableGames()
    private val game = ReplayFixtures.GAME.value
    private val day = LocalDate.parse("2026-11-04")

    @Test
    fun `a daily key names the record or the latest solve of that puzzle`() {
        val puzzle = TestGames.Ref(ReplayFixtures.GAME, PuzzleKey.Daily(day, 7))

        val record = ReplayKey.daily(game, day.toString(), 7, latest = false).toSolveReplayRef(games)
        val latest = ReplayKey.daily(game, day.toString(), 7, latest = true).toSolveReplayRef(games)

        assertEquals(puzzle, (record as SolveReplayRef.Record).puzzle)
        assertEquals(puzzle, (latest as SolveReplayRef.Latest).puzzle)
    }

    @Test
    fun `a custom key names the solve of the friend's puzzle`() {
        val puzzle = TestGames.Ref(ReplayFixtures.GAME, PuzzleKey.Custom("abc"))

        val latest = ReplayKey.custom(game, "abc", latest = true).toSolveReplayRef(games)

        assertEquals(puzzle, (latest as SolveReplayRef.Latest).puzzle)
    }

    @Test
    fun `a leg key names my leg or theirs`() {
        val mine = ReplayKey.leg(game, "M8T3VQ", legIndex = 1).toSolveReplayRef(games)
        val theirs = ReplayKey.leg(game, "M8T3VQ", legIndex = 1, theirs = true).toSolveReplayRef(games)

        assertEquals("M8T3VQ", (mine as SolveReplayRef.MyLeg).code.value)
        assertEquals(1, mine.legIndex)
        assertTrue(theirs is SolveReplayRef.TheirLeg)
    }

    @Test
    fun `ids that name nothing give null`() {
        assertNull(ReplayKey.daily("nope", day.toString(), 7, latest = true).toSolveReplayRef(games))
        assertNull(ReplayKey.daily(game, "not-a-day", 7, latest = true).toSolveReplayRef(games))
        assertNull(ReplayKey.leg(game, "bad", legIndex = 0).toSolveReplayRef(games))
    }
}
