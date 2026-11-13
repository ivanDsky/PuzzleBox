package com.ivandsky.puzzlebox.backend.firebase

import com.google.firebase.firestore.Blob
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.data.replay.DailyReplayRepository
import com.ivandsky.puzzlebox.model.SolveReplay
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FirestoreDailyReplayRepositoryTest {

    private val board = LeaderboardRef("sudoku", LocalDate.of(2026, 11, 13))
    private val clock = Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000), ZoneOffset.UTC)

    private class RecordingReplayDocStore : ReplayDocStore {
        val docs = mutableMapOf<String, Map<String, Any>>()
        var denyReads = false
        var writes = 0

        override suspend fun put(boardId: String, uid: String, fields: Map<String, Any>) {
            writes++
            docs["$boardId/$uid"] = fields
        }

        override suspend fun get(boardId: String, uid: String): Map<String, Any?>? {
            if (denyReads) error("PERMISSION_DENIED")
            return docs["$boardId/$uid"]
        }

        override suspend fun delete(boardId: String, uid: String) {
            docs.remove("$boardId/$uid")
        }
    }

    private fun repo(store: RecordingReplayDocStore, uid: String? = "me") =
        FirestoreDailyReplayRepository(store = store, currentUid = { uid }, clock = clock)

    @Test
    fun `the cap matches the solve replay cap`() {
        assertEquals(SolveReplay.MAX_BYTES, DailyReplayRepository.MAX_BYTES)
    }

    @Test
    fun `a blob over 12288 bytes is refused without a write`() = runTest {
        val store = RecordingReplayDocStore()
        assertFalse(repo(store).publish(board, ByteArray(12_289)))
        assertEquals(0, store.writes)
    }

    @Test
    fun `an empty blob is refused without a write`() = runTest {
        val store = RecordingReplayDocStore()
        assertFalse(repo(store).publish(board, ByteArray(0)))
        assertEquals(0, store.writes)
    }

    @Test
    fun `signed out publish is refused without a write`() = runTest {
        val store = RecordingReplayDocStore()
        assertFalse(repo(store, uid = null).publish(board, byteArrayOf(1)))
        assertEquals(0, store.writes)
    }

    @Test
    fun `a blob of exactly 12288 bytes round-trips`() = runTest {
        val store = RecordingReplayDocStore()
        val bytes = ByteArray(12_288) { (it % 251).toByte() }
        assertTrue(repo(store).publish(board, bytes))
        assertArrayEquals(bytes, repo(store).fetch(board, "me"))
    }

    @Test
    fun `the doc has exactly the four fields the rules accept`() = runTest {
        val store = RecordingReplayDocStore()
        repo(store).publish(board, byteArrayOf(1, 2, 3))
        val doc = store.docs.getValue("${board.boardId}/me")
        assertEquals(setOf("uid", "blob", "formatVersion", "createdAtMs"), doc.keys)
        assertEquals("me", doc["uid"])
        assertEquals(1L, doc["formatVersion"])
        assertEquals(1_700_000_000_000, doc["createdAtMs"])
        assertTrue(doc["blob"] is Blob)
    }

    @Test
    fun `a denied read is null`() = runTest {
        val store = RecordingReplayDocStore().apply { denyReads = true }
        assertNull(repo(store).fetch(board, "other"))
    }

    @Test
    fun `an absent doc is null`() = runTest {
        assertNull(repo(RecordingReplayDocStore()).fetch(board, "other"))
    }

    @Test
    fun `a doc whose blob is mistyped decodes to null`() {
        assertNull(decodeReplayBlob(mapOf("blob" to "text")))
        assertNull(decodeReplayBlob(emptyMap()))
    }

    @Test
    fun `delete removes only the signed-in player's doc`() = runTest {
        val store = RecordingReplayDocStore()
        repo(store).publish(board, byteArrayOf(1))
        store.docs["${board.boardId}/other"] = emptyMap()
        repo(store).delete(board)
        assertEquals(setOf("${board.boardId}/other"), store.docs.keys)
    }
}
