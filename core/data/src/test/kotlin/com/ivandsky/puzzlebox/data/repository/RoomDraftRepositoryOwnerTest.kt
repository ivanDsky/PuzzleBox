package com.ivandsky.puzzlebox.data.repository

import androidx.room.Room
import app.cash.turbine.test
import com.ivandsky.puzzlebox.data.model.Draft
import com.ivandsky.puzzlebox.database.DraftEntity
import com.ivandsky.puzzlebox.database.PuzzleBoxDatabase
import com.ivandsky.puzzlebox.database.SyncTombstoneEntity
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.cloud.FakeOwnerScope
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Owner filter, stamping and tombstones of [RoomDraftRepository] over real in-memory Room (ADR-0029 A2, A3, A17). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomDraftRepositoryOwnerTest {

    private lateinit var db: PuzzleBoxDatabase
    private val owners = FakeOwnerScope()
    private var nowMs = NOW_MS
    private lateinit var repository: DraftRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), PuzzleBoxDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomDraftRepository(db, db.draftDao(), db.syncTombstoneDao(), owners) { nowMs }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun seed(id: String, owner: String?, updatedAtMs: Long = 1_000L, fingerprint: String? = null) =
        db.draftDao().upsert(entity(id, owner, updatedAtMs, fingerprint))

    private suspend fun ownerOf(id: String): String? = db.draftDao().find(id)?.ownerUid

    private suspend fun tombstones(owner: String): List<SyncTombstoneEntity> =
        db.syncTombstoneDao().pendingEligible(owner, Long.MAX_VALUE)

    @Test
    fun `the list shows the visible owner's drafts and the ownerless ones`() = runTest {
        seed("ann", "uid-ann", updatedAtMs = 3L)
        seed("bob", "uid-bob", updatedAtMs = 2L)
        seed("loose", null, updatedAtMs = 1L)
        owners.visibleOwner = "uid-ann"

        assertEquals(listOf("ann", "loose"), repository.observeAll().first().map { it.id })
    }

    @Test
    fun `no visible owner shows every draft`() = runTest {
        seed("ann", "uid-ann", updatedAtMs = 3L)
        seed("bob", "uid-bob", updatedAtMs = 2L)
        seed("loose", null, updatedAtMs = 1L)

        assertEquals(listOf("ann", "bob", "loose"), repository.observeAll().first().map { it.id })
    }

    @Test
    fun `the list follows the visible owner when it changes`() = runTest {
        seed("ann", "uid-ann", updatedAtMs = 2L)
        seed("bob", "uid-bob", updatedAtMs = 1L)
        owners.visibleOwner = "uid-ann"

        repository.observeAll().test {
            assertEquals(listOf("ann"), awaitItem().map { it.id })
            owners.visibleOwner = "uid-bob"
            assertEquals(listOf("bob"), awaitItem().map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `opening a draft by id respects the visible owner`() = runTest {
        seed("ann", "uid-ann")
        seed("bob", "uid-bob")
        seed("loose", null)
        owners.visibleOwner = "uid-ann"

        assertEquals("ann", repository.get("ann")?.id)
        assertEquals("loose", repository.get("loose")?.id)
        assertNull(repository.get("bob"))
        assertNull(repository.observe("bob").first())
        assertEquals("ann", repository.observe("ann").first()?.id)
    }

    @Test
    fun `a new draft takes the writer uid`() = runTest {
        owners.writerUid = "uid-ann"

        repository.upsert(draft("a"))

        assertEquals("uid-ann", ownerOf("a"))
    }

    @Test
    fun `a new draft stays ownerless while nobody is signed in`() = runTest {
        repository.upsert(draft("a"))

        assertNull(ownerOf("a"))
    }

    @Test
    fun `editing an ownerless draft stamps the writer uid`() = runTest {
        seed("a", null)
        owners.writerUid = "uid-ann"

        repository.upsert(draft("a", title = "Edited"))

        assertEquals("uid-ann", ownerOf("a"))
    }

    @Test
    fun `editing an owned draft keeps its owner and its upload marker`() = runTest {
        seed("a", "uid-ann", fingerprint = "fp-1")
        owners.writerUid = "uid-bob"

        repository.upsert(draft("a", title = "Edited"))

        val row = db.draftDao().find("a")
        assertEquals("uid-ann", row?.ownerUid)
        assertEquals("fp-1", row?.uploadedFingerprint)
        assertEquals("Edited", row?.title)
    }

    @Test
    fun `deleting an owned draft writes a tombstone due after the undo window and the margin`() = runTest {
        seed("a", "uid-ann")

        repository.delete("a")

        assertNull(db.draftDao().find("a"))
        assertEquals(
            listOf(SyncTombstoneEntity("drafts", "a", "uid-ann", NOW_MS, NOW_MS + DRAFT_UNDO_WINDOW_MS + MARGIN_MS)),
            tombstones("uid-ann"),
        )
        assertEquals(5_000L, DRAFT_UNDO_WINDOW_MS)
    }

    @Test
    fun `deleting an ownerless draft writes no tombstone`() = runTest {
        seed("a", null)

        repository.delete("a")

        assertNull(db.draftDao().find("a"))
        assertEquals(emptyList<SyncTombstoneEntity>(), tombstones("uid-ann"))
    }

    @Test
    fun `deleting a missing draft writes no tombstone`() = runTest {
        owners.writerUid = "uid-ann"

        repository.delete("nope")

        assertEquals(emptyList<SyncTombstoneEntity>(), tombstones("uid-ann"))
    }

    @Test
    fun `undoing a delete removes the tombstone`() = runTest {
        seed("a", "uid-ann")
        owners.writerUid = "uid-ann"
        val deleted = repository.get("a") ?: error("seeded draft missing")
        repository.delete("a")
        assertEquals(1, tombstones("uid-ann").size)

        repository.upsert(deleted)

        assertEquals(emptyList<SyncTombstoneEntity>(), tombstones("uid-ann"))
        assertEquals("uid-ann", ownerOf("a"))
    }

    private fun draft(id: String, title: String = "Draft $id") = Draft(
        id = id,
        gameId = GameId("queens"),
        variant = VariantId("queens/n8"),
        draftFormatVersion = 1,
        blob = byteArrayOf(1, 2, 3),
        title = title,
        updatedAt = Instant.ofEpochMilli(1_000L),
        testSolve = null,
        reservedCode = null,
        imported = false,
        authorName = "",
        note = "",
    )

    private fun entity(id: String, owner: String?, updatedAtMs: Long, fingerprint: String?) = DraftEntity(
        id = id,
        gameId = "queens",
        variant = "queens/n8",
        draftFormatVersion = 1,
        blob = byteArrayOf(1, 2, 3),
        title = "Draft $id",
        updatedAt = updatedAtMs,
        testLayoutKey = null,
        testTimeMs = null,
        testHints = null,
        reservedCode = null,
        imported = false,
        authorName = "",
        note = "",
        ownerUid = owner,
        uploadedFingerprint = fingerprint,
    )

    private companion object {
        const val NOW_MS = 100_000L
        const val MARGIN_MS = 5_000L
    }
}
