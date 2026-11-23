package com.ivandsky.puzzlebox.data.linkedin

import com.ivandsky.puzzlebox.game.linkedin.LinkedInBoard
import com.ivandsky.puzzlebox.game.linkedin.LinkedInOwnResult
import com.ivandsky.puzzlebox.game.linkedin.LinkedInRead
import com.ivandsky.puzzlebox.game.linkedin.LinkedInSendResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * LinkedIn serves a day's connections board only to a player who has played the day, so a post that ends with
 * the result on LinkedIn reads the board then, and a post that put nothing there reads none.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LinkedInPostBoardReadTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val rigs = mutableListOf<LinkedInPostRig>()

    @After
    fun tearDown() {
        rigs.forEach { it.close() }
    }

    private fun TestScope.rig(callLimit: Int = 100): LinkedInPostRig =
        LinkedInPostRig(this, folder.newFolder(), callLimit = callLimit).also { rigs += it }

    /**
     * Runs the scheduler until the post's board reads have reached [reads] and stored a board. Room answers on its own
     * thread, which the test scheduler does not see, so a fixed number of rounds is a race on a loaded machine; this
     * waits for the thing itself. The clock stays put unless [pastRetry]: the retry's delay lives in the app scope,
     * which the test treats as background, and advanceUntilIdle does not wait for background work, so the retry is
     * fired by moving the clock past it, never by idling.
     */
    private suspend fun TestScope.awaitBoard(
        rig: LinkedInPostRig,
        reads: Int = 1,
        ghosts: Int = 1,
        pastRetry: Boolean = false,
    ) {
        val deadline = System.nanoTime() + AWAIT_NANOS
        while ((rig.boardReads < reads || rig.storedGhosts().size < ghosts) && System.nanoTime() < deadline) {
            if (pastRetry) advanceTimeBy(BOARD_RETRY_MS)
            runCurrent()
        }
    }

    /** Lets the clock run far past any retry, Room's thread included, to show that no read is left to come. */
    private suspend fun TestScope.settleLate(rig: LinkedInPostRig) {
        repeat(3) {
            rig.storedGhosts()
            advanceTimeBy(10 * BOARD_RETRY_MS)
            runCurrent()
        }
    }

    private val withoutMe = LinkedInRead.Ok(LinkedInBoard(LinkedInPostRig.BOARD.rows.filter { it.memberRef != "me" }))

    @Test
    fun `a post that ends Posted reads the day's board once and stores it`() = runTest {
        val rig = rig()
        rig.postable()
        rig.boardable()

        val result = rig.submit()
        awaitBoard(rig, ghosts = 2)

        assertEquals(LinkedInPostState.Posted, result)
        assertEquals(1, rig.boardReads)
        assertEquals(listOf("board:me:6:712:0"), rig.source.calls.filter { it.startsWith("board:") })
        assertEquals(listOf("friend", "me"), rig.storedGhosts().sorted())
        settleLate(rig)
        assertEquals(1, rig.boardReads)
    }

    @Test
    fun `a post that finds the result already on LinkedIn reads the board once`() = runTest {
        val rig = rig()
        rig.postable()
        rig.boardable()
        rig.poster.sendReply = LinkedInSendResult.AlreadyPlayed

        val result = rig.submit()
        awaitBoard(rig, ghosts = 2)
        settleLate(rig)

        assertEquals(LinkedInPostState.AlreadyOnLinkedIn, result)
        assertEquals(1, rig.boardReads)
        assertEquals(listOf("friend", "me"), rig.storedGhosts().sorted())
    }

    @Test
    fun `a check that finds the day already solved reads the board once`() = runTest {
        val rig = rig()
        rig.postable()
        rig.boardable()
        rig.source.respondOwn("me", 6, 712, LinkedInRead.Ok(LinkedInOwnResult.Solved(timeSeconds = 70, hintFree = true)))

        val result = rig.submit()
        awaitBoard(rig)
        settleLate(rig)

        assertEquals(LinkedInPostState.AlreadyOnLinkedIn, result)
        assertEquals(1, rig.boardReads)
    }

    @Test
    fun `a board still cached from before the post is read again, whatever its cache window says`() = runTest {
        val rig = rig()
        rig.postable()
        rig.boardable()
        rig.logBoardRead(fetchedAtMs = LinkedInPostRig.NOW)

        rig.submit()
        awaitBoard(rig)
        settleLate(rig)

        assertEquals(1, rig.boardReads)
    }

    @Test
    fun `a post that ends in any other state reads no board`() = runTest {
        val sends = listOf(
            LinkedInSendResult.Offline,
            LinkedInSendResult.Unknown,
            LinkedInSendResult.SignedOut,
            LinkedInSendResult.Rejected(LinkedInRead.Rejected.Kind.RateLimited),
        )

        val reads = sends.map { reply ->
            val rig = rig()
            rig.postable()
            rig.boardable()
            rig.poster.sendReply = reply
            rig.submit()
            settleLate(rig)
            rig.boardReads
        }
        val refused = rig().apply {
            postable()
            boardable()
            link.setPosting(null)
        }
        refused.submit()
        settleLate(refused)

        assertEquals(List(sends.size) { 0 }, reads)
        assertEquals(0, refused.boardReads)
    }

    @Test
    fun `the board read takes a call slot of the same budget`() = runTest {
        val rig = rig(callLimit = 4)
        rig.postable()
        rig.boardable()

        rig.submit()
        awaitBoard(rig)

        assertEquals(false, rig.budget.tryAcquire())
    }

    @Test
    fun `a board that does not hold the player yet is read once more after a delay and then never again`() = runTest {
        val rig = rig()
        rig.postable()
        rig.boardable(withoutMe)

        rig.submit()
        awaitBoard(rig)
        assertEquals(1, rig.boardReads)

        rig.source.respondBoard("me", 6, 712, 0, LinkedInRead.Ok(LinkedInPostRig.BOARD))
        awaitBoard(rig, reads = 2, ghosts = 2, pastRetry = true)
        settleLate(rig)

        assertEquals(2, rig.boardReads)
        assertEquals(listOf("friend", "me"), rig.storedGhosts().sorted())
    }

    @Test
    fun `a board that still does not hold the player after the retry is not read a third time`() = runTest {
        val rig = rig()
        rig.postable()
        rig.boardable(withoutMe)

        rig.submit()
        awaitBoard(rig, reads = 2, pastRetry = true)
        settleLate(rig)

        assertEquals(2, rig.boardReads)
    }

    @Test
    fun `a board read that could not reach LinkedIn is not retried`() = runTest {
        val rig = rig()
        rig.postable()
        rig.boardable(LinkedInRead.Offline)

        rig.submit()
        awaitBoard(rig, ghosts = 0)
        settleLate(rig)

        assertEquals(1, rig.boardReads)
    }

    private companion object {
        const val AWAIT_NANOS = 20_000_000_000L
    }
}
