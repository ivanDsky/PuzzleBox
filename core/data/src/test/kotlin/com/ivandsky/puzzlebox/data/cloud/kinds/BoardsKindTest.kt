package com.ivandsky.puzzlebox.data.cloud.kinds

import androidx.room.Room
import app.cash.turbine.test
import com.ivandsky.puzzlebox.data.cloud.CloudDoc
import com.ivandsky.puzzlebox.data.linkedin.LinkState
import com.ivandsky.puzzlebox.data.linkedin.LinkedInStoreLock
import com.ivandsky.puzzlebox.database.linkedin.FetchLogEntity
import com.ivandsky.puzzlebox.database.linkedin.GhostEntity
import com.ivandsky.puzzlebox.database.linkedin.LinkedInDatabase
import com.ivandsky.puzzlebox.database.linkedin.SavedBoardEntity
import com.ivandsky.puzzlebox.testing.linkedin.FakeLinkedInLink
import java.security.MessageDigest
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** [BoardsKind] over real in-memory Room: the encoded keys, the restore, upload markers and the store lock (ADR-0029). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BoardsKindTest {

    private lateinit var db: LinkedInDatabase
    private lateinit var kind: BoardsKind
    private val lock = LinkedInStoreLock()
    private val link = FakeLinkedInLink(LinkState.NotConnected)

    @Before
    fun setUp() {
        db = newDb()
        kind = BoardsKind(db, lock, link)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun newDb() = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), LinkedInDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    private fun keyOf(profileId: String) =
        MessageDigest.getInstance("SHA-256").digest(profileId.toByteArray()).joinToString("") { "%02x".format(it) }.take(16)

    private suspend fun seed(
        day: String = DAY,
        profileId: String = ME,
        owner: String? = ANN,
        readAtMs: Long = 5_000L,
        complete: Boolean = true,
        fingerprint: String? = null,
        game: String = "queens",
    ) {
        db.savedBoardDao().upsert(SavedBoardEntity(game, day, profileId, owner, readAtMs, complete, fingerprint))
        db.ghostDao().replace(
            game,
            day,
            listOf(
                ghost(game, day, profileId, "Ann Li", 61_000L),
                ghost(game, day, "m1", "Bea Ox", 75_000L),
                ghost(game, day, "m2", "Cy Dunn", null, hintFree = false),
            ),
        )
    }

    private fun ghost(game: String, day: String, ref: String, name: String, timeMs: Long?, hintFree: Boolean = true) =
        GhostEntity(game, day, ref, name, timeMs, hintFree, flawless = ref == "m1", fetchedAtMs = 5_000L)

    private fun doc(
        day: String = DAY,
        profileId: String = ME,
        readAtMs: Long = 9_000L,
        complete: Boolean = true,
        rows: List<Map<String, Any?>> = listOf(
            mapOf("name" to "Ann Li", "timeMs" to 61_000L, "hintFree" to true, "flawless" to false, "isOwn" to true),
            mapOf("name" to "Bea Ox", "timeMs" to 70_000L, "hintFree" to true, "flawless" to false, "isOwn" to false),
        ),
    ): CloudDoc {
        val key = keyOf(profileId)
        return CloudDoc(
            "queens_${day}_$key",
            mapOf(
                "formatVersion" to 1L, "game" to "queens", "day" to day, "profileKey" to key,
                "readAtMs" to readAtMs, "complete" to complete, "rows" to rows,
            ),
        )
    }

    @Test
    fun `an encoded board carries only allow-listed keys and no id`() = runTest {
        seed()
        val docs = kind.pending(ANN)

        assertEquals(1, docs.size)
        val doc = docs.single()
        assertEquals("queens_${DAY}_${keyOf(ME)}", doc.id)
        assertTrue(BoardDocKeys.DOC.containsAll(doc.fields.keys))
        assertEquals(BoardDocKeys.DOC, doc.fields.keys)
        val rows = doc.fields["rows"] as List<*>
        assertEquals(3, rows.size)
        rows.forEach { row -> assertTrue(BoardDocKeys.ROW.containsAll((row as Map<*, *>).keys)) }
        assertEquals(1L, doc.fields["formatVersion"])
        assertEquals(5_000L, doc.fields["readAtMs"])
        assertEquals(true, doc.fields["complete"])
        val own = rows.map { it as Map<*, *> }.single { it["isOwn"] == true }
        assertEquals("Ann Li", own["name"])
        val hidden = rows.map { it as Map<*, *> }.single { it["name"] == "Cy Dunn" }
        assertFalse("a hidden time has no key, never a zero", hidden.containsKey("timeMs"))
        val text = doc.toString()
        for (secret in listOf(ME, "m1", "m2", "li_at")) assertFalse("doc names $secret", text.contains(secret))
    }

    @Test
    fun `only this account's boards without an acknowledged upload are pending`() = runTest {
        seed(day = "2026-11-01", owner = ANN)
        seed(day = "2026-11-02", owner = BEA)
        seed(day = "2026-11-03", owner = null)
        seed(day = "2026-11-04", owner = ANN, fingerprint = "done")

        assertEquals(listOf("queens_2026-11-01_${keyOf(ME)}"), kind.pending(ANN).map { it.id })
        kind.pendingIds(ANN).test {
            assertEquals(setOf("queens_2026-11-01_${keyOf(ME)}"), awaitItem())
            kind.adopt(ANN)
            assertEquals(
                setOf("queens_2026-11-01_${keyOf(ME)}", "queens_2026-11-03_${keyOf(ME)}"),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an acknowledgement ends the pending state and forget brings it back`() = runTest {
        seed()
        val sent = kind.pending(ANN)

        kind.acknowledge(ANN, sent)
        assertTrue(kind.pending(ANN).isEmpty())
        assertNotNull(db.savedBoardDao().get("queens", DAY)!!.uploadedFingerprint)

        kind.forget(ANN)
        assertEquals(sent, kind.pending(ANN))
    }

    @Test
    fun `a board read again during the upload stays pending after the acknowledgement`() = runTest {
        seed(readAtMs = 5_000L)
        val sent = kind.pending(ANN)
        // The writer replaces the board and clears the marker while the upload is in flight.
        seed(readAtMs = 6_000L)

        kind.acknowledge(ANN, sent)

        assertEquals(1, kind.pending(ANN).size)
        assertNull(db.savedBoardDao().get("queens", DAY)!!.uploadedFingerprint)
    }

    @Test
    fun `adopt gives ownerless boards to the account and leaves another account's`() = runTest {
        seed(day = "2026-11-01", owner = null)
        seed(day = "2026-11-02", owner = BEA)

        kind.adopt(ANN)

        assertEquals(ANN, db.savedBoardDao().get("queens", "2026-11-01")!!.ownerUid)
        assertEquals(BEA, db.savedBoardDao().get("queens", "2026-11-02")!!.ownerUid)
    }

    @Test
    fun `a restored board shows rows and own row and is acknowledged`() = runTest {
        val restored = kind.apply(ANN, listOf(doc()))

        assertEquals(1, restored)
        val board = db.savedBoardDao().get("queens", DAY)!!
        assertEquals(ANN, board.ownerUid)
        assertEquals(9_000L, board.readAtMs)
        assertTrue(board.complete)
        assertNotNull("the cloud already holds it", board.uploadedFingerprint)
        val rows = db.ghostDao().board("queens", DAY)
        assertEquals(setOf("Ann Li", "Bea Ox"), rows.map { it.name }.toSet())
        assertEquals(70_000L, rows.single { it.name == "Bea Ox" }.timeMs)
        assertEquals(board.profileId, rows.single { it.name == "Ann Li" }.memberRef)
        assertTrue(kind.pending(ANN).isEmpty())
    }

    @Test
    fun `a board survives the trip from one device to another`() = runTest {
        seed()
        val sent = kind.pending(ANN)
        val other = newDb()
        try {
            val there = BoardsKind(other, lock, FakeLinkedInLink(LinkState.NotConnected))
            assertEquals(1, there.apply(ANN, sent))

            val back = there.pending(ANN)
            assertTrue("a restored board is not pending", back.isEmpty())
            there.forget(ANN)
            val again = there.pending(ANN).single()
            assertEquals(sent.single().id, again.id)
            assertEquals(sent.single().fields["rows"], again.fields["rows"])
        } finally {
            other.close()
        }
    }

    @Test
    fun `restoring marks the day read for the linked profile`() = runTest {
        link.connect(ME, 1L)
        kind.apply(ANN, listOf(doc(complete = true), doc(day = "2026-11-09", complete = false)))

        val complete = db.fetchLogDao().get("queens", DAY, "Board", ME)!!
        assertEquals("Board", complete.outcome)
        assertEquals(9_000L, complete.fetchedAtMs)
        assertEquals("BoardToday", db.fetchLogDao().get("queens", "2026-11-09", "Board", ME)!!.outcome)
        assertEquals(ME, db.savedBoardDao().get("queens", DAY)!!.profileId)
    }

    @Test
    fun `restoring a board of another profile marks nothing and an older mark is replaced`() = runTest {
        link.connect("someone-else", 1L)
        kind.apply(ANN, listOf(doc()))
        assertNull(db.fetchLogDao().get("queens", DAY, "Board", "someone-else"))
        assertNull(db.fetchLogDao().get("queens", DAY, "Board", ME))

        link.connect(ME, 1L)
        db.fetchLogDao().put(FetchLogEntity("queens", "2026-11-08", "Board", ME, 1_000L, "BoardToday"))
        kind.apply(ANN, listOf(doc(day = "2026-11-08", readAtMs = 3_000L)))
        assertEquals("Board", db.fetchLogDao().get("queens", "2026-11-08", "Board", ME)!!.outcome)
    }

    @Test
    fun `a newer fetch mark is not replaced`() = runTest {
        link.connect(ME, 1L)
        db.fetchLogDao().put(FetchLogEntity("queens", DAY, "Board", ME, 20_000L, "BoardToday"))

        kind.apply(ANN, listOf(doc(readAtMs = 9_000L)))

        assertEquals(20_000L, db.fetchLogDao().get("queens", DAY, "Board", ME)!!.fetchedAtMs)
    }

    @Test
    fun `a partial board never replaces a complete one, a newer read replaces an older`() = runTest {
        seed(readAtMs = 5_000L, complete = true)
        assertEquals(0, kind.apply(ANN, listOf(doc(readAtMs = 9_000L, complete = false))))
        assertEquals(5_000L, db.savedBoardDao().get("queens", DAY)!!.readAtMs)
        assertEquals(3, db.ghostDao().board("queens", DAY).size)

        assertEquals(1, kind.apply(ANN, listOf(doc(readAtMs = 9_000L, complete = true))))
        assertEquals(9_000L, db.savedBoardDao().get("queens", DAY)!!.readAtMs)
        assertEquals(2, db.ghostDao().board("queens", DAY).size)
    }

    @Test
    fun `applying the same docs again restores nothing`() = runTest {
        val docs = listOf(doc(), doc(day = "2026-11-09"))
        assertEquals(2, kind.apply(ANN, docs))
        assertEquals(0, kind.apply(ANN, docs))
        assertEquals(0, kind.apply(ANN, docs.reversed()))
    }

    @Test
    fun `one board per day, the linked profile's first, else the latest read`() = runTest {
        val mine = doc(profileId = ME, readAtMs = 1_000L)
        val theirs = doc(profileId = "other", readAtMs = 9_000L)

        kind.apply(ANN, listOf(mine, theirs))
        assertEquals(9_000L, db.savedBoardDao().get("queens", DAY)!!.readAtMs)

        link.connect(ME, 1L)
        assertEquals(1, kind.apply(ANN, listOf(mine, theirs)))
        assertEquals(1_000L, db.savedBoardDao().get("queens", DAY)!!.readAtMs)
        assertEquals(ME, db.savedBoardDao().get("queens", DAY)!!.profileId)
        assertEquals(0, kind.apply(ANN, listOf(theirs)))
    }

    @Test
    fun `another account's board and unusable docs are left alone`() = runTest {
        seed(owner = BEA, readAtMs = 1_000L, complete = false)
        val junk = listOf(
            CloudDoc("queens_${DAY}_wrong", doc().fields),
            CloudDoc("x", mapOf("game" to "queens")),
            doc(day = "2026-13-45").let { it.copy(id = "queens_2026-13-45_${keyOf(ME)}") },
            doc().let { it.copy(fields = it.fields + ("rows" to "nope")) },
            doc().let { it.copy(fields = it.fields + ("complete" to "yes")) },
        )

        assertEquals(0, kind.apply(ANN, listOf(doc()) + junk))

        val board = db.savedBoardDao().get("queens", DAY)!!
        assertEquals(BEA, board.ownerUid)
        assertEquals(1_000L, board.readAtMs)
        assertEquals(3, db.ghostDao().board("queens", DAY).size)
        assertEquals(0, kind.apply(ANN, junk))
    }

    @Test
    fun `restored connections get stable apart refs and a day's own row is told apart`() = runTest {
        val twins = listOf(
            mapOf("name" to "Twin", "timeMs" to 1_000L, "hintFree" to true, "flawless" to false, "isOwn" to false),
            mapOf("name" to "Twin", "timeMs" to 2_000L, "hintFree" to true, "flawless" to false, "isOwn" to false),
            mapOf("name" to "Me", "timeMs" to 3_000L, "hintFree" to true, "flawless" to false, "isOwn" to true),
            mapOf("name" to "Me2", "timeMs" to 4_000L, "hintFree" to true, "flawless" to false, "isOwn" to true),
        )

        kind.apply(ANN, listOf(doc(rows = twins), doc(day = "2026-11-09", rows = twins.take(1))))

        val rows = db.ghostDao().board("queens", DAY)
        assertEquals(4, rows.map { it.memberRef }.toSet().size)
        val board = db.savedBoardDao().get("queens", DAY)!!
        assertEquals(listOf("Me"), rows.filter { it.memberRef == board.profileId }.map { it.name })
        val twinRef = rows.first { it.name == "Twin" && it.timeMs == 1_000L }.memberRef
        assertEquals(twinRef, db.ghostDao().board("queens", "2026-11-09").single().memberRef)
    }

    @Test
    fun `a restored board keeps its profile key when it uploads again`() = runTest {
        kind.apply(ANN, listOf(doc()))
        kind.forget(ANN)

        assertEquals(doc().id, kind.pending(ANN).single().id)
    }

    @Test
    fun `apply waits for the store lock and never takes it twice`() = runBlocking {
        val executor = Executors.newSingleThreadExecutor { Thread(it, "boards-test").apply { isDaemon = true } }
        val scope = CoroutineScope(executor.asCoroutineDispatcher() + SupervisorJob())
        try {
            lateinit var restored: Deferred<Int>
            lock.withLock {
                restored = scope.async { kind.apply(ANN, listOf(doc())) }
                Thread.sleep(HOLD_MS)
                assertFalse("apply ran under a held lock", restored.isCompleted)
                assertNull(db.savedBoardDao().get("queens", DAY))
            }
            assertEquals(1, restored.await())
            assertNotNull(db.savedBoardDao().get("queens", DAY))
        } finally {
            scope.cancel()
            executor.shutdownNow()
        }
    }

    private companion object {
        const val ANN = "ann"
        const val BEA = "bea"
        const val ME = "ACoAA-me"
        const val DAY = "2026-11-10"
        const val HOLD_MS = 300L
    }
}
