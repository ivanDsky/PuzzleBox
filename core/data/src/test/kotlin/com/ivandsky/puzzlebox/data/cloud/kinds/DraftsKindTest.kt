package com.ivandsky.puzzlebox.data.cloud.kinds

import androidx.room.Room
import com.ivandsky.puzzlebox.testing.room.RoomQueryThread
import app.cash.turbine.test
import com.ivandsky.puzzlebox.data.cloud.CloudDoc
import com.ivandsky.puzzlebox.data.model.Draft
import com.ivandsky.puzzlebox.data.repository.DRAFT_UNDO_WINDOW_MS
import com.ivandsky.puzzlebox.data.repository.DraftRepository
import com.ivandsky.puzzlebox.data.repository.RoomDraftRepository
import com.ivandsky.puzzlebox.database.DraftEntity
import com.ivandsky.puzzlebox.database.PuzzleBoxDatabase
import com.ivandsky.puzzlebox.database.SyncTombstoneEntity
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.cloud.FakeOwnerScope
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** [DraftsKind] over real in-memory Room: its merge table, pending rule and acknowledge (ADR-0029 A17, SC9). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DraftsKindTest {

    private class MutableClock(var ms: Long) : Clock() {
        override fun getZone(): ZoneId = ZoneId.of("UTC")
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = Instant.ofEpochMilli(ms)
    }

    private lateinit var db: PuzzleBoxDatabase
    private val clock = MutableClock(NOW_MS)
    private val owners = FakeOwnerScope(writer = ANN)
    private lateinit var kind: DraftsKind
    private lateinit var repository: DraftRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), PuzzleBoxDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(RoomQueryThread.executor)
            .build()
        kind = DraftsKind(db, db.draftDao(), db.syncTombstoneDao(), clock)
        repository = RoomDraftRepository(db, db.draftDao(), db.syncTombstoneDao(), owners) { clock.ms }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun seed(id: String, owner: String? = ANN, editedAtMs: Long = 1_000L, fingerprint: String? = null) =
        db.draftDao().upsert(entity(id, owner, editedAtMs, fingerprint))

    private suspend fun row(id: String): DraftEntity? = db.draftDao().find(id)

    private suspend fun pendingIds(uid: String = ANN): List<String> = kind.pending(uid).map { it.id }.sorted()

    private fun live(
        id: String,
        editedAtMs: Long,
        name: String = "Cloud $id",
        blob: ByteArray = byteArrayOf(9, 9),
        reservedCode: String? = null,
    ) = CloudDoc(
        id,
        buildMap {
            put("formatVersion", 1L)
            put("gameId", "queens")
            put("variant", "queens/n8")
            put("draftFormatVersion", 1L)
            put("name", name)
            put("blob", blob)
            put("editedAtMs", editedAtMs)
            reservedCode?.let { put("reservedCode", it) }
        },
    )

    private fun tombstone(id: String, deletedAtMs: Long) =
        CloudDoc(id, mapOf("formatVersion" to 1L, "deleted" to true, "deletedAtMs" to deletedAtMs))

    @Test
    fun `the kind is drafts`() {
        assertEquals("drafts", kind.kind)
    }

    @Test
    fun `a draft's doc carries its codec keys`() = runTest {
        seed("a", editedAtMs = 2_000L)

        val doc = kind.pending(ANN).single()

        assertEquals("a", doc.id)
        assertEquals(
            setOf("formatVersion", "gameId", "variant", "draftFormatVersion", "name", "blob", "editedAtMs"),
            doc.fields.keys,
        )
        assertEquals(1L, doc.fields["formatVersion"])
        assertEquals("queens", doc.fields["gameId"])
        assertEquals("queens/n8", doc.fields["variant"])
        assertEquals(1L, doc.fields["draftFormatVersion"])
        assertEquals("Draft a", doc.fields["name"])
        assertArrayEquals(byteArrayOf(1, 2, 3), doc.fields["blob"] as ByteArray)
        assertEquals(2_000L, doc.fields["editedAtMs"])
    }

    // ---- merge table: edits ----

    @Test
    fun `a newer cloud edit replaces an older local one and is not pending`() = runTest {
        seed("a", editedAtMs = 1_000L)

        val restored = kind.apply(ANN, listOf(live("a", editedAtMs = 2_000L, name = "From cloud")))

        assertEquals(1, restored)
        val stored = row("a")!!
        assertEquals("From cloud", stored.title)
        assertEquals(2_000L, stored.updatedAt)
        assertArrayEquals(byteArrayOf(9, 9), stored.blob)
        assertEquals(ANN, stored.ownerUid)
        assertEquals(emptyList<String>(), pendingIds())
    }

    @Test
    fun `an older cloud edit loses to a newer local one that stays pending`() = runTest {
        seed("a", editedAtMs = 3_000L)

        val restored = kind.apply(ANN, listOf(live("a", editedAtMs = 2_000L, name = "From cloud")))

        assertEquals(0, restored)
        assertEquals("Draft a", row("a")!!.title)
        assertEquals(3_000L, row("a")!!.updatedAt)
        assertEquals(listOf("a"), pendingIds())
    }

    @Test
    fun `a newer local edit marked uploaded is pending again when the cloud holds an older one`() = runTest {
        seed("a", editedAtMs = 3_000L)
        kind.acknowledge(ANN, kind.pending(ANN))
        assertEquals(emptyList<String>(), pendingIds())

        kind.apply(ANN, listOf(live("a", editedAtMs = 2_000L)))

        assertEquals(listOf("a"), pendingIds())
    }

    @Test
    fun `the cloud's own copy of a local draft changes nothing`() = runTest {
        seed("a", editedAtMs = 2_000L)
        val mine = kind.pending(ANN).single()
        kind.acknowledge(ANN, listOf(mine))

        val restored = kind.apply(ANN, listOf(mine))

        assertEquals(0, restored)
        assertEquals(emptyList<String>(), pendingIds())
    }

    @Test
    fun `a cloud draft this device lacks is restored under the uid`() = runTest {
        val restored = kind.apply(ANN, listOf(live("new", editedAtMs = 2_000L)))

        assertEquals(1, restored)
        val stored = row("new")!!
        assertEquals(ANN, stored.ownerUid)
        assertEquals("queens", stored.gameId)
        assertEquals("queens/n8", stored.variant)
        assertEquals(1, stored.draftFormatVersion)
        assertEquals("Cloud new", stored.title)
        assertEquals(2_000L, stored.updatedAt)
        assertNull(stored.reservedCode)
        assertEquals(emptyList<String>(), pendingIds())
    }

    @Test
    fun `a replaced draft keeps its local-only fields`() = runTest {
        db.draftDao().upsert(
            entity("a", ANN, 1_000L, null, reservedCode = "CODE", authorName = "Ann", note = "hi"),
        )

        kind.apply(ANN, listOf(live("a", editedAtMs = 2_000L)))

        val stored = row("a")!!
        assertEquals("CODE", stored.reservedCode)
        assertEquals("Ann", stored.authorName)
        assertEquals("hi", stored.note)
    }

    @Test
    fun `a draft kept under a published code uploads its reserved code`() = runTest {
        db.draftDao().upsert(entity("a", ANN, 2_000L, null, reservedCode = "K7Q2MX"))

        val doc = kind.pending(ANN).single()

        assertEquals("K7Q2MX", doc.fields["reservedCode"])
        assertEquals(1L, doc.fields["formatVersion"])
    }

    @Test
    fun `reserving a code on an uploaded draft makes it pending again`() = runTest {
        seed("a")
        kind.acknowledge(ANN, kind.pending(ANN))
        assertEquals(emptyList<String>(), pendingIds())

        db.draftDao().upsert(entity("a", ANN, 1_000L, row("a")!!.uploadedFingerprint, reservedCode = "K7Q2MX"))

        assertEquals(listOf("a"), pendingIds())
    }

    @Test
    fun `a cloud draft with a reserved code is restored with it`() = runTest {
        val restored = kind.apply(ANN, listOf(live("new", editedAtMs = 2_000L, reservedCode = "K7Q2MX")))

        assertEquals(1, restored)
        assertEquals("K7Q2MX", row("new")!!.reservedCode)
        assertEquals(emptyList<String>(), pendingIds())
    }

    @Test
    fun `a newer cloud edit with a code replaces the content and sets the code`() = runTest {
        seed("a", editedAtMs = 1_000L)

        kind.apply(ANN, listOf(live("a", editedAtMs = 2_000L, reservedCode = "K7Q2MX")))

        val stored = row("a")!!
        assertEquals("Cloud a", stored.title)
        assertEquals("K7Q2MX", stored.reservedCode)
    }

    @Test
    fun `an older or equal cloud doc gives a local draft without a code its code and nothing else`() = runTest {
        seed("a", editedAtMs = 2_000L)
        seed("b", editedAtMs = 2_000L)

        kind.apply(
            ANN,
            listOf(
                live("a", editedAtMs = 1_000L, reservedCode = "K7Q2MX"),
                live("b", editedAtMs = 2_000L, reservedCode = "B4N8TW"),
            ),
        )

        val a = row("a")!!
        assertEquals("K7Q2MX", a.reservedCode)
        assertEquals("Draft a", a.title)
        assertEquals(2_000L, a.updatedAt)
        assertEquals("B4N8TW", row("b")!!.reservedCode)
    }

    @Test
    fun `a code is never cleared by an older or newer doc without one`() = runTest {
        db.draftDao().upsert(entity("old", ANN, 3_000L, null, reservedCode = "K7Q2MX"))
        db.draftDao().upsert(entity("new", ANN, 1_000L, null, reservedCode = "B4N8TW"))

        kind.apply(ANN, listOf(live("old", editedAtMs = 1_000L), live("new", editedAtMs = 2_000L)))

        assertEquals("K7Q2MX", row("old")!!.reservedCode)
        assertEquals("a newer doc replaces the content but not the code", "Cloud new", row("new")!!.title)
        assertEquals("B4N8TW", row("new")!!.reservedCode)
        val uploaded = kind.pending(ANN).associate { it.id to it.fields["reservedCode"] }
        assertEquals("the kept codes go back up", mapOf("old" to "K7Q2MX", "new" to "B4N8TW"), uploaded)
    }

    @Test
    fun `a local code wins over a different code in an older doc`() = runTest {
        db.draftDao().upsert(entity("a", ANN, 3_000L, null, reservedCode = "K7Q2MX"))

        kind.apply(ANN, listOf(live("a", editedAtMs = 1_000L, reservedCode = "B4N8TW")))

        assertEquals("K7Q2MX", row("a")!!.reservedCode)
    }

    @Test
    fun `a reserved code that is not a share code is ignored`() = runTest {
        kind.apply(
            ANN,
            listOf(live("bad", editedAtMs = 2_000L, reservedCode = "k7q2mx"), live("short", 2_000L, reservedCode = "K7Q")),
        )

        assertNull(row("bad")!!.reservedCode)
        assertNull(row("short")!!.reservedCode)
    }

    @Test
    fun `a doc missing a required key is skipped`() = runTest {
        val broken = CloudDoc("x", mapOf("formatVersion" to 1L, "gameId" to "queens", "editedAtMs" to 5L))

        assertEquals(0, kind.apply(ANN, listOf(broken)))
        assertNull(row("x"))
    }

    // ---- merge table: cloud tombstones ----

    @Test
    fun `a cloud tombstone beats an older local edit`() = runTest {
        seed("a", editedAtMs = 1_000L)

        val restored = kind.apply(ANN, listOf(tombstone("a", deletedAtMs = 2_000L)))

        assertEquals(0, restored)
        assertNull(row("a"))
        assertEquals(emptyList<SyncTombstoneEntity>(), db.syncTombstoneDao().pendingEligible(ANN, Long.MAX_VALUE))
    }

    @Test
    fun `a cloud tombstone loses to a newer local edit that stays pending`() = runTest {
        seed("a", editedAtMs = 3_000L)
        kind.acknowledge(ANN, kind.pending(ANN))

        kind.apply(ANN, listOf(tombstone("a", deletedAtMs = 2_000L)))

        assertNotNull(row("a"))
        assertEquals(listOf("a"), pendingIds())
    }

    @Test
    fun `a doc that was deleted and then edited reads as the newer edit`() = runTest {
        val revived = CloudDoc(
            "a",
            live("a", editedAtMs = 5_000L, name = "Back").fields + mapOf("deleted" to true, "deletedAtMs" to 4_000L),
        )

        assertEquals(1, kind.apply(ANN, listOf(revived)))
        assertEquals("Back", row("a")!!.title)
    }

    @Test
    fun `a doc that was edited and then deleted reads as the tombstone`() = runTest {
        seed("a", editedAtMs = 1_000L)
        val deleted = CloudDoc(
            "a",
            live("a", editedAtMs = 3_000L).fields + mapOf("deleted" to true, "deletedAtMs" to 4_000L),
        )

        assertEquals(0, kind.apply(ANN, listOf(deleted)))
        assertNull(row("a"))
    }

    // ---- merge table: local tombstones ----

    @Test
    fun `a cloud edit older than a pending local delete is ignored`() = runTest {
        seed("a", editedAtMs = 1_000L)
        repository.delete("a")

        val restored = kind.apply(ANN, listOf(live("a", editedAtMs = 50_000L)))

        assertEquals(0, restored)
        assertNull(row("a"))
        assertEquals(1, db.syncTombstoneDao().pendingEligible(ANN, Long.MAX_VALUE).size)
    }

    @Test
    fun `a cloud edit newer than a pending local delete brings the draft back and drops the tombstone`() = runTest {
        seed("a", editedAtMs = 1_000L)
        repository.delete("a")

        val restored = kind.apply(ANN, listOf(live("a", editedAtMs = NOW_MS + 1L)))

        assertEquals(1, restored)
        assertNotNull(row("a"))
        assertEquals(emptyList<SyncTombstoneEntity>(), db.syncTombstoneDao().pendingEligible(ANN, Long.MAX_VALUE))
    }

    // ---- first restore ----

    @Test
    fun `a draft made while the first restore runs survives`() = runTest {
        seed("old", editedAtMs = 1_000L)
        seed("started-during-restore", editedAtMs = NOW_MS)

        kind.apply(
            ANN,
            listOf(live("old", editedAtMs = 2_000L), tombstone("gone", 3_000L), live("other", editedAtMs = 4_000L)),
        )

        assertEquals("Draft started-during-restore", row("started-during-restore")!!.title)
        assertEquals("Cloud old", row("old")!!.title)
        assertEquals(listOf("started-during-restore"), pendingIds())
    }

    @Test
    fun `a draft made during the restore is not overwritten by an older cloud doc nor deleted by an older tombstone`() =
        runTest {
            seed("mine", editedAtMs = NOW_MS)

            kind.apply(ANN, listOf(live("mine", editedAtMs = 2_000L)))
            kind.apply(ANN, listOf(tombstone("mine", deletedAtMs = 2_000L)))

            assertEquals("Draft mine", row("mine")!!.title)
            assertEquals(listOf("mine"), pendingIds())
        }

    // ---- pending ----

    @Test
    fun `a row is pending only for its owner and while its fingerprint differs`() = runTest {
        seed("ann", ANN)
        seed("bob", BOB)
        seed("loose", null)

        assertEquals(listOf("ann"), pendingIds(ANN))
        assertEquals(listOf("bob"), pendingIds(BOB))
    }

    @Test
    fun `adopt claims the ownerless rows`() = runTest {
        seed("loose", null)
        seed("bob", BOB)

        kind.adopt(ANN)

        assertEquals(ANN, row("loose")!!.ownerUid)
        assertEquals(BOB, row("bob")!!.ownerUid)
        assertEquals(listOf("loose"), pendingIds(ANN))
    }

    @Test
    fun `apply never touches another account's row`() = runTest {
        seed("bob", BOB, editedAtMs = 1_000L)

        val restored = kind.apply(ANN, listOf(live("bob", editedAtMs = 9_000L), tombstone("bob", 9_000L)))

        assertEquals(0, restored)
        assertEquals("Draft bob", row("bob")!!.title)
        assertEquals(BOB, row("bob")!!.ownerUid)
    }

    @Test
    fun `a draft too big for the rules is never pending`() = runTest {
        db.draftDao().upsert(entity("big", ANN, 1_000L, null, blob = ByteArray(65_537)))
        db.draftDao().upsert(entity("fits", ANN, 1_000L, null, blob = ByteArray(65_536)))

        assertEquals(listOf("fits"), pendingIds())
    }

    // ---- tombstones ----

    @Test
    fun `a tombstone inside the undo window is not pending`() = runTest {
        seed("a")
        repository.delete("a")
        clock.ms = NOW_MS + DRAFT_UNDO_WINDOW_MS

        assertEquals(emptyList<String>(), pendingIds())
    }

    @Test
    fun `a tombstone past its eligible time uploads as a deleted doc`() = runTest {
        seed("a")
        repository.delete("a")
        clock.ms = NOW_MS + DRAFT_UNDO_WINDOW_MS + 5_000L

        val doc = kind.pending(ANN).single()

        assertEquals("a", doc.id)
        assertEquals(mapOf("formatVersion" to 1L, "deleted" to true, "deletedAtMs" to NOW_MS), doc.fields)
    }

    @Test
    fun `acknowledging a tombstone marks it uploaded`() = runTest {
        seed("a")
        repository.delete("a")
        clock.ms = NOW_MS + 60_000L
        val docs = kind.pending(ANN)

        kind.acknowledge(ANN, docs)

        assertEquals(emptyList<String>(), pendingIds())
        assertEquals(emptyList<SyncTombstoneEntity>(), db.syncTombstoneDao().pendingEligible(ANN, Long.MAX_VALUE))
    }

    @Test
    fun `a tombstone of another account is not pending`() = runTest {
        seed("a", BOB)
        owners.writerUid = BOB
        repository.delete("a")
        clock.ms = NOW_MS + 60_000L

        assertEquals(emptyList<String>(), pendingIds(ANN))
        assertEquals(listOf("a"), pendingIds(BOB))
    }

    // ---- acknowledge ----

    @Test
    fun `acknowledged rows are not pending again`() = runTest {
        seed("a")
        seed("b")

        kind.acknowledge(ANN, kind.pending(ANN))

        assertEquals(emptyList<String>(), pendingIds())
    }

    @Test
    fun `an edit after the upload started is pending again after the older upload is acknowledged`() = runTest {
        seed("a", editedAtMs = 1_000L)
        val uploaded = kind.pending(ANN)
        seed("a", editedAtMs = 2_000L)

        kind.acknowledge(ANN, uploaded)

        assertEquals(listOf("a"), pendingIds())
    }

    @Test
    fun `acknowledge under another uid changes nothing`() = runTest {
        seed("a")
        val uploaded = kind.pending(ANN)

        kind.acknowledge(BOB, uploaded)

        assertEquals(listOf("a"), pendingIds())
    }

    @Test
    fun `forget makes everything pending again`() = runTest {
        seed("a")
        seed("b", BOB)
        seed("gone")
        repository.delete("gone")
        clock.ms = NOW_MS + 60_000L
        kind.acknowledge(ANN, kind.pending(ANN))
        kind.acknowledge(BOB, kind.pending(BOB))
        assertEquals(emptyList<String>(), pendingIds())

        kind.forget(ANN)

        assertEquals(listOf("a", "gone"), pendingIds())
        assertEquals(emptyList<String>(), pendingIds(BOB))
    }

    // ---- pendingIds ----

    @Test
    fun `pendingIds follows edits and acknowledgements`() = runTest {
        seed("a")

        kind.pendingIds(ANN).test {
            assertEquals(setOf("a"), awaitItem())
            kind.acknowledge(ANN, kind.pending(ANN))
            assertEquals(emptySet<String>(), awaitItem())
            seed("b")
            assertEquals(setOf("b"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `pendingIds shows a deleted draft once its undo window has closed`() = runTest {
        seed("a")
        kind.acknowledge(ANN, kind.pending(ANN))
        repository.delete("a")

        kind.pendingIds(ANN).test {
            assertEquals(emptySet<String>(), awaitItem())
            clock.ms = NOW_MS + DRAFT_UNDO_WINDOW_MS + 5_000L
            testScheduler.advanceTimeBy(DRAFT_UNDO_WINDOW_MS + 5_001L)
            assertEquals(setOf("a"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an edit after the upload changes the doc`() = runTest {
        seed("a")
        val before = kind.pending(ANN).single()
        kind.acknowledge(ANN, listOf(before))

        repository.upsert(draft("a", title = "Edited", updatedAtMs = 5_000L))

        val after = kind.pending(ANN).single()
        assertEquals("Edited", after.fields["name"])
        assertTrue(before.fields != after.fields)
    }

    @Test
    fun `an edit to a pending draft changes its stamp and an acknowledgement of another does not`() = runTest {
        seed("a")
        seed("b")
        val first = kind.pendingStamps(ANN).first()
        assertEquals(setOf("a", "b"), first.keys)

        repository.upsert(draft("a", title = "Edited", updatedAtMs = 5_000L))
        val edited = kind.pendingStamps(ANN).first()
        assertEquals(setOf("a", "b"), edited.keys)
        assertTrue("the edited draft's stamp changed", edited.getValue("a") != first.getValue("a"))
        assertEquals("the untouched draft's stamp did not", first.getValue("b"), edited.getValue("b"))

        kind.acknowledge(ANN, kind.pending(ANN).filter { it.id == "b" })
        assertEquals(mapOf("a" to edited.getValue("a")), kind.pendingStamps(ANN).first())
    }

    private fun draft(id: String, title: String, updatedAtMs: Long) = Draft(
        id = id,
        gameId = GameId("queens"),
        variant = VariantId("queens/n8"),
        draftFormatVersion = 1,
        blob = byteArrayOf(1, 2, 3),
        title = title,
        updatedAt = Instant.ofEpochMilli(updatedAtMs),
        testSolve = null,
        reservedCode = null,
        imported = false,
        authorName = "",
        note = "",
    )

    private fun entity(
        id: String,
        owner: String?,
        updatedAtMs: Long,
        fingerprint: String?,
        blob: ByteArray = byteArrayOf(1, 2, 3),
        reservedCode: String? = null,
        authorName: String = "",
        note: String = "",
    ) = DraftEntity(
        id = id,
        gameId = "queens",
        variant = "queens/n8",
        draftFormatVersion = 1,
        blob = blob,
        title = "Draft $id",
        updatedAt = updatedAtMs,
        testLayoutKey = null,
        testTimeMs = null,
        testHints = null,
        reservedCode = reservedCode,
        imported = false,
        authorName = authorName,
        note = note,
        ownerUid = owner,
        uploadedFingerprint = fingerprint,
    )

    private companion object {
        const val ANN = "uid-ann"
        const val BOB = "uid-bob"
        const val NOW_MS = 100_000L
    }
}
