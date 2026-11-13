package com.ivandsky.puzzlebox.data.cloud.kinds

import androidx.room.Room
import com.ivandsky.puzzlebox.data.cloud.CloudDoc
import com.ivandsky.puzzlebox.data.di.inSyncOrder
import com.ivandsky.puzzlebox.database.CompletionEntity
import com.ivandsky.puzzlebox.database.CompletionReplayEntity
import com.ivandsky.puzzlebox.database.PuzzleBoxDatabase
import com.ivandsky.puzzlebox.model.SolveReplay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Replays as a synced kind over in-memory Room: the doc shape, the syncId mapping, pending, apply and acknowledge. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReplaysKindTest {

    private lateinit var db: PuzzleBoxDatabase
    private lateinit var kind: ReplaysKind

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), PuzzleBoxDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        kind = ReplaysKind(db, db.completionReplayDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun bytes(n: Int, size: Int = 3) = ByteArray(size) { n.toByte() }

    /** A finished game's completion with its replay (when [replay] is not null); returns the completion id. */
    private suspend fun solve(
        syncId: String,
        at: Long,
        ref: String = "alpha/2026-03-01/1",
        owner: String? = "ann",
        isRecord: Boolean = false,
        replay: ByteArray? = bytes(at.toInt()),
        uploaded: Boolean = false,
    ): Long {
        val id = db.completionDao().insert(
            CompletionEntity(
                puzzleRef = ref,
                timeMs = 40_000L,
                hints = 0,
                statsVersion = 1,
                statsBlob = "{}",
                completedAtMs = at,
                firstCompletedAtMs = if (isRecord) at else null,
                isRecord = isRecord,
                ownerUid = owner,
                syncId = syncId,
            ),
        )
        if (replay != null) db.completionReplayDao().insert(CompletionReplayEntity(id, replay, uploaded))
        return id
    }

    private fun doc(id: String, replay: Any? = bytes(9)) =
        CloudDoc(id, buildMap { put("formatVersion", 1L); if (replay != null) put("replay", replay) })

    private suspend fun stored(syncId: String): ByteArray? =
        db.openHelper.readableDatabase
            .query("SELECT r.replay FROM completion_replays r JOIN completions c ON c.id = r.completionId WHERE c.syncId = '$syncId'")
            .use { c -> if (c.moveToFirst()) c.getBlob(0) else null }

    @Test
    fun `a pending replay encodes as one doc named by the completion's syncId`() = runTest {
        solve("s1", 10, isRecord = true)

        val docs = kind.pending("ann")

        assertEquals(listOf("s1"), docs.map { it.id })
        assertEquals(setOf("formatVersion", "replay"), docs.single().fields.keys)
        assertEquals(1L, docs.single().fields["formatVersion"])
        assertArrayEquals(bytes(10), docs.single().fields["replay"] as ByteArray)
    }

    @Test
    fun `the doc a device uploads decodes on another device`() = runTest {
        solve("s1", 10, isRecord = true)
        val sent = kind.pending("ann")

        val other = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), PuzzleBoxDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val id = other.completionDao().insert(
                CompletionEntity(
                    puzzleRef = "alpha/2026-03-01/1", timeMs = 40_000L, hints = 0, statsVersion = 1, statsBlob = "{}",
                    completedAtMs = 10, firstCompletedAtMs = 10, isRecord = true, ownerUid = "ann", syncId = "s1",
                ),
            )

            assertEquals(1, ReplaysKind(other, other.completionReplayDao()).apply("ann", sent))

            val row = other.completionReplayDao().recordReplay("alpha/2026-03-01/1")
            assertArrayEquals(bytes(10), row)
            assertEquals(emptyList<String>(), other.completionReplayDao().pending("ann").map { it.syncId })
            assertEquals(id, other.completionReplayDao().keptCompletionId("ann", "s1"))
        } finally {
            other.close()
        }
    }

    @Test
    fun `only the account's own replays are pending`() = runTest {
        solve("mine", 10, ref = "a", isRecord = true)
        solve("theirs", 20, ref = "b", owner = "bob", isRecord = true)
        solve("nobody", 30, ref = "c", owner = null, isRecord = true)
        solve("done", 40, ref = "d", isRecord = true, uploaded = true)
        solve("bare", 50, ref = "e", isRecord = true, replay = null)

        assertEquals(listOf("mine"), kind.pending("ann").map { it.id })
        assertEquals(setOf("mine"), kind.pendingIds("ann").first())
        assertEquals(listOf("theirs"), kind.pending("bob").map { it.id })
    }

    @Test
    fun `a game that is not finished has no replay to send`() = runTest {
        // A replay row references a completion, which exists only once a game is finished: with no completion the row is refused.
        val refused = runCatching { db.completionReplayDao().insert(CompletionReplayEntity(77, bytes(1))) }
        assertTrue(refused.isFailure)
        assertEquals(emptyList<CloudDoc>(), kind.pending("ann"))
    }

    @Test
    fun `an oversized stored replay is never uploaded`() = runTest {
        solve("big", 10, isRecord = true, replay = bytes(1, SolveReplay.MAX_BYTES + 1))
        solve("max", 20, ref = "b", isRecord = true, replay = bytes(2, SolveReplay.MAX_BYTES))

        assertEquals(listOf("max"), kind.pending("ann").map { it.id })
    }

    @Test
    fun `acknowledge marks only the uploaded docs and forget makes them pending again`() = runTest {
        solve("s1", 10, ref = "a", isRecord = true)
        solve("s2", 20, ref = "b", isRecord = true)

        kind.acknowledge("ann", kind.pending("ann").filter { it.id == "s1" })
        assertEquals(listOf("s2"), kind.pending("ann").map { it.id })

        kind.forget("ann")
        assertEquals(listOf("s1", "s2"), kind.pending("ann").map { it.id })
    }

    @Test
    fun `apply restores a replay for the record and the latest row, marked uploaded`() = runTest {
        solve("rec", 10, isRecord = true, replay = null)
        solve("mid", 20, replay = null)
        solve("new", 30, replay = null)

        val restored = kind.apply("ann", listOf(doc("rec", bytes(1)), doc("mid", bytes(2)), doc("new", bytes(3))))

        assertEquals(2, restored)
        assertArrayEquals(bytes(1), stored("rec"))
        assertNull(stored("mid"))
        assertArrayEquals(bytes(3), stored("new"))
        assertEquals(emptyList<CloudDoc>(), kind.pending("ann"))
    }

    @Test
    fun `apply never replaces a stored replay and ignores unknown or foreign completions and bad docs`() = runTest {
        solve("kept", 10, ref = "a", isRecord = true, replay = bytes(5))
        solve("theirs", 20, ref = "b", owner = "bob", isRecord = true, replay = null)
        solve("nobody", 30, ref = "c", owner = null, isRecord = true, replay = null)
        solve("text", 40, ref = "d", isRecord = true, replay = null)
        solve("empty", 50, ref = "e", isRecord = true, replay = null)
        solve("huge", 60, ref = "f", isRecord = true, replay = null)

        val restored = kind.apply(
            "ann",
            listOf(
                doc("kept", bytes(9)),
                doc("theirs"),
                doc("nobody"),
                doc("unknown"),
                doc("text", "AQID"),
                doc("empty", ByteArray(0)),
                doc("huge", bytes(1, SolveReplay.MAX_BYTES + 1)),
                doc("missing", null),
            ),
        )

        assertEquals(0, restored)
        assertArrayEquals(bytes(5), stored("kept"))
        assertNull(stored("theirs"))
        assertNull(stored("nobody"))
        assertNull(stored("text"))
        assertNull(stored("empty"))
        assertNull(stored("huge"))
    }

    @Test
    fun `adopt leaves ownership to the completions`() = runTest {
        solve("nobody", 30, owner = null, isRecord = true)

        kind.adopt("ann")

        assertEquals(emptyList<CloudDoc>(), kind.pending("ann"))
    }

    @Test
    fun `a run visits the replays kind after the completions it restores into`() {
        val completions = CompletionsKind(db, db.completionDao())

        assertEquals(
            listOf("completions", "replays"),
            inSyncOrder(listOf(kind, completions)).map { it.kind },
        )
    }
}
