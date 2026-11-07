package com.ivandsky.puzzlebox.data.repository

import app.cash.turbine.test
import com.ivandsky.puzzlebox.data.leaderboard.SubmitError
import com.ivandsky.puzzlebox.data.leaderboard.SubmitResult
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.testing.contract.CustomBoardRepositoryContract
import com.ivandsky.puzzlebox.testing.repository.FakeCustomBoardRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Runs [CustomBoardRepositoryContract] against [FakeCustomBoardRepository], then pins the knobs
 * only the fake can force: rules rejection, writes while offline or signed out, and a live board.
 */
class CustomBoardRepositoryContractTest : CustomBoardRepositoryContract() {

    private val fake = FakeCustomBoardRepository()

    override val repository: CustomBoardRepository get() = fake

    override fun signInAs(uid: String) {
        fake.signedIn = true
    }

    override fun signOut() {
        fake.signedIn = false
    }

    override fun goOffline() {
        fake.online = false
    }

    @Test
    fun `rejectSubmits is Err Rejected and writes nothing`() = runTest {
        fake.rejectSubmits = true

        val result = fake.submit(code(), MIDDLE)

        assertTrue(result is SubmitResult.Err && result.error is SubmitError.Rejected)
        assertEquals(emptyMap<Any, Any>(), fake.entries)
    }

    @Test
    fun `offline submit is Err Unavailable, writes nothing and solvesCount is null`() = runTest {
        fake.online = false

        val result = fake.submit(code(), MIDDLE)

        assertTrue(result is SubmitResult.Err && result.error is SubmitError.Unavailable)
        assertEquals(emptyMap<Any, Any>(), fake.entries)
        assertNull(fake.solvesCount(code()))
    }

    @Test
    fun `signed out submit is Err Unavailable and writes nothing`() = runTest {
        fake.signedIn = false

        val result = fake.submit(code(), MIDDLE)

        assertTrue(result is SubmitResult.Err && result.error is SubmitError.Unavailable)
        assertEquals(emptyMap<Any, Any>(), fake.entries)
    }

    @Test
    fun `the board is live through submits, going offline and coming back`() = runTest {
        fake.observe(GAME, code(), RankOrder.TimeThenHints, 10).test {
            assertEquals(emptyList<Any>(), awaitItem())

            fake.submit(code(), SLOW_NO_HINTS)
            assertEquals(listOf(SLOW_NO_HINTS), awaitItem())

            fake.submit(code(), FAST_MANY_HINTS)
            assertEquals(listOf(FAST_MANY_HINTS, SLOW_NO_HINTS), awaitItem())

            fake.online = false
            assertNull(awaitItem())

            fake.online = true
            assertEquals(listOf(FAST_MANY_HINTS, SLOW_NO_HINTS), awaitItem())
        }
    }

    @Test
    fun `entries keep submit order per code`() = runTest {
        fake.submit(code(), SLOW_NO_HINTS)
        fake.submit(code(), FAST_MANY_HINTS)

        assertEquals(mapOf(code() to listOf(SLOW_NO_HINTS, FAST_MANY_HINTS)), fake.entries)
    }
}
