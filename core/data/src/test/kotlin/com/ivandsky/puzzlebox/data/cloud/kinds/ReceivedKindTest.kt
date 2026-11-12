package com.ivandsky.puzzlebox.data.cloud.kinds

import androidx.room.Room
import app.cash.turbine.test
import com.ivandsky.puzzlebox.data.cloud.CloudDoc
import com.ivandsky.puzzlebox.database.PuzzleBoxDatabase
import com.ivandsky.puzzlebox.database.ReceivedPuzzleEntity
import com.ivandsky.puzzlebox.database.SyncTombstoneEntity
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
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

/** [ReceivedKind] over real in-memory Room: the doc codec, the merge table, tombstones and upload markers (ADR-0029). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReceivedKindTest {

    private lateinit var db: PuzzleBoxDatabase
    private lateinit var kind: ReceivedKind
    private var nowMs = NOW_MS

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), PuzzleBoxDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val clock = object : Clock() {
            override fun getZone() = ZoneOffset.UTC
            override fun withZone(zone: java.time.ZoneId?) = this
            override fun instant(): Instant = Instant.ofEpochMilli(nowMs)
        }
        kind = ReceivedKind(db, db.receivedPuzzleDao(), db.syncTombstoneDao(), clock)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun seed(
        code: String,
        owner: String? = ANN,
        openedAt: Long = 1_000L,
        postState: String = "Pending",
        fingerprint: String? = null,
        bytes: ByteArray = byteArrayOf(1, 2, 3, -1, 0, 127),
        title: String = "Morning crown",
    ) = db.receivedPuzzleDao().upsert(
        ReceivedPuzzleEntity(
            code = code,
            gameId = "queens",
            variant = "7x7",
            sharedFormatVersion = 2,
            puzzleBytes = bytes,
            title = title,
            authorName = "Bea",
            note = "have fun",
            authorTimeMs = 61_000L,
            authorHints = 1,
            authorUid = "uid-bea",
            openedAt = openedAt,
            unpublished = false,
            postState = postState,
            postAttempts = 3,
            difficulty = "Hard",
            ownerUid = owner,
            uploadedFingerprint = fingerprint,
        ),
    )

    private suspend fun row(code: String): ReceivedPuzzleEntity? = db.receivedPuzzleDao().find(code)

    private fun live(
        code: String,
        postState: String = "Pending",
        receivedAtMs: Long = 2_000L,
        title: String = "Cloud title",
        bytes: ByteArray = byteArrayOf(9, 8, 7),
        extra: Map<String, Any?> = emptyMap(),
    ) = CloudDoc(
        code,
        mapOf(
            "formatVersion" to 1L,
            "gameId" to "queens",
            "variant" to "5x5",
            "sharedFormatVersion" to 3L,
            "name" to title,
            "senderName" to "Cy",
            "authorUid" to "uid-cy",
            "note" to "hi",
            "authorTimeMs" to 42_000L,
            "authorHints" to 2L,
            "difficulty" to "Easy",
            "receivedAtMs" to receivedAtMs,
            "bytes" to bytes,
            "postState" to postState,
            "deleted" to false,
        ) + extra,
        updatedAtMs = 5L,
    )

    private fun gone(code: String, deletedAtMs: Long) =
        CloudDoc(code, mapOf("formatVersion" to 1L, "deleted" to true, "deletedAtMs" to deletedAtMs))

    private suspend fun tombstones(uid: String = ANN) =
        db.syncTombstoneDao().pendingEligible(uid, Long.MAX_VALUE)

    @Test
    fun `the kind is received`() {
        assertEquals("received", kind.kind)
    }

    @Test
    fun `a pending doc carries exactly the pinned keys and the row's values`() = runTest {
        seed("AAA111", openedAt = 1_500L, postState = "Posted")

        val doc = kind.pending(ANN).single()

        assertEquals("AAA111", doc.id)
        assertEquals(
            mapOf<String, Any?>(
                "formatVersion" to 1L,
                "gameId" to "queens",
                "variant" to "7x7",
                "sharedFormatVersion" to 2L,
                "name" to "Morning crown",
                "senderName" to "Bea",
                "authorUid" to "uid-bea",
                "note" to "have fun",
                "authorTimeMs" to 61_000L,
                "authorHints" to 1L,
                "difficulty" to "Hard",
                "receivedAtMs" to 1_500L,
                "postState" to "Posted",
                "deleted" to false,
            ),
            doc.fields - "bytes",
        )
    }

    @Test
    fun `a row without difficulty leaves the key out`() = runTest {
        db.receivedPuzzleDao().upsert(entityWithoutDifficulty())

        assertTrue("difficulty" !in kind.pending(ANN).single().fields)
    }

    private fun entityWithoutDifficulty() = ReceivedPuzzleEntity(
        "AAA111", "queens", "7x7", 2, byteArrayOf(1), "t", "a", "", 1L, 0, "u", 1L, false, "Pending", 0, null, ANN,
    )

    @Test
    fun `payload bytes round trip unchanged through a doc`() = runTest {
        val bytes = ByteArray(300) { (it * 7 - 100).toByte() }
        seed("AAA111", bytes = bytes)
        val doc = kind.pending(ANN).single()
        assertArrayEquals(bytes, doc.fields["bytes"] as ByteArray)
        db.receivedPuzzleDao().delete("AAA111")

        val restored = kind.apply(ANN, listOf(doc))

        assertEquals(1, restored)
        val back = row("AAA111")!!
        assertArrayEquals(bytes, back.puzzleBytes)
        assertEquals("queens", back.gameId)
        assertEquals("7x7", back.variant)
        assertEquals(2, back.sharedFormatVersion)
        assertEquals("Morning crown", back.title)
        assertEquals("Bea", back.authorName)
        assertEquals("have fun", back.note)
        assertEquals(61_000L, back.authorTimeMs)
        assertEquals(1, back.authorHints)
        assertEquals("uid-bea", back.authorUid)
        assertEquals(1_000L, back.openedAt)
        assertEquals("Hard", back.difficulty)
    }

    @Test
    fun `an unknown code inserts owned by the uid with the cloud post state`() = runTest {
        val restored = kind.apply(ANN, listOf(live("AAA111", postState = "Posted")))

        assertEquals(1, restored)
        val inserted = row("AAA111")!!
        assertEquals(ANN, inserted.ownerUid)
        assertEquals("Posted", inserted.postState)
        assertEquals(0, inserted.postAttempts)
        assertEquals(false, inserted.unpublished)
        assertEquals("Cloud title", inserted.title)
        assertEquals("Easy", inserted.difficulty)
        assertEquals(2_000L, inserted.openedAt)
    }

    @Test
    fun `a restored row is not uploaded again`() = runTest {
        kind.apply(ANN, listOf(live("AAA111", postState = "Posted")))

        assertEquals(emptyList<CloudDoc>(), kind.pending(ANN))
    }

    @Test
    fun `a restored Posted row stays Posted when the cloud says Pending`() = runTest {
        seed("AAA111", postState = "Posted", fingerprint = "Posted")

        val restored = kind.apply(ANN, listOf(live("AAA111", postState = "Pending")))

        assertEquals(0, restored)
        assertEquals("Posted", row("AAA111")!!.postState)
    }

    @Test
    fun `a post state that moved forward replaces the local one and keeps the attempts`() = runTest {
        seed("AAA111", postState = "Pending")
        seed("BBB222", postState = "Pending")
        seed("CCC333", postState = "LocalOnly")

        val restored = kind.apply(
            ANN,
            listOf(live("AAA111", postState = "Posted"), live("BBB222", postState = "LocalOnly"),
                live("CCC333", postState = "Posted")),
        )

        assertEquals(0, restored)
        assertEquals("Posted", row("AAA111")!!.postState)
        assertEquals("LocalOnly", row("BBB222")!!.postState)
        assertEquals("Posted", row("CCC333")!!.postState)
        assertEquals(3, row("AAA111")!!.postAttempts)
        assertEquals(emptyList<CloudDoc>(), kind.pending(ANN))
    }

    @Test
    fun `a known code keeps every other local value`() = runTest {
        seed("AAA111", title = "Mine", bytes = byteArrayOf(5), openedAt = 777L)

        kind.apply(ANN, listOf(live("AAA111", postState = "Posted", title = "Theirs", bytes = byteArrayOf(6))))

        val kept = row("AAA111")!!
        assertEquals("Mine", kept.title)
        assertArrayEquals(byteArrayOf(5), kept.puzzleBytes)
        assertEquals(777L, kept.openedAt)
        assertEquals("7x7", kept.variant)
    }

    @Test
    fun `a cloud tombstone deletes the local row without leaving a tombstone`() = runTest {
        seed("AAA111", openedAt = 1_000L)

        val restored = kind.apply(ANN, listOf(gone("AAA111", deletedAtMs = 1_000L)))

        assertEquals(0, restored)
        assertNull(row("AAA111"))
        assertEquals(emptyList<SyncTombstoneEntity>(), tombstones())
    }

    @Test
    fun `a tombstone for an unknown code changes nothing`() = runTest {
        assertEquals(0, kind.apply(ANN, listOf(gone("AAA111", 9L))))
        assertNull(row("AAA111"))
    }

    @Test
    fun `a row opened again after the cloud delete survives the tombstone`() = runTest {
        seed("AAA111", openedAt = 5_000L)

        kind.apply(ANN, listOf(gone("AAA111", deletedAtMs = 4_000L)))

        assertNotNull(row("AAA111"))
    }

    @Test
    fun `another account's row is never touched by a doc`() = runTest {
        seed("AAA111", owner = BOB, postState = "Pending", title = "Bob's")

        val restored = kind.apply(ANN, listOf(live("AAA111", postState = "Posted"), gone("AAA111", 9_000L)))

        assertEquals(0, restored)
        val kept = row("AAA111")!!
        assertEquals(BOB, kept.ownerUid)
        assertEquals("Pending", kept.postState)
        assertEquals("Bob's", kept.title)
    }

    @Test
    fun `a local tombstone wins over an older cloud doc`() = runTest {
        seed("AAA111", openedAt = 1_000L)
        nowMs = 3_000L
        db.receivedPuzzleDao().deleteWithTombstone("AAA111", nowMs)

        val restored = kind.apply(ANN, listOf(live("AAA111", receivedAtMs = 2_000L)))

        assertEquals(0, restored)
        assertNull(row("AAA111"))
        assertEquals(1, tombstones().size)
    }

    @Test
    fun `a cloud doc received after the local delete restores the row and drops the tombstone`() = runTest {
        seed("AAA111", openedAt = 1_000L)
        db.receivedPuzzleDao().deleteWithTombstone("AAA111", 3_000L)

        val restored = kind.apply(ANN, listOf(live("AAA111", receivedAtMs = 4_000L)))

        assertEquals(1, restored)
        assertNotNull(row("AAA111"))
        assertEquals(emptyList<SyncTombstoneEntity>(), tombstones())
    }

    @Test
    fun `docs that cannot be opened are skipped and the rest still merge`() = runTest {
        val noVariant = live("AAA111").let { it.copy(fields = it.fields - "variant") }
        val noBytes = live("BBB222").let { it.copy(fields = it.fields - "bytes") }
        val badCode = live("not a code")

        val restored = kind.apply(ANN, listOf(noVariant, noBytes, badCode, live("CCC333")))

        assertEquals(1, restored)
        assertEquals(listOf("CCC333"), db.receivedPuzzleDao().observeAll(null).first().map { it.code })
    }

    @Test
    fun `new rows are pending until acknowledged and a post state change makes them pending again`() = runTest {
        seed("AAA111")
        seed("BBB222", owner = BOB)

        val docs = kind.pending(ANN)
        assertEquals(listOf("AAA111"), docs.map { it.id })

        kind.acknowledge(ANN, docs)
        assertEquals(emptyList<CloudDoc>(), kind.pending(ANN))

        db.receivedPuzzleDao().updatePost("AAA111", "Posted", 1)
        assertEquals(listOf("Posted"), kind.pending(ANN).map { it.fields["postState"] })
    }

    @Test
    fun `an acknowledge for an old version leaves the newer one pending`() = runTest {
        seed("AAA111")
        val sent = kind.pending(ANN)
        db.receivedPuzzleDao().updatePost("AAA111", "Posted", 1)

        kind.acknowledge(ANN, sent)

        assertEquals(listOf("AAA111"), kind.pending(ANN).map { it.id })
    }

    @Test
    fun `a delete is pending as a tombstone at once and acknowledged by marking it uploaded`() = runTest {
        seed("AAA111")
        nowMs = 9_000L
        db.receivedPuzzleDao().deleteWithTombstone("AAA111", nowMs)

        val docs = kind.pending(ANN)

        assertEquals(1, docs.size)
        assertEquals("AAA111", docs.single().id)
        assertEquals(
            mapOf<String, Any?>("formatVersion" to 1L, "deleted" to true, "deletedAtMs" to 9_000L),
            docs.single().fields,
        )
        kind.acknowledge(ANN, docs)
        assertEquals(emptyList<CloudDoc>(), kind.pending(ANN))
    }

    @Test
    fun `a tombstone of a code opened again is dropped instead of uploaded`() = runTest {
        seed("AAA111")
        db.receivedPuzzleDao().deleteWithTombstone("AAA111", 2_000L)
        seed("AAA111", openedAt = 3_000L)

        val docs = kind.pending(ANN)

        assertEquals(listOf(false), docs.map { it.fields["deleted"] })
        assertEquals(emptyList<SyncTombstoneEntity>(), tombstones())
    }

    @Test
    fun `adopt claims the ownerless rows only`() = runTest {
        seed("AAA111", owner = null)
        seed("BBB222", owner = BOB)

        kind.adopt(ANN)

        assertEquals(ANN, row("AAA111")!!.ownerUid)
        assertEquals(BOB, row("BBB222")!!.ownerUid)
        assertEquals(listOf("AAA111"), kind.pending(ANN).map { it.id })
    }

    @Test
    fun `forget makes every row and tombstone of the uid pending again`() = runTest {
        seed("AAA111", fingerprint = "Pending")
        seed("BBB222")
        db.receivedPuzzleDao().deleteWithTombstone("BBB222", 2_000L)
        kind.acknowledge(ANN, kind.pending(ANN))
        assertEquals(emptyList<CloudDoc>(), kind.pending(ANN))

        kind.forget(ANN)

        assertEquals(setOf("AAA111", "BBB222"), kind.pending(ANN).map { it.id }.toSet())
    }

    @Test
    fun `pending ids follow the rows and tombstones that are not acknowledged`() = runTest {
        kind.pendingIds(ANN).test {
            assertEquals(emptySet<String>(), awaitItem())

            seed("AAA111")
            assertEquals(setOf("AAA111"), awaitItem())

            kind.acknowledge(ANN, kind.pending(ANN))
            assertEquals(emptySet<String>(), awaitItem())

            db.receivedPuzzleDao().deleteWithTombstone("AAA111", 2_000L)
            assertEquals(setOf("AAA111"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    private companion object {
        const val ANN = "uid-ann"
        const val BOB = "uid-bob"
        const val NOW_MS = 10_000L
    }
}
