package com.ivandsky.puzzlebox.backend.firebase

import com.google.firebase.firestore.FirebaseFirestoreException
import com.ivandsky.puzzlebox.data.leaderboard.SubmitError
import com.ivandsky.puzzlebox.data.leaderboard.SubmitResult
import com.ivandsky.puzzlebox.data.repository.CustomBoardRepository
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.testing.contract.CustomBoardRepositoryContract
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Runs [CustomBoardRepositoryContract] against [FirestoreCustomBoardRepository] over a fake
 * [CustomBoardStore] that orders like the server: by the query's fields, then the doc id.
 * Robolectric: constructing real Firebase exceptions reaches android.*.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FirestoreCustomBoardRepositoryContractTest : CustomBoardRepositoryContract() {

    /** Server-side view of `puzzles/{code}/entries/{uid}`; offline makes every call fail. */
    private class FakeCustomBoardStore : CustomBoardStore {
        val boards = HashMap<String, LinkedHashMap<String, Map<String, Any?>>>()
        val queriedFields = mutableListOf<List<String>>()
        var offline = false

        private fun failIfOffline() {
            if (offline) throw FirebaseFirestoreException("offline", FirebaseFirestoreException.Code.UNAVAILABLE)
        }

        override fun observeEntries(code: String, orderBy: List<String>, limit: Long): Flow<List<Map<String, Any?>>> = flow {
            failIfOffline()
            queriedFields += orderBy
            val byFields = orderBy.map { field -> compareBy<Map.Entry<String, Map<String, Any?>>> { (it.value[field] as Number).toLong() } }
            val ranked = boards[code].orEmpty().entries
                .sortedWith(byFields.reduce { a, b -> a.then(b) }.thenBy { it.key })
                .take(limit.toInt())
            emit(ranked.map { it.value })
        }

        override suspend fun createIfAbsent(code: String, uid: String, data: Map<String, Any?>): Map<String, Any?>? {
            failIfOffline()
            val board = boards.getOrPut(code) { LinkedHashMap() }
            board[uid]?.let { return it }
            board[uid] = data
            return null
        }

        override suspend fun count(code: String): Long {
            failIfOffline()
            return boards[code].orEmpty().size.toLong()
        }
    }

    private var signedIn: String? = VIEWER
    private val store = FakeCustomBoardStore()

    override val repository: CustomBoardRepository =
        FirestoreCustomBoardRepository(store = store, currentUid = { signedIn })

    override fun signInAs(uid: String) {
        signedIn = uid
    }

    override fun signOut() {
        signedIn = null
    }

    override fun goOffline() {
        store.offline = true
    }

    @Test
    fun `time then hints queries timeMs then hints`() {
        assertEquals(listOf("timeMs", "hints"), RankOrder.TimeThenHints.boardQueryFields())
    }

    @Test
    fun `hints then time queries hints then timeMs`() {
        assertEquals(listOf("hints", "timeMs"), RankOrder.HintsThenTime.boardQueryFields())
    }

    @Test
    fun `observe sends the order's fields to the store`() = runTest {
        repository.observe(GAME, code(), RankOrder.HintsThenTime, 10).first()

        assertEquals(listOf(listOf("hints", "timeMs")), store.queriedFields)
    }

    @Test
    fun `equal scores tie-break by uid`() = runTest {
        val b = MIDDLE.copy(uid = "b-uid", completedAtMs = MIDDLE.completedAtMs - 1)
        val a = MIDDLE.copy(uid = "a-uid")
        submitAll(b, a)

        assertEquals(listOf(a, b), repository.observe(GAME, code(), RankOrder.TimeThenHints, 10).first())
    }

    @Test
    fun `entries stored under another puzzle ref are dropped`() = runTest {
        store.boards[CODE] = linkedMapOf("x" to LeaderboardDocCodec.encode(MIDDLE.copy(puzzleRef = "queens/2026-10-31/1")))

        assertEquals(emptyList<Any>(), repository.observe(GAME, code(), RankOrder.TimeThenHints, 10).first())
    }

    @Test
    fun `offline submit is unavailable and solvesCount is null`() = runTest {
        goOffline()

        assertTrue((repository.submit(code(), MIDDLE) as SubmitResult.Err).error is SubmitError.Unavailable)
        assertNull(repository.solvesCount(code()))
    }

    @Test
    fun `signed out submit writes nothing`() = runTest {
        signOut()

        assertTrue((repository.submit(code(), MIDDLE) as SubmitResult.Err).error is SubmitError.Unavailable)
        assertTrue(store.boards.isEmpty())
    }
}
