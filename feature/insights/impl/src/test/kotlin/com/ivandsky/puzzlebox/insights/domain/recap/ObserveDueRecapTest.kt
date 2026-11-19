package com.ivandsky.puzzlebox.insights.domain.recap

import app.cash.turbine.test
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import com.ivandsky.puzzlebox.testing.game.TestGames.beta
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** When Today offers last month's recap (A2, A4), on the puzzle day from the game calendar and never the device's. */
class ObserveDueRecapTest {

    private val fixture = RecapHistoryFixture()
    private val due = ObserveDueRecap(fixture.availability())

    private val octoberSolve = fixture.solve(alpha, LocalDate.of(2026, 10, 14))

    private fun day(month: Int, dayOfMonth: Int) = LocalDate.of(2026, month, dayOfMonth)

    private suspend fun assertDue(periodId: String?) = due().test {
        assertEquals(periodId, awaitItem()?.periodId)
        cancelAndIgnoreRemainingEvents()
    }

    @Test
    fun `due on each of days 1 to 3 once the day has its first counted solve`() = runTest {
        for (dayOfMonth in 1..3) {
            fixture.calendar.setAll(day(11, dayOfMonth))
            fixture.store(octoberSolve, fixture.solve(beta, day(11, dayOfMonth)))

            assertDue("2026-10")
        }
    }

    @Test
    fun `names the month just ended with a label the player reads`() = runTest {
        fixture.calendar.setAll(day(11, 2))
        fixture.store(octoberSolve, fixture.solve(alpha, day(11, 2)))

        due().test {
            val recap = checkNotNull(awaitItem())
            assertEquals("2026-10", recap.periodId)
            assertTrue(recap.label, "2026" in recap.label)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `not due before the day's first counted solve, due the moment it lands`() = runTest {
        fixture.calendar.setAll(day(11, 2))
        fixture.store(octoberSolve, fixture.solve(alpha, day(11, 1)))

        due().test {
            assertNull(awaitItem())
            fixture.store(octoberSolve, fixture.solve(alpha, day(11, 1)), fixture.solve(alpha, day(11, 2)))
            assertEquals("2026-10", awaitItem()?.periodId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `not due on day 4 even with a solve that day`() = runTest {
        fixture.calendar.setAll(day(11, 4))
        fixture.store(octoberSolve, fixture.solve(alpha, day(11, 4)))

        assertDue(null)
    }

    @Test
    fun `a recap that first appears after its window is never due`() = runTest {
        fixture.calendar.setAll(day(11, 12))
        fixture.store(octoberSolve, fixture.solve(alpha, day(11, 12)))

        assertDue(null)
    }

    @Test
    fun `not due when the month just ended has no counted solve`() = runTest {
        fixture.calendar.setAll(day(11, 2))
        fixture.store(
            fixture.solve(alpha, day(9, 14)),
            fixture.playAgain(alpha, day(10, 14)),
            fixture.solve(alpha, day(10, 20), puzzleNumber = null),
            fixture.solve(alpha, day(11, 2)),
        )

        assertDue(null)
    }

    @Test
    fun `play again, practice and imports of old days are not the day's first solve`() = runTest {
        fixture.calendar.setAll(day(11, 2))
        fixture.store(
            octoberSolve,
            fixture.playAgain(alpha, day(11, 2)),
            fixture.solve(beta, day(11, 2), puzzleNumber = null),
            fixture.imported(alpha, day(10, 30)),
        )

        assertDue(null)
    }

    @Test
    fun `the puzzle day rolling over to day 4 withdraws the card`() = runTest {
        fixture.calendar.setAll(day(11, 3))
        fixture.store(octoberSolve, fixture.solve(alpha, day(11, 3)), fixture.solve(alpha, day(11, 4)))

        due().test {
            assertEquals("2026-10", awaitItem()?.periodId)
            fixture.calendar.setAll(day(11, 4))
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `in January the month just ended is December of the year before`() = runTest {
        fixture.calendar.setAll(LocalDate.of(2027, 1, 2))
        fixture.store(fixture.solve(alpha, LocalDate.of(2026, 12, 31)), fixture.solve(alpha, LocalDate.of(2027, 1, 2)))

        assertDue("2026-12")
    }
}
