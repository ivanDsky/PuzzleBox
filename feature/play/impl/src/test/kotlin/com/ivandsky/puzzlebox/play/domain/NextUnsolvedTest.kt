package com.ivandsky.puzzlebox.play.domain

import com.ivandsky.puzzlebox.domain.TodayEntry
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import com.ivandsky.puzzlebox.testing.game.TestGames.beta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NextUnsolvedTest {

    private val gamma = GameId("gamma")

    @Test
    fun `nextUnsolved wraps and excludes the current game`() {
        val set = listOf(
            TodayEntry(alpha, "Alpha", TodayStatus.Solved(1L)),
            TodayEntry(beta, "Beta", TodayStatus.InProgress(1L)),
            TodayEntry(gamma, "Gamma", TodayStatus.NotStarted),
        )

        assertEquals(beta, set.nextUnsolved(after = alpha))
        assertEquals(beta, set.nextUnsolved(after = gamma))
        assertEquals(gamma, set.nextUnsolved(after = beta))
    }

    @Test
    fun `nextUnsolved is null when every other game is solved`() {
        val set = listOf(
            TodayEntry(alpha, "Alpha", TodayStatus.Solved(1L)),
            TodayEntry(beta, "Beta", TodayStatus.Solved(1L)),
            TodayEntry(gamma, "Gamma", TodayStatus.NotStarted),
        )

        assertNull(set.nextUnsolved(after = gamma))
        assertNull(set.map { it.copy(status = TodayStatus.Solved(1L)) }.nextUnsolved(after = alpha))
    }
}
