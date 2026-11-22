package com.ivandsky.puzzlebox.insights.domain.stats

import app.cash.turbine.test
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.insights.domain.recap.RecapHistoryFixture
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import com.ivandsky.puzzlebox.testing.game.TestGames.beta
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** The dates the Since filter reads: the puzzle day and the first counted solve of the scope. */
class ObserveStatsSinceTest {

    private val fixture = RecapHistoryFixture()
    private val observe = ObserveStatsSince(fixture.observeHistory, fixture.calendar, fixture.registry)

    private fun day(dayOfMonth: Int, month: Int = 11) = LocalDate.of(2026, month, dayOfMonth)

    @Test
    fun `every game reads the earliest solve of any game`() = runTest {
        fixture.store(fixture.solve(alpha, day(9, month = 8)), fixture.solve(beta, day(2, month = 6)))

        observe(scope = null).test {
            assertEquals(StatsSince(today = day(20), firstSolve = day(2, month = 6)), awaitItem())
        }
    }

    @Test
    fun `one game reads its own first solve`() = runTest {
        fixture.store(fixture.solve(alpha, day(9, month = 8)), fixture.solve(beta, day(2, month = 6)))

        observe(scope = alpha).test {
            assertEquals(StatsSince(today = day(20), firstSolve = day(9, month = 8)), awaitItem())
        }
    }

    @Test
    fun `no solve has no first day`() = runTest {
        observe(scope = null).test {
            assertEquals(StatsSince(today = day(20), firstSolve = null), awaitItem())
        }
    }

    @Test
    fun `no game registered has no dates rather than no answer`() = runTest {
        val none = ObserveStatsSince(fixture.observeHistory, fixture.calendar, GameRegistry(emptyList()))

        none(scope = null).test {
            assertEquals(null, awaitItem())
            awaitComplete()
        }
    }
}
