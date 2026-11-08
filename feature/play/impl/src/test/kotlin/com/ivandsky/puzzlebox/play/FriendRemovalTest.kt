package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.data.model.PostState
import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
import com.ivandsky.puzzlebox.data.repository.ReceivedPuzzleRepository
import com.ivandsky.puzzlebox.domain.DeleteReceivedPuzzle
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeReceivedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Remove with Undo (spec A19): a removal hides the card at once and deletes nothing until
 * its window closes; Undo inside the window leaves puzzle and saved play untouched; expiry,
 * a newer removal and clearing each delete exactly once.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FriendRemovalTest {

    private val alpha = TestGames.alpha
    private val received = FakeReceivedPuzzleRepository()
    private val saved = FakeSavedPlayRepository()

    private fun TestScope.removal() = FriendRemoval(DeleteReceivedPuzzle(received, saved), this) {}

    private suspend fun store(code: ShareCode) {
        received.upsert(puzzle(code))
        saved.save(PlaySlot.Custom(alpha, code.value), byteArrayOf(7), elapsedMs = 70_000)
    }

    @Test
    fun `remove hides the code and deletes nothing while the window is open`() = runTest {
        store(A)
        val removal = removal()

        removal.remove(A, alpha)
        advanceTimeBy(FriendRemoval.UNDO_WINDOW_MS - 1)

        assertEquals(A, removal.removed.value)
        assertEquals(setOf(A), received.rows.keys)
        assertEquals(1, saved.rows.size)
    }

    @Test
    fun `Undo inside the window keeps the puzzle and its saved play untouched`() = runTest {
        store(A)
        val removal = removal()

        removal.remove(A, alpha)
        advanceTimeBy(FriendRemoval.UNDO_WINDOW_MS - 1)
        removal.undo()
        advanceTimeBy(FriendRemoval.UNDO_WINDOW_MS)
        runCurrent()

        assertNull(removal.removed.value)
        assertEquals(setOf(A), received.rows.keys)
        assertEquals(1, saved.rows.size)
    }

    @Test
    fun `expiry deletes the puzzle and its saved play`() = runTest {
        store(A)
        val removal = removal()

        removal.remove(A, alpha)
        advanceTimeBy(FriendRemoval.UNDO_WINDOW_MS + 1)
        runCurrent()

        assertNull(removal.removed.value)
        assertEquals(emptySet<ShareCode>(), received.rows.keys)
        assertEquals(0, saved.rows.size)
    }

    @Test
    fun `a newer removal deletes the older one at once and holds its own`() = runTest {
        store(A)
        store(B)
        val removal = removal()

        removal.remove(A, alpha)
        removal.remove(B, alpha)
        runCurrent()

        assertEquals(B, removal.removed.value)
        assertEquals(setOf(B), received.rows.keys)
    }

    @Test
    fun `clearing the owner deletes the pending puzzle`() = runTest {
        store(A)
        val removal = removal()

        removal.remove(A, alpha)
        removal.clear()
        runCurrent()

        assertNull(removal.removed.value)
        assertEquals(emptySet<ShareCode>(), received.rows.keys)
        assertEquals(0, saved.rows.size)
    }

    @Test
    fun `each close deletes exactly once`() = runTest {
        store(A)
        store(B)
        val deletes = ArrayList<ShareCode>()
        val counting = object : ReceivedPuzzleRepository by received {
            override suspend fun delete(code: ShareCode) {
                deletes += code
                received.delete(code)
            }
        }
        val removal = FriendRemoval(DeleteReceivedPuzzle(counting, saved), this) {}

        removal.remove(A, alpha)
        removal.remove(B, alpha)
        advanceTimeBy(FriendRemoval.UNDO_WINDOW_MS + 1)
        removal.clear()
        runCurrent()

        assertEquals(listOf(A, B), deletes)
    }

    private fun puzzle(code: ShareCode) = ReceivedPuzzle(
        code = code,
        gameId = alpha,
        variant = TestGames.variant(alpha),
        sharedFormatVersion = 1,
        puzzleBytes = byteArrayOf(TestGames.MAGIC),
        title = "Crown",
        authorName = "Bo",
        note = "",
        authorTimeMs = 100_000,
        authorHints = 0,
        authorUid = "author",
        openedAt = Instant.ofEpochMilli(1_700_000_000_000),
        unpublished = false,
        postState = PostState.Pending,
        postAttempts = 0,
        difficulty = null,
    )

    private companion object {
        val A = checkNotNull(ShareCode.parse("K7Q2MX"))
        val B = checkNotNull(ShareCode.parse("B4R9TZ"))
    }
}
