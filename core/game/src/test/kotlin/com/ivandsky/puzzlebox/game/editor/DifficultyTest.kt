package com.ivandsky.puzzlebox.game.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A difficulty is a level, a score and, where the game has a ladder, its hardest rung (90). */
class DifficultyTest {

    private object Pairs : LadderRung {
        override val id = "pairs"
        override val level = Difficulty.Level.Hard
        override val captionKey = "pairs"
    }

    @Test
    fun `a legacy difficulty takes its level from the rung and has no score`() {
        val legacy = Difficulty(Pairs)

        assertEquals(Difficulty.Level.Hard, legacy.level)
        assertNull(legacy.score)
        assertEquals(Pairs, legacy.hardestRung)
        assertEquals("two legacy labels of one rung are the same value", Difficulty(Pairs), legacy)
    }

    @Test
    fun `a scored difficulty carries its own level, whatever the rung says, and needs no rung`() {
        val scored = Difficulty(Difficulty.Level.Easy, score = 12, hardestRung = Pairs)
        val rungless = Difficulty(Difficulty.Level.Expert, score = 80, hardestRung = null)

        assertEquals(Difficulty.Level.Easy, scored.level)
        assertEquals(12, scored.score)
        assertEquals(Difficulty.Level.Expert, rungless.level)
        assertNull(rungless.hardestRung)
    }
}
