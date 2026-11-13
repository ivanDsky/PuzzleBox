package com.ivandsky.puzzlebox.data.cloud.kinds

import androidx.room.Room
import com.ivandsky.puzzlebox.testing.room.RoomQueryThread
import androidx.room.withTransaction
import com.ivandsky.puzzlebox.data.cloud.CloudDoc
import com.ivandsky.puzzlebox.database.CompletionEntity
import com.ivandsky.puzzlebox.database.PuzzleBoxDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Completions as a synced kind over in-memory Room: month docs, pending, apply, adopt, acknowledge, forget. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CompletionsKindTest {

    private val march = 1_772_323_200_000L // 2026-03-01T00:00:00Z
    private val april = 1_775_001_600_000L // 2026-04-01T00:00:00Z
    private val dbs = mutableListOf<PuzzleBoxDatabase>()
    private lateinit var db: PuzzleBoxDatabase
    private lateinit var kind: CompletionsKind

    @Before
    fun setUp() {
        db = newDb()
        kind = CompletionsKind(db, db.completionDao())
    }

    @After
    fun tearDown() {
        dbs.forEach { it.close() }
    }

    private fun newDb(): PuzzleBoxDatabase = Room
        .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), PuzzleBoxDatabase::class.java)
        .allowMainThreadQueries()
        .setQueryExecutor(RoomQueryThread.executor)
        .build()
        .also { dbs += it }

    private fun row(
        syncId: String,
        at: Long,
        ref: String = "alpha/2026-03-01/1",
        owner: String? = "ann",
        uploaded: Boolean = false,
        isRecord: Boolean = false,
    ) = CompletionEntity(
        puzzleRef = ref,
        timeMs = 40_000L,
        hints = 1,
        statsVersion = 2,
        statsBlob = """{"v":2}""",
        completedAtMs = at,
        firstCompletedAtMs = if (isRecord) at else null,
        isRecord = isRecord,
        matchCode = null,
        source = "app",
        ownerUid = owner,
        syncId = syncId,
        uploaded = uploaded,
    )

    private suspend fun insert(vararg rows: CompletionEntity) = insertAll(rows.toList())

    private suspend fun insertAll(rows: List<CompletionEntity>) = db.withTransaction {
        rows.forEach { db.completionDao().insert(it) }
    }

    /** Every row of the database, whoever owns it. */
    private suspend fun allRows(into: PuzzleBoxDatabase = db): List<CompletionEntity> =
        into.completionDao().observeBySource("app", null).first().sortedBy { it.id }

    private fun recordsOf(rows: List<CompletionEntity>) = rows.filter { it.isRecord }

    private fun doc(id: String, vararg rows: CompletionEntity) = CloudDoc(
        id,
        mapOf("formatVersion" to 1L, "records" to rows.associate { it.syncId to CompletionMerge().encode(it) }),
    )

    @Test
    fun `pending groups the account's unuploaded rows into month docs`() = runTest {
        insert(
            row("a", march + 10),
            row("b", march + 20, ref = "alpha/y"),
            row("c", april + 10),
            row("done", march + 30, uploaded = true),
            row("other", march + 40, owner = "bob"),
            row("ownerless", march + 50, owner = null),
        )

        val docs = kind.pending("ann")

        assertEquals(listOf("2026-03", "2026-04"), docs.map { it.id }.sorted())
        val marchDoc = docs.single { it.id == "2026-03" }
        assertEquals(1L, marchDoc.fields["formatVersion"])
        assertEquals(setOf("a", "b"), records(marchDoc).keys)
        assertEquals(setOf("c"), records(docs.single { it.id == "2026-04" }).keys)
    }

    @Test
    fun `no encoded record carries isRecord`() = runTest {
        insert(row("a", march + 10, isRecord = true), row("b", march + 20))

        val encoded = kind.pending("ann").flatMap { records(it).values }

        assertEquals(2, encoded.size)
        assertTrue(encoded.none { "isRecord" in it })
        assertEquals(
            setOf(
                "puzzleRef", "timeMs", "hints", "statsVersion", "statsBlob", "completedAtMs", "firstCompletedAtMs",
                "matchCode", "source",
            ),
            encoded.first().keys,
        )
    }

    @Test
    fun `a doc id switches to a numbered overflow every 1000 records by finish time and sync id`() = runTest {
        // 2,001 rows in March: finish times 0..1999 and one tie at 1999; insert order is shuffled so ordinals come from the sort.
        val rows = (0 until 2_001).map { i -> row("s%04d".format(i), march + minOf(i, 1_999)) }
        insertAll(rows.shuffled(java.util.Random(7)))

        val docs = kind.pending("ann").associate { it.id to records(it).keys }

        assertEquals(setOf("2026-03", "2026-03-1", "2026-03-2"), docs.keys)
        assertEquals((0 until 1_000).map { "s%04d".format(it) }.toSet(), docs.getValue("2026-03"))
        assertEquals((1_000 until 2_000).map { "s%04d".format(it) }.toSet(), docs.getValue("2026-03-1"))
        assertEquals(setOf("s2000"), docs.getValue("2026-03-2"))
    }

    @Test
    fun `only the pending rows of an overflowed month travel`() = runTest {
        val rows = (0 until 1_001).map { i -> row("s%04d".format(i), march + i, uploaded = i != 1_000) }
        insertAll(rows)

        val docs = kind.pending("ann")

        assertEquals(listOf("2026-03-1"), docs.map { it.id })
        assertEquals(setOf("s1000"), records(docs.single()).keys)
    }

    @Test
    fun `pendingIds follows the pending docs`() = runTest {
        insert(row("a", march + 10), row("c", april + 10), row("other", march + 40, owner = "bob"))

        assertEquals(setOf("2026-03", "2026-04"), kind.pendingIds("ann").first())

        kind.acknowledge("ann", kind.pending("ann"))

        assertEquals(emptySet<String>(), kind.pendingIds("ann").first())
    }

    @Test
    fun `acknowledge marks only the uploaded records of that account`() = runTest {
        insert(row("a", march + 10), row("b", march + 20), row("other", march + 30, owner = "bob"))

        kind.acknowledge("ann", listOf(doc("2026-03", row("a", march + 10), row("other", march + 30))))

        val byId = allRows().associateBy { it.syncId }
        assertTrue(byId.getValue("a").uploaded)
        assertFalse(byId.getValue("b").uploaded)
        assertFalse(byId.getValue("other").uploaded)
        assertEquals(setOf("2026-03"), kind.pending("ann").map { it.id }.toSet())
        assertEquals(setOf("b"), records(kind.pending("ann").single()).keys)
    }

    @Test
    fun `forget makes every row of the account pending again`() = runTest {
        insert(row("a", march + 10, uploaded = true), row("other", march + 20, owner = "bob", uploaded = true))

        kind.forget("ann")

        val byId = allRows().associateBy { it.syncId }
        assertFalse(byId.getValue("a").uploaded)
        assertTrue(byId.getValue("other").uploaded)
    }

    @Test
    fun `a round trip restores the rows with their timestamps and no extra upload`() = runTest {
        insert(
            row("a", march + 10, isRecord = true),
            row("b", march + 20),
            row("c", april + 10, ref = "alpha/y", isRecord = true),
        )
        val docs = kind.pending("ann")

        val otherDevice = newDb()
        val restored = CompletionsKind(otherDevice, otherDevice.completionDao())
        val count = restored.apply("ann", docs)

        assertEquals(3, count)
        val byId = allRows(otherDevice).associateBy { it.syncId }
        assertEquals(setOf("a", "b", "c"), byId.keys)
        assertEquals(march + 10, byId.getValue("a").completedAtMs)
        assertEquals(march + 20, byId.getValue("b").completedAtMs)
        assertEquals(april + 10, byId.getValue("c").completedAtMs)
        assertTrue(byId.values.all { it.ownerUid == "ann" && it.uploaded && it.source == "app" })
        assertEquals(setOf("a", "c"), recordsOf(byId.values.toList()).map { it.syncId }.toSet())
        assertEquals(march + 10, byId.getValue("a").firstCompletedAtMs)
        assertNull(byId.getValue("b").firstCompletedAtMs)
        assertTrue(restored.pending("ann").isEmpty())
    }

    @Test
    fun `apply is idempotent`() = runTest {
        val docs = listOf(doc("2026-03", row("a", march + 10), row("b", march + 20)))

        assertEquals(2, kind.apply("ann", docs))
        val after = allRows()
        assertEquals(0, kind.apply("ann", docs))

        assertEquals(after, allRows())
        assertEquals(1, recordsOf(allRows()).size)
    }

    @Test
    fun `apply never overwrites a local row with the same sync id`() = runTest {
        insert(row("a", march + 10, isRecord = true).copy(timeMs = 1L))

        assertEquals(0, kind.apply("ann", listOf(doc("2026-03", row("a", march + 10).copy(timeMs = 99L)))))

        assertEquals(1L, allRows().single().timeMs)
    }

    @Test
    fun `apply leaves another account's row with the same sync id alone`() = runTest {
        insert(row("a", march + 10, owner = "bob", isRecord = true))

        assertEquals(0, kind.apply("ann", listOf(doc("2026-03", row("a", march + 10)))))

        assertEquals("bob", allRows().single().ownerUid)
    }

    @Test
    fun `a restored earlier finish takes the record from the local one`() = runTest {
        insert(row("late", march + 500, isRecord = true))

        assertEquals(1, kind.apply("ann", listOf(doc("2026-03", row("early", march + 100)))))

        val byId = allRows().associateBy { it.syncId }
        assertTrue(byId.getValue("early").isRecord)
        assertEquals(march + 100, byId.getValue("early").firstCompletedAtMs)
        assertFalse(byId.getValue("late").isRecord)
        assertNull(byId.getValue("late").firstCompletedAtMs)
    }

    @Test
    fun `a restored later finish stays a play-again`() = runTest {
        insert(row("early", march + 100, isRecord = true))

        kind.apply("ann", listOf(doc("2026-03", row("late", march + 500, isRecord = true))))

        val byId = allRows().associateBy { it.syncId }
        assertTrue(byId.getValue("early").isRecord)
        assertFalse(byId.getValue("late").isRecord)
    }

    @Test
    fun `apply does not touch another account's record of the same puzzle`() = runTest {
        insert(row("bobs", march + 900, owner = "bob", isRecord = true))

        kind.apply("ann", listOf(doc("2026-03", row("a", march + 100))))

        val byId = allRows().associateBy { it.syncId }
        assertTrue(byId.getValue("bobs").isRecord)
        assertTrue(byId.getValue("a").isRecord)
    }

    @Test
    fun `adopt claims ownerless rows and leaves one record per puzzle after joining an account that has the ref`() = runTest {
        // Signed out earlier: its own first win of the ref. The account already finished the same ref, later.
        insert(row("offline", march + 100, owner = null, isRecord = true), row("account", march + 500, isRecord = true))

        kind.adopt("ann")

        val rows = allRows()
        assertTrue(rows.all { it.ownerUid == "ann" })
        assertEquals(listOf("offline"), recordsOf(rows).map { it.syncId })
        assertNull(rows.single { it.syncId == "account" }.firstCompletedAtMs)
        assertEquals(setOf("offline", "account"), kind.pending("ann").flatMap { records(it).keys }.toSet())
    }

    @Test
    fun `adopt keeps the account's earlier record when the ownerless row is later`() = runTest {
        insert(row("offline", march + 900, owner = null, isRecord = true), row("account", march + 100, isRecord = true))

        kind.adopt("ann")

        assertEquals(listOf("account"), recordsOf(allRows()).map { it.syncId })
    }

    @Test
    fun `adopt does not take another account's rows`() = runTest {
        insert(row("bobs", march + 100, owner = "bob", isRecord = true), row("offline", march + 200, owner = null, isRecord = true))

        kind.adopt("ann")

        val byId = allRows().associateBy { it.syncId }
        assertEquals("bob", byId.getValue("bobs").ownerUid)
        assertEquals("ann", byId.getValue("offline").ownerUid)
        assertTrue(byId.getValue("bobs").isRecord)
        assertTrue(byId.getValue("offline").isRecord)
    }

    @Suppress("UNCHECKED_CAST")
    private fun records(doc: CloudDoc): Map<String, Map<String, Any?>> = doc.fields.getValue("records") as Map<String, Map<String, Any?>>
}
