package com.ivandsky.puzzlebox.data.repository

import androidx.room.Room
import com.ivandsky.puzzlebox.testing.room.RoomQueryThread
import com.ivandsky.puzzlebox.data.model.PostState
import com.ivandsky.puzzlebox.data.model.PublishedPuzzle
import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
import com.ivandsky.puzzlebox.database.PublishedPuzzleEntity
import com.ivandsky.puzzlebox.database.PuzzleBoxDatabase
import com.ivandsky.puzzlebox.database.ReceivedPuzzleEntity
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.testing.cloud.FakeOwnerScope
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Owner scoping of the published and received lists over in-memory Room: readers show the visible
 * owner's rows plus ownerless ones, writes stamp the writer uid, targeted updates leave owner and
 * upload fingerprint alone, and deleting an owned received puzzle leaves an immediately eligible tombstone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SharedListsOwnerTest {

    private val db: PuzzleBoxDatabase = Room
        .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), PuzzleBoxDatabase::class.java)
        .allowMainThreadQueries()
        .setQueryExecutor(RoomQueryThread.executor)
        .build()
    private val scope = FakeOwnerScope()
    private var nowMs = 5_000L
    private val published = RoomPublishedPuzzleRepository(db.publishedPuzzleDao(), scope)
    private val received = RoomReceivedPuzzleRepository(db.receivedPuzzleDao(), scope) { nowMs }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `published list shows the visible owner's rows and ownerless rows`() = runTest {
        seedPublished("AAA111", owner = "ann", createdAt = 3)
        seedPublished("BBB222", owner = "bob", createdAt = 2)
        seedPublished("CCC333", owner = null, createdAt = 1)

        assertEquals(listOf("AAA111", "BBB222", "CCC333"), publishedCodes())
        scope.visibleOwner = "ann"
        assertEquals(listOf("AAA111", "CCC333"), publishedCodes())
        scope.visibleOwner = "bob"
        assertEquals(listOf("BBB222", "CCC333"), publishedCodes())
        scope.visibleOwner = "cy"
        assertEquals(listOf("CCC333"), publishedCodes())
    }

    @Test
    fun `published single row is hidden from another owner`() = runTest {
        seedPublished("AAA111", owner = "ann", createdAt = 1)

        scope.visibleOwner = "bob"
        assertNull(published.observe(code("AAA111")).first())
        scope.visibleOwner = "ann"
        assertEquals("AAA111", published.observe(code("AAA111")).first()?.code?.value)
    }

    @Test
    fun `published upsert stamps the writer uid and no fingerprint`() = runTest {
        scope.writerUid = "ann"
        published.upsert(publishedPuzzle("AAA111"))
        scope.writerUid = null
        published.upsert(publishedPuzzle("BBB222"))

        assertEquals("ann", publishedRow("AAA111").ownerUid)
        assertNull(publishedRow("AAA111").uploadedFingerprint)
        assertNull(publishedRow("BBB222").ownerUid)
    }

    @Test
    fun `published unpublish and solve count keep owner and fingerprint`() = runTest {
        seedPublished("AAA111", owner = "ann", createdAt = 1, fingerprint = "fp-1")
        scope.writerUid = "bob"

        published.markUnpublished(code("AAA111"))
        published.updateSolves(code("AAA111"), count = 7, at = Instant.ofEpochMilli(9))

        val row = publishedRow("AAA111")
        assertEquals("ann", row.ownerUid)
        assertEquals("fp-1", row.uploadedFingerprint)
        assertEquals(true, row.unpublished)
        assertEquals(7, row.solvesCount)
    }

    @Test
    fun `received list shows the visible owner's rows and ownerless rows`() = runTest {
        seedReceived("AAA111", owner = "ann", openedAt = 3)
        seedReceived("BBB222", owner = "bob", openedAt = 2)
        seedReceived("CCC333", owner = null, openedAt = 1)

        assertEquals(listOf("AAA111", "BBB222", "CCC333"), receivedCodes())
        scope.visibleOwner = "ann"
        assertEquals(listOf("AAA111", "CCC333"), receivedCodes())
        scope.visibleOwner = "bob"
        assertEquals(listOf("BBB222", "CCC333"), receivedCodes())
        scope.visibleOwner = "cy"
        assertEquals(listOf("CCC333"), receivedCodes())
    }

    @Test
    fun `received single row reads are hidden from another owner`() = runTest {
        seedReceived("AAA111", owner = "ann", openedAt = 1)

        scope.visibleOwner = "bob"
        assertNull(received.get(code("AAA111")))
        assertNull(received.observe(code("AAA111")).first())
        scope.visibleOwner = "ann"
        assertEquals("AAA111", received.get(code("AAA111"))?.code?.value)
        assertEquals("AAA111", received.observe(code("AAA111")).first()?.code?.value)
    }

    @Test
    fun `received upsert stamps the writer uid and no fingerprint`() = runTest {
        scope.writerUid = "ann"
        received.upsert(receivedPuzzle("AAA111"))
        scope.writerUid = null
        received.upsert(receivedPuzzle("BBB222"))

        assertEquals("ann", receivedRow("AAA111").ownerUid)
        assertNull(receivedRow("AAA111").uploadedFingerprint)
        assertNull(receivedRow("BBB222").ownerUid)
    }

    @Test
    fun `received post state and unpublish keep owner and fingerprint`() = runTest {
        seedReceived("AAA111", owner = "ann", openedAt = 1, fingerprint = "fp-1")
        scope.writerUid = "bob"

        received.updatePost(code("AAA111"), PostState.Posted, attempts = 2)
        received.markUnpublished(code("AAA111"))

        val row = receivedRow("AAA111")
        assertEquals("ann", row.ownerUid)
        assertEquals("fp-1", row.uploadedFingerprint)
        assertEquals("Posted", row.postState)
        assertEquals(true, row.unpublished)
    }

    @Test
    fun `deleting an owned received puzzle writes an immediately eligible tombstone`() = runTest {
        seedReceived("AAA111", owner = "ann", openedAt = 1)

        received.delete(code("AAA111"))

        assertNull(received.get(code("AAA111")))
        val pending = db.syncTombstoneDao().pendingEligible("ann", nowMs)
        assertEquals(listOf("received"), pending.map { it.kind })
        assertEquals(listOf("AAA111"), pending.map { it.docId })
        assertEquals(listOf(nowMs), pending.map { it.deletedAtMs })
        assertEquals(listOf(nowMs), pending.map { it.eligibleAtMs })
        assertEquals(listOf(false), pending.map { it.uploaded })
    }

    @Test
    fun `deleting an ownerless received puzzle writes no tombstone`() = runTest {
        seedReceived("AAA111", owner = null, openedAt = 1)

        received.delete(code("AAA111"))

        assertNull(received.get(code("AAA111")))
        assertEquals(emptyList<String>(), db.syncTombstoneDao().pendingEligible("ann", Long.MAX_VALUE).map { it.docId })
    }

    @Test
    fun `deleting an unknown received code writes no tombstone`() = runTest {
        received.delete(code("AAA111"))

        assertEquals(emptyList<String>(), db.syncTombstoneDao().pendingEligible("ann", Long.MAX_VALUE).map { it.docId })
    }

    private fun code(raw: String): ShareCode = requireNotNull(ShareCode.parse(raw))

    private suspend fun publishedCodes() = published.observeAll().first().map { it.code.value }

    private suspend fun receivedCodes() = received.observeAll().first().map { it.code.value }

    private suspend fun publishedRow(raw: String): PublishedPuzzleEntity = db.publishedPuzzleDao().observe(raw, null).first()!!

    private suspend fun receivedRow(raw: String): ReceivedPuzzleEntity = db.receivedPuzzleDao().get(raw, null)!!

    private suspend fun seedPublished(raw: String, owner: String?, createdAt: Long, fingerprint: String? = null) =
        db.publishedPuzzleDao().upsert(
            PublishedPuzzleEntity(
                code = raw,
                gameId = "queens",
                variant = "queens/n8",
                title = "Puzzle",
                authorTimeMs = 1L,
                createdAt = createdAt,
                unpublished = false,
                solvesCount = null,
                solvesCountAt = null,
                authorHints = 0,
                ownerUid = owner,
                uploadedFingerprint = fingerprint,
            ),
        )

    private suspend fun seedReceived(raw: String, owner: String?, openedAt: Long, fingerprint: String? = null) =
        db.receivedPuzzleDao().upsert(
            ReceivedPuzzleEntity(
                code = raw,
                gameId = "queens",
                variant = "queens/n8",
                sharedFormatVersion = 1,
                puzzleBytes = byteArrayOf(1, 2, 3),
                title = "Puzzle",
                authorName = "Ann",
                note = "",
                authorTimeMs = 1L,
                authorHints = 0,
                authorUid = "uid-ann",
                openedAt = openedAt,
                unpublished = false,
                postState = "Pending",
                postAttempts = 0,
                difficulty = null,
                ownerUid = owner,
                uploadedFingerprint = fingerprint,
            ),
        )

    private fun publishedPuzzle(raw: String) = PublishedPuzzle(
        code = code(raw),
        gameId = GameId("queens"),
        variant = VariantId("queens/n8"),
        title = "Puzzle",
        authorTimeMs = 1L,
        authorHints = 0,
        createdAt = Instant.ofEpochMilli(1),
        unpublished = false,
        solvesCount = null,
        solvesCountAt = null,
    )

    private fun receivedPuzzle(raw: String) = ReceivedPuzzle(
        code = code(raw),
        gameId = GameId("queens"),
        variant = VariantId("queens/n8"),
        sharedFormatVersion = 1,
        puzzleBytes = byteArrayOf(1, 2, 3),
        title = "Puzzle",
        authorName = "Ann",
        note = "",
        authorTimeMs = 1L,
        authorHints = 0,
        authorUid = "uid-ann",
        openedAt = Instant.ofEpochMilli(1),
        unpublished = false,
        postState = PostState.Pending,
        postAttempts = 0,
        difficulty = null,
    )
}
