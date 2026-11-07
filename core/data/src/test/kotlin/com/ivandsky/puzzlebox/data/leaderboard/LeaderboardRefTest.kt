package com.ivandsky.puzzlebox.data.leaderboard

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Only an official daily key maps to a daily board (spec A12); a custom key never does. */
class LeaderboardRefTest {

    @Test
    fun `a daily key maps to its game's board for that day`() {
        assertEquals(
            LeaderboardRef("queens", LocalDate.of(2026, 11, 6)),
            LeaderboardRef.fromPuzzleRefKey("queens/2026-11-06/918"),
        )
    }

    @Test
    fun `a custom key never maps to a daily board`() {
        assertNull(LeaderboardRef.fromPuzzleRefKey("custom/queens/K7Q2MX"))
    }
}
