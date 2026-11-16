package com.ivandsky.puzzlebox.create.editor

import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.game.editor.Difficulty.Level
import com.ivandsky.puzzlebox.game.editor.IncompleteReason
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.game.editor.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The level the author publishes at (96): the Studio proposes the verdict's level and the author's own pick, kept only
 * when it differs from the proposal, wins over it. A board with no proposal has no level to choose.
 */
class LevelChoiceTest {

    private fun unique(level: Level, needsGuessing: Boolean = false) =
        Verdict.Unique(Solution(listOf(1)), difficulty = Difficulty(level, score = 7, hardestRung = null), needsGuessing = needsGuessing)

    @Test
    fun `no pick follows the proposal`() {
        val choice = LevelChoice.of(unique(Level.Hard), pick = null)

        assertEquals(LevelChoice(proposed = Level.Hard, chosen = Level.Hard), choice)
        assertFalse(choice!!.changed)
    }

    @Test
    fun `an own pick wins over the proposal and shows as changed`() {
        val choice = LevelChoice.of(unique(Level.Hard), pick = Level.Easy)!!

        assertEquals(Level.Hard, choice.proposed)
        assertEquals(Level.Easy, choice.chosen)
        assertTrue(choice.changed)
    }

    @Test
    fun `a proposal that moves takes an unchanged choice along and leaves an own pick`() {
        assertEquals(Level.Expert, LevelChoice.of(unique(Level.Expert), pick = null)!!.chosen)
        assertEquals(Level.Easy, LevelChoice.of(unique(Level.Expert), pick = Level.Easy)!!.chosen)
        assertEquals(Level.Expert, LevelChoice.of(unique(Level.Expert), pick = Level.Easy)!!.proposed)
    }

    @Test
    fun `a board with no proposal has no choice`() {
        assertNull(LevelChoice.of(Verdict.Unique(Solution(listOf(1))), pick = Level.Easy))
        assertNull("a solve that needs guessing has no label", LevelChoice.of(unique(Level.Hard, needsGuessing = true), pick = Level.Easy))
        assertNull(LevelChoice.of(Verdict.Checking, pick = Level.Easy))
        assertNull(LevelChoice.of(Verdict.Incomplete(IncompleteReason("empty", 0)), pick = null))
    }

    @Test
    fun `picking the proposal drops the pick, any other level is kept`() {
        val choice = LevelChoice(proposed = Level.Hard, chosen = Level.Easy)

        assertNull(choice.pickFor(Level.Hard))
        assertEquals(Level.Medium, choice.pickFor(Level.Medium))
        assertEquals(Level.Easy, choice.pickFor(Level.Easy))
    }
}
