package com.ivandsky.puzzlebox.game.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** One way to turn a score into a level, shared by every game's scored grader (90). */
class LevelBandsTest {

    private val bands = LevelBands(medium = 20, hard = 45, expert = 80)

    @Test
    fun `a level starts at its threshold and runs to the next one`() {
        val levels = listOf(0, 19, 20, 44, 45, 79, 80, 500).map(bands::levelOf)

        assertEquals(
            listOf(
                Difficulty.Level.Easy, Difficulty.Level.Easy,
                Difficulty.Level.Medium, Difficulty.Level.Medium,
                Difficulty.Level.Hard, Difficulty.Level.Hard,
                Difficulty.Level.Expert, Difficulty.Level.Expert,
            ),
            levels,
        )
    }

    @Test
    fun `a difficulty holds the score, its level and the rung handed in`() {
        assertEquals(Difficulty(Difficulty.Level.Hard, score = 50, hardestRung = null), bands.difficulty(50))
        assertEquals(
            Difficulty(Difficulty.Level.Easy, score = 3, hardestRung = FakeScoredGrader.LegacyRung),
            bands.difficulty(3, FakeScoredGrader.LegacyRung),
        )
    }

    @Test
    fun `thresholds out of order are refused`() {
        assertThrows(IllegalArgumentException::class.java) { LevelBands(medium = 45, hard = 45, expert = 80) }
        assertThrows(IllegalArgumentException::class.java) { LevelBands(medium = 20, hard = 90, expert = 80) }
    }
}
