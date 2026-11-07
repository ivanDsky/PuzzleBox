package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.model.PublishedPuzzle
import com.ivandsky.puzzlebox.data.model.SharedPuzzleDoc
import com.ivandsky.puzzlebox.data.repository.UnpublishResult
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.testing.repository.FakePublishedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unpublish (spec 21): the remote tombstone goes first and only a confirmed one marks the
 * author's local row, so a failed call leaves the puzzle listed as live.
 */
class UpdatePublishStateTest {

    private val shared = FakeSharedPuzzleRepository().apply { signedInUid = AUTHOR }
    private val published = FakePublishedPuzzleRepository()
    private val unpublish = UpdatePublishState(shared, published)

    @Before
    fun publishOne() = runTest {
        shared.publish(doc())
        published.upsert(row())
    }

    @Test
    fun `done sets the remote tombstone and marks the local row`() = runTest {
        assertEquals(UnpublishResult.Done, unpublish(CODE))

        assertTrue(shared.docs.getValue(CODE).unpublished)
        assertTrue(published.rows.getValue(CODE).unpublished)
    }

    @Test
    fun `offline changes neither the doc nor the local row`() = runTest {
        shared.online = false

        assertEquals(UnpublishResult.Offline, unpublish(CODE))

        assertFalse(shared.docs.getValue(CODE).unpublished)
        assertFalse(published.rows.getValue(CODE).unpublished)
    }

    @Test
    fun `a rejected call leaves the local row live`() = runTest {
        shared.signedInUid = "someone-else"

        val result = unpublish(CODE)

        assertTrue(result is UnpublishResult.Rejected)
        assertFalse(published.rows.getValue(CODE).unpublished)
    }

    private fun doc() = SharedPuzzleDoc(
        formatVersion = SharedPuzzleDoc.FORMAT_VERSION,
        code = CODE,
        gameId = GAME.value,
        variant = VARIANT.value,
        shareCodecVersion = 1,
        payload = byteArrayOf(1),
        title = "Castle",
        authorName = "Ivan",
        note = "",
        authorUid = AUTHOR,
        authorTimeMs = 102_000,
        authorHints = 0,
        createdAtMs = NOW.toEpochMilli(),
        unpublished = false,
    )

    private fun row() = PublishedPuzzle(
        code = CODE,
        gameId = GAME,
        variant = VARIANT,
        title = "Castle",
        authorTimeMs = 102_000,
        authorHints = 0,
        createdAt = NOW,
        unpublished = false,
        solvesCount = null,
        solvesCountAt = null,
    )

    private companion object {
        const val AUTHOR = "author-uid"
        val CODE: ShareCode = checkNotNull(ShareCode.parse("K7Q2MX"))
        val GAME = GameId("fake")
        val VARIANT = VariantId("small")
        val NOW: Instant = Instant.parse("2026-11-07T10:00:00Z")
    }
}
