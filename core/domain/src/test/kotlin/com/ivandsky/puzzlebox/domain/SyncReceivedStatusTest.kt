package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.model.PostState
import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
import com.ivandsky.puzzlebox.data.model.SharedPuzzleDoc
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.testing.repository.FakeReceivedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The opened puzzle's tombstone follows its doc: a doc found unpublished marks the stored row,
 * and whatever else the fetch says (still live, offline, gone, too new) leaves the row as it was.
 */
class SyncReceivedStatusTest {

    private val shared = FakeSharedPuzzleRepository().apply { signedInUid = AUTHOR }
    private val received = FakeReceivedPuzzleRepository()
    private val sync = SyncReceivedStatus(shared, received)

    @Test
    fun `a doc found unpublished marks the stored row`() = runTest {
        publish()
        received.upsert(stored())
        shared.unpublish(CODE)

        sync(CODE)

        assertTrue(checkNotNull(received.get(CODE)).unpublished)
    }

    @Test
    fun `a live doc keeps the row`() = runTest {
        publish()
        received.upsert(stored())

        sync(CODE)

        assertEquals(stored(), received.get(CODE))
    }

    @Test
    fun `offline keeps the row, even after the author unpublished`() = runTest {
        publish()
        received.upsert(stored())
        shared.unpublish(CODE)
        shared.online = false

        sync(CODE)

        assertEquals(stored(), received.get(CODE))
    }

    @Test
    fun `a missing doc keeps the row`() = runTest {
        received.upsert(stored())

        sync(CODE)

        assertEquals(stored(), received.get(CODE))
    }

    @Test
    fun `a doc too new to read keeps the row`() = runTest {
        publish(formatVersion = SharedPuzzleDoc.FORMAT_VERSION + 1)
        received.upsert(stored())

        sync(CODE)

        assertEquals(stored(), received.get(CODE))
    }

    @Test
    fun `a row already marked stays marked when the doc can't be read`() = runTest {
        received.upsert(stored().copy(unpublished = true))
        shared.online = false

        sync(CODE)

        assertTrue(checkNotNull(received.get(CODE)).unpublished)
    }

    @Test
    fun `an unpublished doc never opened here stores nothing`() = runTest {
        publish()
        shared.unpublish(CODE)

        sync(CODE)

        assertFalse(received.rows.containsKey(CODE))
    }

    private suspend fun publish(formatVersion: Int = SharedPuzzleDoc.FORMAT_VERSION) {
        shared.publish(
            SharedPuzzleDoc(
                code = CODE, formatVersion = formatVersion, gameId = GAME.value, variant = "alpha/n4",
                shareCodecVersion = 1, payload = byteArrayOf(1, 2), title = "Twins", authorName = "Jonas",
                note = "", authorUid = AUTHOR, authorTimeMs = 42_000L, authorHints = 0, createdAtMs = 0L, unpublished = false,
            ),
        )
    }

    private fun stored() = ReceivedPuzzle(
        code = CODE, gameId = GAME, variant = VariantId("alpha/n4"), sharedFormatVersion = 1,
        puzzleBytes = byteArrayOf(2, 1), title = "Twins", authorName = "Jonas", note = "", authorTimeMs = 42_000L,
        authorHints = 0, authorUid = AUTHOR, openedAt = Instant.EPOCH, unpublished = false,
        postState = PostState.Pending, postAttempts = 0, difficulty = null,
    )

    private companion object {
        val CODE = checkNotNull(ShareCode.parse("K7Q2MX"))
        val GAME = GameId("alpha")
        const val AUTHOR = "uid-jonas"
    }
}
