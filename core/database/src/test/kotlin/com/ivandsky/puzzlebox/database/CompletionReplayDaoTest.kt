package com.ivandsky.puzzlebox.database

import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
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

/** Record vs latest replay lookup and retention over real in-memory Room. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CompletionReplayDaoTest {

    private lateinit var db: PuzzleBoxDatabase
    private lateinit var dao: CompletionReplayDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), PuzzleBoxDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(RoomQueryThread.executor)
            .build()
        dao = db.completionReplayDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun bytes(n: Int) = byteArrayOf(n.toByte())

    /** Inserts a completion and, when [withReplay], its replay row holding `bytes(at)`; returns the completion id. */
    private suspend fun solve(ref: String, at: Long, record: Boolean, withReplay: Boolean = true): Long {
        val id = db.completionDao().insert(
            CompletionEntity(
                puzzleRef = ref,
                timeMs = 1_000L,
                hints = 0,
                statsVersion = 1,
                statsBlob = "{}",
                completedAtMs = at,
                firstCompletedAtMs = if (record) at else null,
                isRecord = record,
            ),
        )
        if (withReplay) dao.insert(CompletionReplayEntity(id, bytes(at.toInt())))
        return id
    }

    private suspend fun replayIds(ref: String): List<Long> =
        db.openHelper.readableDatabase
            .query("SELECT r.completionId FROM completion_replays r JOIN completions c ON c.id = r.completionId WHERE c.puzzleRef = '$ref' ORDER BY r.completionId")
            .use { c -> buildList { while (c.moveToNext()) add(c.getLong(0)) } }

    @Test
    fun `one solve is both record and latest`() = runTest {
        solve("a", 10, record = true)

        assertArrayEquals(bytes(10), dao.recordReplay("a"))
        assertArrayEquals(bytes(10), dao.latestReplay("a"))
        assertTrue(dao.latestIsRecord("a"))
    }

    @Test
    fun `two solves split record and latest`() = runTest {
        solve("a", 10, record = true)
        solve("a", 20, record = false)

        assertArrayEquals(bytes(10), dao.recordReplay("a"))
        assertArrayEquals(bytes(20), dao.latestReplay("a"))
        assertFalse(dao.latestIsRecord("a"))
    }

    @Test
    fun `three solves take the newest by completedAtMs not insertion order`() = runTest {
        solve("a", 10, record = true)
        solve("a", 30, record = false)
        solve("a", 20, record = false)

        assertArrayEquals(bytes(10), dao.recordReplay("a"))
        assertArrayEquals(bytes(30), dao.latestReplay("a"))
        assertFalse(dao.latestIsRecord("a"))
    }

    @Test
    fun `puzzle without solves has no replay and counts as latest is record`() = runTest {
        assertNull(dao.recordReplay("a"))
        assertNull(dao.latestReplay("a"))
        assertTrue(dao.latestIsRecord("a"))
    }

    @Test
    fun `a solve without a replay row yields null`() = runTest {
        solve("a", 10, record = true, withReplay = false)
        solve("a", 20, record = false, withReplay = false)

        assertNull(dao.recordReplay("a"))
        assertNull(dao.latestReplay("a"))
    }

    @Test
    fun `prune keeps exactly the record and latest replays`() = runTest {
        val record = solve("a", 10, record = true)
        solve("a", 20, record = false)
        solve("a", 25, record = false)
        val latest = solve("a", 30, record = false)

        dao.prune("a")

        assertEquals(listOf(record, latest), replayIds("a"))
        assertArrayEquals(bytes(10), dao.recordReplay("a"))
        assertArrayEquals(bytes(30), dao.latestReplay("a"))
    }

    @Test
    fun `prune keeps the single row when latest is the record`() = runTest {
        val record = solve("a", 10, record = true)
        solve("a", 5, record = false)

        dao.prune("a")

        assertEquals(listOf(record), replayIds("a"))
    }

    @Test
    fun `prune leaves other puzzles untouched`() = runTest {
        solve("a", 10, record = true)
        solve("a", 20, record = false)
        solve("a", 30, record = false)
        val other = listOf(solve("b", 1, record = true), solve("b", 2, record = false), solve("b", 3, record = false))

        dao.prune("a")

        assertEquals(other, replayIds("b"))
    }

    @Test
    fun `prune with a replay-less latest keeps only the record replay`() = runTest {
        val record = solve("a", 10, record = true)
        solve("a", 20, record = false)
        solve("a", 30, record = false, withReplay = false)

        dao.prune("a")

        assertEquals(listOf(record), replayIds("a"))
    }

    /** As [solve] with an owner and a cloud id; returns the completion id. */
    private suspend fun owned(ref: String, at: Long, record: Boolean, owner: String?, syncId: String, withReplay: Boolean = true): Long {
        val id = db.completionDao().insert(
            CompletionEntity(
                puzzleRef = ref,
                timeMs = 1_000L,
                hints = 0,
                statsVersion = 1,
                statsBlob = "{}",
                completedAtMs = at,
                firstCompletedAtMs = if (record) at else null,
                isRecord = record,
                ownerUid = owner,
                syncId = syncId,
            ),
        )
        if (withReplay) dao.insert(CompletionReplayEntity(id, bytes(at.toInt())))
        return id
    }

    @Test
    fun `pending is the replays of the owner's completions that are not uploaded`() = runTest {
        owned("a", 10, record = true, owner = "ann", syncId = "s1")
        owned("b", 20, record = true, owner = "ann", syncId = "s2")
        owned("c", 30, record = true, owner = "bob", syncId = "s3")
        owned("d", 40, record = true, owner = null, syncId = "s4")

        assertEquals(listOf("s1", "s2"), dao.pending("ann").map { it.syncId })
        assertArrayEquals(bytes(10), dao.pending("ann").first().replay)

        dao.markUploaded("ann", listOf("s1", "s3"))

        assertEquals(listOf("s2"), dao.pending("ann").map { it.syncId })
        assertEquals(listOf("s3"), dao.pending("bob").map { it.syncId })

        dao.clearUploaded("ann")

        assertEquals(listOf("s1", "s2"), dao.pending("ann").map { it.syncId })
        assertEquals(listOf("s3"), dao.pending("bob").map { it.syncId })
    }

    @Test
    fun `pending sync ids re-emit when a replay is stored or acknowledged`() = runTest {
        owned("a", 10, record = true, owner = "ann", syncId = "s1")

        assertEquals(listOf("s1"), dao.observePendingSyncIds("ann").first())
        dao.markUploaded("ann", listOf("s1"))
        assertEquals(emptyList<String>(), dao.observePendingSyncIds("ann").first())
    }

    @Test
    fun `a restored replay is kept for the record and the latest row only and never replaces a stored one`() = runTest {
        owned("a", 10, record = true, owner = "ann", syncId = "rec")
        owned("a", 20, record = false, owner = "ann", syncId = "mid")
        owned("a", 30, record = false, owner = "ann", syncId = "new")
        owned("b", 10, record = true, owner = "bob", syncId = "theirs")

        assertNotNull(dao.keptCompletionId("ann", "rec"))
        assertNotNull(dao.keptCompletionId("ann", "new"))
        assertNull(dao.keptCompletionId("ann", "mid"))
        assertNull(dao.keptCompletionId("ann", "theirs"))
        assertNull(dao.keptCompletionId("ann", "unknown"))

        val id = checkNotNull(dao.keptCompletionId("ann", "rec"))
        assertEquals(-1L, dao.insertIfAbsent(CompletionReplayEntity(id, bytes(99), uploaded = true)))
        assertArrayEquals(bytes(10), dao.pending("ann").first { it.syncId == "rec" }.replay)

        val bare = owned("c", 5, record = true, owner = "ann", syncId = "bare", withReplay = false)
        assertEquals(bare, dao.keptCompletionId("ann", "bare"))
        assertEquals(bare, dao.insertIfAbsent(CompletionReplayEntity(bare, bytes(7), uploaded = true)))
        assertEquals(listOf("rec"), dao.pending("ann").filter { it.syncId in setOf("rec", "bare") }.map { it.syncId })
    }
}
