package com.ivandsky.puzzlebox.backend.firebase

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardSnapshot
import com.ivandsky.puzzlebox.data.leaderboard.SubmitError
import com.ivandsky.puzzlebox.data.leaderboard.SubmitResult
import com.ivandsky.puzzlebox.game.RankOrder
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Repository behavior over a fake [LeaderboardStore] (brief §4): snapshot decoding,
 * versioned-envelope guards, submit dedup on (uid, puzzleRef), and typed-error mapping
 * for REAL Firebase exception types thrown at the seam. Robolectric: constructing real
 * Firebase exceptions reaches android.* (TextUtils).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FirestoreLeaderboardRepositoryTest {

    private class FakeStore : LeaderboardStore {
        val docs = MutableStateFlow<Map<String, List<Map<String, Any?>>>>(emptyMap())
        val existing = mutableSetOf<Pair<String, String>>() // boardId to uid
        var observeError: Exception? = null
        /** How many in-flow failures the next observations throw before succeeding. */
        var observeFailuresLeft = 0
        var submitError: Exception? = null
        val observed = mutableListOf<String>()
        val observedOrders = mutableListOf<RankOrder>()

        /** When set, the listener plays this script instead of the stored docs; null = nothing known yet (empty cache). */
        var script: Flow<List<Map<String, Any?>>?>? = null

        override fun observeEntries(boardId: String, order: RankOrder): Flow<List<Map<String, Any?>>?> {
            observed += boardId
            observedOrders += order
            script?.let { return it }
            // Errors surface IN the flow, like the real snapshot listener — never eagerly.
            return flow {
                if (observeFailuresLeft > 0) {
                    observeFailuresLeft--
                    throw observeError ?: error("no error configured")
                }
                // The real store orders server-side by the order's query fields; malformed
                // rows must survive the sort (nulls first) so decode can drop them.
                val sorted = docs.value[boardId].orEmpty().sortedWith(
                    compareBy(
                        { (it[order.boardQueryFields()[0]] as? Number)?.toLong() ?: 0L },
                        { (it[order.boardQueryFields()[1]] as? Number)?.toLong() ?: 0L },
                    ),
                )
                emit(sorted)
            }
        }

        var fetchError: Exception? = null
        var countError: Exception? = null

        /** What the server-side count reports; null = the stored docs. */
        var total: Long? = null
        val fetched = mutableListOf<String>()
        val counted = mutableListOf<String>()

        override suspend fun fetchEntries(boardId: String, order: RankOrder): List<Map<String, Any?>> {
            fetched += boardId
            fetchError?.let { throw it }
            return docs.value[boardId].orEmpty().sortedWith(
                compareBy(
                    { (it[order.boardQueryFields()[0]] as? Number)?.toLong() ?: 0L },
                    { (it[order.boardQueryFields()[1]] as? Number)?.toLong() ?: 0L },
                ),
            )
        }

        override suspend fun count(boardId: String): Long {
            counted += boardId
            countError?.let { throw it }
            return total ?: docs.value[boardId].orEmpty().size.toLong()
        }

        override suspend fun createIfAbsent(
            boardId: String,
            uid: String,
            data: Map<String, Any?>,
        ): Boolean {
            submitError?.let { throw it }
            val key = boardId to uid
            return if (key in existing) false else {
                existing += key
                docs.value = docs.value + (boardId to (docs.value[boardId].orEmpty() + data))
                true
            }
        }
    }

    private val day = LocalDate.of(2026, 10, 31)
    private val board = LeaderboardRef("zip", day)
    private val refKey = "zip/2026-10-31/592"

    private fun entry(
        uid: String = "u1",
        timeMs: Long = 95_000,
        hints: Int = 2,
        puzzleRef: String = refKey,
    ) = LeaderboardEntry(
        uid = uid,
        displayName = "Player $uid",
        puzzleRef = puzzleRef,
        timeMs = timeMs,
        hints = hints,
        completedAtMs = 1_700_000_000_000,
    )

    private fun doc(entry: LeaderboardEntry, extra: Map<String, Any?> = emptyMap()) =
        LeaderboardDocCodec.encode(entry) + extra

    // ----- observe -----

    @Test
    fun `signed out observes null without touching the backend`() = runTest {
        val store = FakeStore()
        val repository = FirestoreLeaderboardRepository(store, currentUid = { null })

        assertNull(repository.observe(board, RankOrder.TimeThenHints).first())
        assertTrue(store.observed.isEmpty())
    }

    @Test
    fun `observe delivers a versioned snapshot of valid docs`() = runTest {
        val store = FakeStore()
        store.docs.value = mapOf(board.boardId to listOf(doc(entry(uid = "u1")), doc(entry(uid = "u2", timeMs = 80_000))))
        val repository = FirestoreLeaderboardRepository(store, currentUid = { "me" })

        val snapshot = repository.observe(board, RankOrder.TimeThenHints).first()!!

        assertEquals(board, snapshot.board)
        assertEquals(LeaderboardSnapshot.FORMAT_VERSION, snapshot.formatVersion)
        assertEquals(listOf("u2", "u1"), snapshot.entries.map { it.uid }) // store-ordered
    }

    @Test
    fun `observe asks the store for the given order and delivers hints-first boards hints first`() = runTest {
        val store = FakeStore()
        store.docs.value = mapOf(
            board.boardId to listOf(
                doc(entry(uid = "slow", timeMs = 200_000, hints = 0)),
                doc(entry(uid = "fast", timeMs = 50_000, hints = 3)),
                doc(entry(uid = "quick", timeMs = 100_000, hints = 0)),
            ),
        )
        val repository = FirestoreLeaderboardRepository(store, currentUid = { "me" })

        val snapshot = repository.observe(board, RankOrder.HintsThenTime).first()!!

        assertEquals(listOf(RankOrder.HintsThenTime), store.observedOrders)
        assertEquals(listOf("quick", "slow", "fast"), snapshot.entries.map { it.uid })
        assertEquals(listOf("hints", "timeMs"), RankOrder.HintsThenTime.boardQueryFields())
        assertEquals(listOf("timeMs", "hints"), RankOrder.TimeThenHints.boardQueryFields())
    }

    @Test
    fun `malformed and foreign-version docs are dropped, never reinterpreted (A19)`() = runTest {
        val store = FakeStore()
        store.docs.value = mapOf(
            board.boardId to listOf(
                doc(entry(uid = "u1")),
                // Unknown future format version: dropped.
                doc(entry(uid = "u2")) + mapOf("formatVersion" to 2L),
                // Missing fields: dropped.
                mapOf("uid" to "u3"),
                // Sanity-violating: dropped (matches the rules envelope).
                doc(entry(uid = "u4")).toMutableMap().apply { put("timeMs", -5L) },
                // Unknown EXTRA fields are preserved on the doc and simply ignored here.
                doc(entry(uid = "u5"), extra = mapOf("someFutureField" to "x")),
            ),
        )
        val repository = FirestoreLeaderboardRepository(store, currentUid = { "me" })

        val snapshot = repository.observe(board, RankOrder.TimeThenHints).first()!!

        assertEquals(listOf("u1", "u5"), snapshot.entries.map { it.uid })
    }

    @Test
    fun `an empty cache snapshot is null, not an empty board, and the server answer follows on the same flow`() = runTest {
        val store = FakeStore()
        val answers = MutableSharedFlow<List<Map<String, Any?>>?>()
        store.script = answers
        val repository = FirestoreLeaderboardRepository(store, currentUid = { "me" })
        val seen = mutableListOf<LeaderboardSnapshot?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.observe(board, RankOrder.TimeThenHints).toList(seen)
        }

        answers.emit(null)
        assertEquals(listOf<LeaderboardSnapshot?>(null), seen)

        answers.emit(emptyList())
        assertEquals(listOf(null, LeaderboardSnapshot(board, emptyList())), seen)
    }

    @Test
    fun `only a snapshot from the cache with no docs reads as nothing known`() {
        val docs = listOf(doc(entry()))

        assertNull(boardDocs(fromCache = true, docs = emptyList()))
        assertEquals(emptyList<Map<String, Any?>>(), boardDocs(fromCache = false, docs = emptyList()))
        assertEquals(docs, boardDocs(fromCache = true, docs = docs))
        assertEquals(docs, boardDocs(fromCache = false, docs = docs))
    }

    @Test
    fun `listener errors degrade to a null snapshot (A7)`() = runTest {
        val store = FakeStore()
        store.observeError = FirebaseNetworkException("offline")
        // More failures than the repository's retries: initial + 2 backoff attempts.
        store.observeFailuresLeft = 3
        val repository = FirestoreLeaderboardRepository(store, currentUid = { "me" })

        // Persistent errors exhaust the retries, then terminate at the soft null —
        // never a thrown error across the seam. runTest's virtual time skips the
        // retry backoff instantly.
        assertNull(repository.observe(board, RankOrder.TimeThenHints).first())
    }

    @Test
    fun `transient listener errors recover through the retry backoff`() = runTest {
        val store = FakeStore()
        store.observeFailuresLeft = 1 // one offline flap, then the listener stabilizes
        store.docs.value = mapOf(board.boardId to listOf(doc(entry())))
        val repository = FirestoreLeaderboardRepository(store, currentUid = { "me" })

        val snapshot = repository.observe(board, RankOrder.TimeThenHints).first()
        assertTrue(snapshot != null)
        assertEquals(listOf("u1"), snapshot!!.entries.map { it.uid })
    }

    // ----- submit -----

    @Test
    fun `signed-out submit is local-only unavailable, no backend touch (A7)`() = runTest {
        val store = FakeStore()
        val repository = FirestoreLeaderboardRepository(store, currentUid = { null })

        val result = repository.submit(entry())

        assertTrue(result is SubmitResult.Err)
        assertTrue((result as SubmitResult.Err).error is SubmitError.Unavailable)
        assertTrue(store.existing.isEmpty())
    }

    @Test
    fun `first submit writes and returns ok`() = runTest {
        val store = FakeStore()
        val repository = FirestoreLeaderboardRepository(store, currentUid = { "u1" })

        val result = repository.submit(entry())

        assertEquals(SubmitResult.Ok(entry()), result)
        assertEquals(listOf("u1"), store.docs.value[board.boardId]!!.map { it["uid"] })
    }

    @Test
    fun `re-submitting the same (uid, puzzleRef) dedups — first write stands (A4-A5)`() = runTest {
        val store = FakeStore()
        val repository = FirestoreLeaderboardRepository(store, currentUid = { "u1" })
        repository.submit(entry(timeMs = 95_000))

        val faster = repository.submit(entry(timeMs = 10_000))

        assertEquals(SubmitResult.Deduped(existing = null), faster)
        // Only the first write survived — a faster play-again can never overwrite the record.
        assertEquals(95_000L, store.docs.value[board.boardId]!!.single()["timeMs"])
    }

    @Test
    fun `same uid on another day writes to that day's board`() = runTest {
        val store = FakeStore()
        val repository = FirestoreLeaderboardRepository(store, currentUid = { "u1" })
        repository.submit(entry())

        val nextDay = repository.submit(entry(puzzleRef = "zip/2026-11-01/593"))

        assertTrue(nextDay is SubmitResult.Ok)
        assertTrue(board.boardId to "u1" in store.existing)
        assertTrue(LeaderboardRef("zip", LocalDate.of(2026, 11, 1)).boardId to "u1" in store.existing)
    }

    @Test
    fun `non-daily puzzleRef is rejected before touching the backend (A5)`() = runTest {
        val store = FakeStore()
        val repository = FirestoreLeaderboardRepository(store, currentUid = { "u1" })

        val result = repository.submit(entry(puzzleRef = "zip/2026-10-31"))

        assertTrue(result is SubmitResult.Err)
        assertTrue((result as SubmitResult.Err).error is SubmitError.Rejected)
        assertTrue(store.existing.isEmpty())
    }

    @Test
    fun `permission denied maps to rejected`() = runTest {
        val store = FakeStore()
        store.submitError = FirebaseFirestoreException(
            "denied",
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
        )
        val repository = FirestoreLeaderboardRepository(store, currentUid = { "u1" })

        val result = repository.submit(entry())

        assertTrue((result as SubmitResult.Err).error is SubmitError.Rejected)
    }

    @Test
    fun `network outage maps to unavailable`() = runTest {
        val store = FakeStore()
        store.submitError = FirebaseNetworkException("offline")
        val repository = FirestoreLeaderboardRepository(store, currentUid = { "u1" })

        val result = repository.submit(entry())

        assertTrue((result as SubmitResult.Err).error is SubmitError.Unavailable)
    }

    @Test
    fun `firestore unavailable maps to unavailable`() = runTest {
        val store = FakeStore()
        store.submitError = FirebaseFirestoreException(
            "down",
            FirebaseFirestoreException.Code.UNAVAILABLE,
        )
        val repository = FirestoreLeaderboardRepository(store, currentUid = { "u1" })

        val result = repository.submit(entry())

        assertTrue((result as SubmitResult.Err).error is SubmitError.Unavailable)
    }

    @Test
    fun `unexpected failure maps to unknown`() = runTest {
        val store = FakeStore()
        store.submitError = IllegalStateException("boom")
        val repository = FirestoreLeaderboardRepository(store, currentUid = { "u1" })

        val result = repository.submit(entry())

        assertTrue((result as SubmitResult.Err).error is SubmitError.Unknown)
    }

    // ----- fetch / count -----

    private fun fullBoard() = mapOf(
        board.boardId to (1..100).map { doc(entry(uid = "u$it", timeMs = 1_000L * it)) },
    )

    @Test
    fun `signed out fetch and count are null without touching the backend`() = runTest {
        val store = FakeStore()
        val repository = FirestoreLeaderboardRepository(store, currentUid = { null })

        assertNull(repository.fetch(board, RankOrder.TimeThenHints))
        assertNull(repository.count(board))
        assertTrue(store.fetched.isEmpty())
        assertTrue(store.counted.isEmpty())
    }

    @Test
    fun `fetch decodes a page ranked by the order and skips count when not full`() = runTest {
        val store = FakeStore()
        store.docs.value = mapOf(
            board.boardId to listOf(
                doc(entry(uid = "u1")),
                doc(entry(uid = "u2", timeMs = 80_000)),
                mapOf("uid" to "broken"),
            ),
        )
        val repository = FirestoreLeaderboardRepository(store, currentUid = { "me" })

        val page = repository.fetch(board, RankOrder.TimeThenHints)!!

        assertEquals(listOf("u2", "u1"), page.entries.map { it.uid })
        assertEquals(2, page.count)
        assertTrue(store.counted.isEmpty())
    }

    @Test
    fun `fetch of a full page issues count once and reports N`() = runTest {
        val store = FakeStore()
        store.docs.value = fullBoard()
        store.total = 137
        val repository = FirestoreLeaderboardRepository(store, currentUid = { "me" })

        val page = repository.fetch(board, RankOrder.TimeThenHints)!!

        assertEquals(100, page.entries.size)
        assertEquals(137, page.count)
        assertEquals(listOf(board.boardId), store.counted)
    }

    @Test
    fun `fetch is null when the read fails or the count of a full page fails`() = runTest {
        val store = FakeStore()
        store.docs.value = fullBoard()
        val repository = FirestoreLeaderboardRepository(store, currentUid = { "me" })

        store.countError = IllegalStateException("count down")
        assertNull(repository.fetch(board, RankOrder.TimeThenHints))

        store.countError = null
        store.fetchError = FirebaseNetworkException("offline")
        assertNull(repository.fetch(board, RankOrder.TimeThenHints))
    }

    @Test
    fun `count reports N and is null on failure`() = runTest {
        val store = FakeStore()
        store.total = 250
        val repository = FirestoreLeaderboardRepository(store, currentUid = { "me" })

        assertEquals(250, repository.count(board))

        store.countError = FirebaseNetworkException("offline")
        assertNull(repository.count(board))
    }
}
