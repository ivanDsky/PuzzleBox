package com.ivandsky.puzzlebox.game.editor

import org.junit.Assert.assertEquals
import org.junit.Test

/** The time Finish may take per target (90): 2 s as before, 4 s for the two levels that take more search. */
class FinishBudgetTest {

    @Test
    fun `no target, Easy and Medium keep two seconds`() {
        assertEquals(listOf(2_000L, 2_000L, 2_000L), listOf(null, Difficulty.Level.Easy, Difficulty.Level.Medium).map(FinishBudget::millisFor))
    }

    @Test
    fun `Hard and Expert get four seconds`() {
        assertEquals(listOf(4_000L, 4_000L), listOf(Difficulty.Level.Hard, Difficulty.Level.Expert).map(FinishBudget::millisFor))
    }
}
