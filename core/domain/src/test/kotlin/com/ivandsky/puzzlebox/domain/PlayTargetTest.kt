package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import com.ivandsky.puzzlebox.testing.game.TestGames.beta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** "Play today"'s game (A13, A15, SC11): an unfinished save, else the first unsolved, else the first. */
class PlayTargetTest {

    private val gamma = GameId("gamma")

    private fun set(vararg statuses: Pair<GameId, TodayStatus>) =
        statuses.map { (game, status) -> TodayEntry(game, game.value, status) }

    @Test
    fun `an unfinished save wins over an earlier unsolved game`() {
        val target = set(
            alpha to TodayStatus.NotStarted,
            beta to TodayStatus.Solved(1L),
            gamma to TodayStatus.InProgress(1L),
        ).playTarget()

        assertEquals(gamma, target)
    }

    @Test
    fun `with no save the first unsolved game wins, offline included`() {
        val target = set(
            alpha to TodayStatus.Solved(1L),
            beta to TodayStatus.Offline,
            gamma to TodayStatus.NotStarted,
        ).playTarget()

        assertEquals(beta, target)
    }

    @Test
    fun `all solved falls back to the first game`() {
        val target = set(alpha to TodayStatus.Solved(1L), beta to TodayStatus.Solved(2L)).playTarget()

        assertEquals(alpha, target)
    }

    @Test
    fun `an empty set has no target`() {
        assertNull(emptyList<TodayEntry>().playTarget())
    }
}
