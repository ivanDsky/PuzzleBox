package com.ivandsky.puzzlebox.data.linkedin

import com.ivandsky.puzzlebox.game.linkedin.LinkedInBoard
import com.ivandsky.puzzlebox.game.linkedin.LinkedInOwnResult
import com.ivandsky.puzzlebox.game.linkedin.LinkedInRead
import com.ivandsky.puzzlebox.game.linkedin.LinkedInSendResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
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

    /** Runs everything the post left scheduled, Room's own thread included: a store's answer is what schedules the next step. */
    private suspend fun TestScope.settle(rig: LinkedInPostRig) {
        repeat(3) {
            rig.storedGhosts()
            advanceUntilIdle()
        }
    }

    private val withoutMe = LinkedInRead.Ok(LinkedInBoard(LinkedInPostRig.BOARD.rows.filter { it.memberRef != "me" }))

    @Test
    fun `a post that ends Posted reads the day's board once and stores it`() = runTest {
        val rig = rig()
        rig.postable()
        rig.boardable()

        val result = rig.submit()
        runCurrent()

        assertEquals(LinkedInPostState.Posted, result)
        assertEquals(1, rig.boardReads)
        assertEquals(listOf("board:me:6:712:0"), rig.source.calls.filter { it.startsWith("board:") })
        assertEquals(listOf("friend", "me"), rig.storedGhosts().sorted())
        settle(rig)
        assertEquals(1, rig.boardReads)
    }

    @Test
    fun `a post that finds the result already on LinkedIn reads the board once`() = runTest {
        val rig = rig()
        rig.postable()
        rig.boardable()
        rig.poster.sendReply = LinkedInSendResult.AlreadyPlayed

        val result = rig.submit()
        settle(rig)

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
        settle(rig)

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
        settle(rig)

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
            settle(rig)
            rig.boardReads
        }
        val refused = rig().apply {
            postable()
            boardable()
            link.setPosting(null)
        }
        refused.submit()
        settle(refused)

        assertEquals(List(sends.size) { 0 }, reads)
        assertEquals(0, refused.boardReads)
    }

    @Test
    fun `the board read takes a call slot of the same budget`() = runTest {
        val rig = rig(callLimit = 4)
        rig.postable()
        rig.boardable()

        rig.submit()
        runCurrent()

        assertEquals(false, rig.budget.tryAcquire())
    }

    @Test
    fun `a board that does not hold the player yet is read once more after a delay and then never again`() = runTest {
        val rig = rig()
        rig.postable()
        rig.boardable(withoutMe)

        rig.submit()
        runCurrent()
        assertEquals(1, rig.boardReads)

        rig.source.respondBoard("me", 6, 712, 0, LinkedInRead.Ok(LinkedInPostRig.BOARD))
        settle(rig)

        assertEquals(2, rig.boardReads)
        assertEquals(listOf("friend", "me"), rig.storedGhosts().sorted())
    }

    @Test
    fun `a board that still does not hold the player after the retry is not read a third time`() = runTest {
        val rig = rig()
        rig.postable()
        rig.boardable(withoutMe)

        rig.submit()
        settle(rig)

        assertEquals(2, rig.boardReads)
    }

    @Test
    fun `a board read that could not reach LinkedIn is not retried`() = runTest {
        val rig = rig()
        rig.postable()
        rig.boardable(LinkedInRead.Offline)

        rig.submit()
        settle(rig)

        assertEquals(1, rig.boardReads)
    }
}
