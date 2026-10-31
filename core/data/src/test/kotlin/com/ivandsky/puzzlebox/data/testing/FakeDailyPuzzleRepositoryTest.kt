package com.ivandsky.puzzlebox.data.testing

import app.cash.turbine.test
import com.ivandsky.puzzlebox.common.result.AppError
import com.ivandsky.puzzlebox.common.result.AppResult
import com.ivandsky.puzzlebox.data.model.DailyPuzzle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class FakeDailyPuzzleRepositoryTest {

    private val puzzle = DailyPuzzle("zip", "2026-10-31", size = 6, cells = List(36) { 0 })

    @Test
    fun `seed drives observeHistory and cached`() = runTest {
        val fake = FakeDailyPuzzleRepository().apply { seed(puzzle) }

        fake.observeHistory("zip").test {
            assertEquals(listOf("2026-10-31"), awaitItem().map { it.day })
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals("2026-10-31", fake.cached("zip", "2026-10-31")?.day)
    }

    @Test
    fun `default refresh returns seeded cache and records the call`() = runTest {
        val fake = FakeDailyPuzzleRepository().apply { seed(puzzle) }

        assertEquals(AppResult.Ok(puzzle), fake.refresh("zip", "2026-10-31"))
        assertEquals(listOf("zip" to "2026-10-31"), fake.refreshCalls)
    }

    @Test
    fun `queued result is returned once and calls accumulate`() = runTest {
        val fake = FakeDailyPuzzleRepository()
        fake.nextRefreshResult = AppResult.Err(AppError.Network(IOException("offline")))

        assertTrue(fake.refresh("zip", "2026-10-31") is AppResult.Err)
        assertTrue(fake.refresh("queens", "2026-10-31") is AppResult.Err)

        assertEquals(
            listOf("zip" to "2026-10-31", "queens" to "2026-10-31"),
            fake.refreshCalls,
        )
    }
}
