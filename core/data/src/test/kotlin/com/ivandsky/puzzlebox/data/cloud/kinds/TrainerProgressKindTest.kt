package com.ivandsky.puzzlebox.data.cloud.kinds

import androidx.room.Room
import com.ivandsky.puzzlebox.data.cloud.CloudDoc
import com.ivandsky.puzzlebox.database.PuzzleBoxDatabase
import com.ivandsky.puzzlebox.database.TrainerProgressEntity
import com.ivandsky.puzzlebox.testing.room.RoomQueryThread
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Trainer progress as a synced kind over in-memory Room: the doc shape, when a game is pending, the merge on receive,
 * that bad cloud values are skipped, and that signing in folds a signed-out row into the account's.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrainerProgressKindTest {

    private lateinit var db: PuzzleBoxDatabase
    private lateinit var kind: TrainerProgressKind

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), PuzzleBoxDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(RoomQueryThread.executor)
            .build()
        kind = TrainerProgressKind(db, db.trainerProgressDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private val dao get() = db.trainerProgressDao()

    /** One stored row, written as is: the flag is the caller's, as if the sync had already run. */
    private suspend fun store(
        owner: String?,
        technique: String,
        mark: String = "practicing",
        inRow: Int = 1,
        at: Long = 1_000L,
        uploaded: Boolean = false,
        game: String = "queens",
    ) {
        dao.insert(TrainerProgressEntity(0, owner, game, technique, mark, inRow, at, uploaded))
    }

    private suspend fun stored(owner: String?, technique: String, game: String = "queens") = dao.find(game, technique, owner)

    /** A progress map as a doc carries one technique. */
    private fun value(mark: Any?, inRow: Any?, at: Any?) = mapOf("mark" to mark, "inRow" to inRow, "answeredAtMs" to at)

    private fun doc(game: String, vararg techniques: Pair<String, Any?>, formatVersion: Any? = 1L) =
        CloudDoc(game, mapOf("formatVersion" to formatVersion, "techniques" to mapOf(*techniques)))

    @Test
    fun `a pending game is one doc with literal marks, numbers as Long and a technique still new left out`() = runTest {
        store("ann", "line-lock", mark = "practicing", inRow = 2, at = 5_000L)
        store("ann", "pairs", mark = "mastered", inRow = 5, at = 6_000L)
        store("ann", "fresh", mark = "new", inRow = 0, at = 7_000L)

        assertEquals(
            listOf(
                CloudDoc(
                    "queens",
                    mapOf(
                        "formatVersion" to 1L,
                        "techniques" to mapOf(
                            "line-lock" to mapOf("mark" to "practicing", "inRow" to 2L, "answeredAtMs" to 5_000L),
                            "pairs" to mapOf("mark" to "mastered", "inRow" to 5L, "answeredAtMs" to 6_000L),
                        ),
                    ),
                ),
            ),
            kind.pending("ann"),
        )
    }

    @Test
    fun `the doc carries the whole game, the techniques already uploaded included`() = runTest {
        store("ann", "line-lock", uploaded = true)
        store("ann", "pairs", uploaded = false)
        store("ann", "solo", game = "tango", uploaded = true)

        val docs = kind.pending("ann")

        assertEquals(listOf("queens"), docs.map { it.id })
        assertEquals(setOf("line-lock", "pairs"), (docs.single().fields["techniques"] as Map<*, *>).keys)
        assertEquals(setOf("queens"), kind.pendingIds("ann").first())
    }

    @Test
    fun `a row another account or nobody holds is never pending for this account`() = runTest {
        store("bob", "line-lock")
        store(null, "pairs")

        assertTrue(kind.pending("ann").isEmpty())
        assertTrue(kind.pendingIds("ann").first().isEmpty())
    }

    @Test
    fun `an answer that changes only its time leaves an uploaded game uploaded, a count change makes it pending`() = runTest {
        store("ann", "line-lock", inRow = 2, at = 1_000L, uploaded = true)

        dao.upsert(TrainerProgressEntity(0, "ann", "queens", "line-lock", "practicing", 2, 9_000L))
        assertTrue(kind.pendingIds("ann").first().isEmpty())

        dao.upsert(TrainerProgressEntity(0, "ann", "queens", "line-lock", "practicing", 0, 9_500L))
        assertEquals(setOf("queens"), kind.pendingIds("ann").first())
    }

    @Test
    fun `a received technique with the higher mark wins, a lower one never lowers the local mark`() = runTest {
        store("ann", "line-lock", mark = "practicing", inRow = 3, at = 5_000L, uploaded = true)
        store("ann", "pairs", mark = "mastered", inRow = 5, at = 5_000L, uploaded = true)

        val restored = kind.apply(
            "ann",
            listOf(
                doc(
                    "queens",
                    "line-lock" to value("mastered", 5L, 4_000L),
                    "pairs" to value("practicing", 1L, 9_000L),
                ),
            ),
        )

        assertEquals("mastered", stored("ann", "line-lock")!!.mark)
        assertEquals("mastered", stored("ann", "pairs")!!.mark)
        assertEquals(2, restored)
    }

    @Test
    fun `a received technique's count follows the later answer, whichever side it came from`() = runTest {
        store("ann", "older-here", inRow = 4, at = 1_000L, uploaded = true)
        store("ann", "newer-here", inRow = 1, at = 9_000L, uploaded = true)

        kind.apply(
            "ann",
            listOf(doc("queens", "older-here" to value("practicing", 1L, 2_000L), "newer-here" to value("practicing", 3L, 3_000L))),
        )

        assertEquals(1 to 2_000L, stored("ann", "older-here")!!.let { it.inRow to it.answeredAtMs })
        assertEquals(1 to 9_000L, stored("ann", "newer-here")!!.let { it.inRow to it.answeredAtMs })
    }

    @Test
    fun `a technique the cloud has not seen yet is inserted as uploaded and counted`() = runTest {
        val restored = kind.apply("ann", listOf(doc("queens", "line-lock" to value("practicing", 2L, 4_000L))))

        assertEquals(1, restored)
        val row = stored("ann", "line-lock")!!
        assertEquals(Triple("practicing", 2, 4_000L), Triple(row.mark, row.inRow, row.answeredAtMs))
        assertTrue(row.uploaded)
        assertTrue(kind.pendingIds("ann").first().isEmpty())
    }

    @Test
    fun `a technique where the local value wins stays pending, one equal to the cloud's is uploaded`() = runTest {
        store("ann", "ahead", mark = "mastered", inRow = 5, at = 9_000L, uploaded = false)
        store("ann", "same", mark = "practicing", inRow = 2, at = 4_000L, uploaded = false)

        kind.apply(
            "ann",
            listOf(doc("queens", "ahead" to value("practicing", 1L, 1_000L), "same" to value("practicing", 2L, 4_000L))),
        )

        assertFalse(stored("ann", "ahead")!!.uploaded)
        assertTrue(stored("ann", "same")!!.uploaded)
        assertEquals(setOf("queens"), kind.pendingIds("ann").first())
    }

    @Test
    fun `a technique that was uploaded but the cloud now holds less of turns pending again`() = runTest {
        store("ann", "line-lock", mark = "mastered", inRow = 5, at = 9_000L, uploaded = true)

        kind.apply("ann", listOf(doc("queens", "line-lock" to value("practicing", 1L, 2_000L))))

        assertFalse(stored("ann", "line-lock")!!.uploaded)
    }

    @Test
    fun `a local technique the doc does not name is left alone`() = runTest {
        store("ann", "mine", mark = "practicing", inRow = 2, at = 1_000L, uploaded = false)

        kind.apply("ann", listOf(doc("queens", "theirs" to value("mastered", 5L, 2_000L))))

        val mine = stored("ann", "mine")!!
        assertEquals(Triple("practicing", 2, false), Triple(mine.mark, mine.inRow, mine.uploaded))
    }

    @Test
    fun `applying the same doc twice leaves the rows as after the first time`() = runTest {
        store("ann", "line-lock", mark = "practicing", inRow = 3, at = 5_000L)
        val received = listOf(doc("queens", "line-lock" to value("mastered", 5L, 4_000L), "pairs" to value("practicing", 1L, 2_000L)))

        kind.apply("ann", received)
        val once = dao.ownedRowsOfGame("ann", "queens")
        val again = kind.apply("ann", received)

        assertEquals(once, dao.ownedRowsOfGame("ann", "queens"))
        assertEquals(0, again)
    }

    @Test
    fun `two docs applied in either order end with the same rows`() = runTest {
        val first = doc("queens", "line-lock" to value("practicing", 2L, 3_000L), "pairs" to value("mastered", 5L, 1_000L))
        val second = doc("queens", "line-lock" to value("practicing", 4L, 8_000L), "pairs" to value("practicing", 1L, 9_000L))

        kind.apply("ann", listOf(first))
        kind.apply("ann", listOf(second))
        val forward = dao.ownedRowsOfGame("ann", "queens").map { Triple(it.techniqueId, it.mark, it.inRow to it.answeredAtMs) }
        db.clearAllTables()
        kind.apply("ann", listOf(second))
        kind.apply("ann", listOf(first))
        val backward = dao.ownedRowsOfGame("ann", "queens").map { Triple(it.techniqueId, it.mark, it.inRow to it.answeredAtMs) }

        assertEquals(forward, backward)
        assertEquals(listOf(Triple("line-lock", "practicing", 4 to 8_000L), Triple("pairs", "mastered", 1 to 9_000L)), forward)
    }

    @Test
    fun `a technique with an unknown mark, a missing or negative number or a non-map value is skipped alone`() = runTest {
        val restored = kind.apply(
            "ann",
            listOf(
                doc(
                    "queens",
                    "good" to value("practicing", 2L, 4_000L),
                    "unknown-mark" to value("grandmaster", 2L, 4_000L),
                    "new-mark" to value("new", 0L, 4_000L),
                    "no-mark" to value(null, 2L, 4_000L),
                    "no-count" to value("practicing", null, 4_000L),
                    "negative-count" to value("practicing", -1L, 4_000L),
                    "huge-count" to value("practicing", Int.MAX_VALUE + 1L, 4_000L),
                    "no-time" to value("practicing", 2L, null),
                    "negative-time" to value("practicing", 2L, -1L),
                    "fraction" to value("practicing", 2.5, 4_000L),
                    "int-count" to value("practicing", 2, 4_000L),
                    "not-a-map" to "practicing",
                    "Bad Key" to value("practicing", 2L, 4_000L),
                ),
            ),
        )

        assertEquals(1, restored)
        assertEquals(listOf("good"), dao.ownedRowsOfGame("ann", "queens").map { it.techniqueId })
    }

    @Test
    fun `a doc with no techniques map, another format version or a bad game id applies nothing and does not throw`() = runTest {
        val restored = kind.apply(
            "ann",
            listOf(
                CloudDoc("queens", mapOf("formatVersion" to 1L)),
                CloudDoc("queens", mapOf("formatVersion" to 1L, "techniques" to "practicing")),
                doc("queens", "line-lock" to value("practicing", 2L, 4_000L), formatVersion = 2L),
                doc("queens", "line-lock" to value("practicing", 2L, 4_000L), formatVersion = null),
                doc("Queens!", "line-lock" to value("practicing", 2L, 4_000L)),
                doc("", "line-lock" to value("practicing", 2L, 4_000L)),
            ),
        )

        assertEquals(0, restored)
        assertTrue(dao.ownedRowsOfGame("ann", "queens").isEmpty())
    }

    @Test
    fun `a received doc never touches another account's rows`() = runTest {
        store("bob", "line-lock", mark = "practicing", inRow = 1, at = 1_000L, uploaded = true)
        store(null, "pairs", mark = "practicing", inRow = 1, at = 1_000L)

        kind.apply("ann", listOf(doc("queens", "line-lock" to value("mastered", 5L, 4_000L), "pairs" to value("mastered", 5L, 4_000L))))

        assertEquals("practicing", stored("bob", "line-lock")!!.mark)
        assertEquals("practicing", stored(null, "pairs")!!.mark)
        assertEquals("mastered", stored("ann", "line-lock")!!.mark)
    }

    @Test
    fun `an acknowledged game leaves pending`() = runTest {
        store("ann", "line-lock", inRow = 2)
        store("ann", "pairs", mark = "mastered", inRow = 5)

        kind.acknowledge("ann", kind.pending("ann"))

        assertTrue(kind.pendingIds("ann").first().isEmpty())
        assertTrue(kind.pending("ann").isEmpty())
    }

    @Test
    fun `an acknowledgement after a newer write keeps that technique pending`() = runTest {
        store("ann", "line-lock", inRow = 2, at = 1_000L)
        store("ann", "pairs", inRow = 1, at = 1_000L)
        val sent = kind.pending("ann")

        dao.upsert(TrainerProgressEntity(0, "ann", "queens", "line-lock", "practicing", 3, 2_000L))
        kind.acknowledge("ann", sent)

        assertFalse(stored("ann", "line-lock")!!.uploaded)
        assertTrue(stored("ann", "pairs")!!.uploaded)
        assertEquals(setOf("queens"), kind.pendingIds("ann").first())
        val resent = (kind.pending("ann").single().fields["techniques"] as Map<*, *>)["line-lock"] as Map<*, *>
        assertEquals(3L, resent["inRow"])
    }

    @Test
    fun `a game whose only rows are still new is sent as an empty doc and then stops being pending`() = runTest {
        store("ann", "fresh", mark = "new", inRow = 0, at = 7_000L)

        val sent = kind.pending("ann")
        assertEquals(mapOf("formatVersion" to 1L, "techniques" to emptyMap<String, Any?>()), sent.single().fields)
        kind.acknowledge("ann", sent)

        assertTrue(kind.pendingIds("ann").first().isEmpty())
    }

    @Test
    fun `a new row beside a sent technique is acknowledged with the game, or the game would stay pending forever`() = runTest {
        store("ann", "line-lock", inRow = 2)
        store("ann", "fresh", mark = "new", inRow = 0)

        kind.acknowledge("ann", kind.pending("ann"))

        assertTrue(stored("ann", "fresh")!!.uploaded)
        assertTrue(kind.pendingIds("ann").first().isEmpty())
    }

    @Test
    fun `forget makes every game pending again`() = runTest {
        store("ann", "line-lock", uploaded = true)
        store("ann", "solo", game = "tango", uploaded = true)
        store("bob", "line-lock", uploaded = true)

        kind.forget("ann")

        assertEquals(setOf("queens", "tango"), kind.pendingIds("ann").first())
        assertTrue(stored("bob", "line-lock")!!.uploaded)
    }

    @Test
    fun `the stamp of a pending game changes with an answer recorded while it waits`() = runTest {
        store("ann", "line-lock", inRow = 2, at = 1_000L)
        val before = kind.pendingStamps("ann").first()

        dao.upsert(TrainerProgressEntity(0, "ann", "queens", "line-lock", "practicing", 2, 2_000L))

        assertEquals(setOf("queens"), before.keys)
        assertNotEquals(before["queens"], kind.pendingStamps("ann").first()["queens"])
    }

    @Test
    fun `the stamp of a game that stays pending is the same after a partial acknowledgement`() = runTest {
        store("ann", "line-lock", inRow = 2, at = 1_000L)
        store("ann", "pairs", inRow = 1, at = 1_000L)
        val sent = kind.pending("ann")
        dao.upsert(TrainerProgressEntity(0, "ann", "queens", "line-lock", "practicing", 3, 2_000L))
        val beforeAck = kind.pendingStamps("ann").first()

        kind.acknowledge("ann", sent)

        assertEquals(setOf("queens"), kind.pendingIds("ann").first())
        assertEquals(beforeAck, kind.pendingStamps("ann").first())
    }

    @Test
    fun `adopt folds a signed-out row into the account's row, leaving one merged pending row`() = runTest {
        store(null, "line-lock", mark = "practicing", inRow = 4, at = 9_000L)
        store("ann", "line-lock", mark = "mastered", inRow = 1, at = 2_000L, uploaded = true)

        kind.adopt("ann")

        val rows = dao.ownedRowsOfGame("ann", "queens")
        assertEquals(1, rows.size)
        assertEquals(Triple("mastered", 4, 9_000L), rows.single().let { Triple(it.mark, it.inRow, it.answeredAtMs) })
        assertFalse(rows.single().uploaded)
        assertTrue(dao.ownerlessRows().isEmpty())
        assertEquals(setOf("queens"), kind.pendingIds("ann").first())
    }

    @Test
    fun `adopt gives the account a signed-out technique it has no row for, pending`() = runTest {
        store(null, "line-lock", mark = "mastered", inRow = 5, at = 3_000L)

        kind.adopt("ann")

        val row = stored("ann", "line-lock")!!
        assertEquals(Triple("mastered", 5, false), Triple(row.mark, row.inRow, row.uploaded))
        assertNull(stored(null, "line-lock"))
    }

    @Test
    fun `adopt leaves another account's rows and an account with no signed-out rows alone`() = runTest {
        store("bob", "line-lock", inRow = 3, uploaded = true)
        store("ann", "pairs", inRow = 2, uploaded = true)

        kind.adopt("ann")

        assertTrue(stored("bob", "line-lock")!!.uploaded)
        assertTrue(stored("ann", "pairs")!!.uploaded)
    }
}
