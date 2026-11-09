package com.ivandsky.puzzlebox.backend.firebase

import app.cash.turbine.test
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.ivandsky.puzzlebox.data.match.ClaimResult
import com.ivandsky.puzzlebox.data.match.CreateResult
import com.ivandsky.puzzlebox.data.match.Match
import com.ivandsky.puzzlebox.data.match.MatchFetch
import com.ivandsky.puzzlebox.data.match.MatchRepository
import com.ivandsky.puzzlebox.data.match.PostResult
import com.ivandsky.puzzlebox.data.match.ResultRead
import com.ivandsky.puzzlebox.data.match.WriteResult
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.testing.contract.MatchResultsContract
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Runs the match contract against [FirestoreMatchRepository] over a fake [MatchStore] that behaves
 * like the server: it stamps `createdAt`, keeps the per-challenger daily count and refuses the writes
 * the rules refuse with `PERMISSION_DENIED`. Then pins the mapping of Firebase failures and the
 * offline post. Robolectric: constructing real Firebase exceptions reaches android.*.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FirestoreMatchRepositoryContractTest : MatchResultsContract() {

    /**
     * Server-side view of `matches`, their results and `matchQuota`. [failures] makes the named
     * call throw; offline, reads and transactions fail with `UNAVAILABLE` and a post queues for good.
     */
    private class FakeMatchStore(
        private val serverNow: () -> Instant,
        private val auth: () -> String?,
    ) : MatchStore {
        private class Queued(val code: String, val fields: Map<String, Any?>, val uid: String, val legIndex: Int)

        private val matches = MutableStateFlow<Map<String, Map<String, Any?>>>(emptyMap())
        private val results = HashMap<String, Map<String, Any?>>()
        private val quotas = HashMap<String, Map<String, Any?>>()
        private val queued = ArrayList<Queued>()
        private val connected = MutableStateFlow(true)

        val puzzles = HashSet<String>()
        val failures = HashMap<String, Exception>()
        private val raceLosses = HashMap<String, Pair<FirebaseFirestoreException.Code, suspend () -> Unit>>()
        val queuedPosts: Int get() = queued.size

        fun disconnect() {
            connected.value = false
        }

        /** Back online, the SDK sends what it queued; the rules judge each write then. */
        fun reconnect() {
            connected.value = true
            queued.forEach { runCatching { commit(it) } }
            queued.clear()
        }

        private fun gate(call: String) {
            failures[call]?.let { throw it }
            if (!connected.value) throw FirebaseFirestoreException("offline", FirebaseFirestoreException.Code.UNAVAILABLE)
        }

        private fun denied() = FirebaseFirestoreException("denied", FirebaseFirestoreException.Code.PERMISSION_DENIED)

        private fun stamp(fields: Map<String, Any?>): Map<String, Any?> {
            val at = serverNow()
            return fields.mapValues { (_, value) ->
                if (value == FieldValue.serverTimestamp()) Timestamp(at.epochSecond, at.nano) else value
            }
        }

        private fun matchOf(code: String, fields: Map<String, Any?>): Match =
            (MatchDocCodec.decode(checkNotNull(ShareCode.parse(code)), fields) as MatchFetch.Found).match

        override suspend fun codeTaken(code: String): Boolean {
            gate("codeTaken")
            return code in puzzles
        }

        override suspend fun matchExists(code: String): Boolean {
            gate("matchExists")
            return code in matches.value
        }

        override suspend fun create(code: String, fields: Map<String, Any?>, uid: String, utcDay: String): MatchCreate =
            rerunOnLostRace {
                gate("create")
                loseRace("create")
                val existing = matches.value[code]
                val count = nextMatchCount(quotas[uid], utcDay)
                when {
                    existing != null -> MatchCreate.Exists(existing[MatchDocCodec.FIELD_CHALLENGER_UID] as String)
                    count == null -> MatchCreate.CapReached
                    fields[MatchDocCodec.FIELD_CHALLENGER_UID] != uid -> throw denied()
                    else -> {
                        matches.value += code to stamp(fields)
                        quotas[uid] = authorFields(utcDay, count, code)
                        MatchCreate.Created
                    }
                }
            }

        /**
         * Reads, yields as the SDK's round trip does, then commits unless another write got there
         * first, which is `ABORTED`; the store reruns it once and reads the winner's seat.
         */
        override suspend fun claim(code: String, fields: Map<String, Any?>, uid: String, now: Instant): SeatWrite =
            rerunOnLostRace {
                gate("claim")
                loseRace("claim")
                val before = matches.value[code]
                val settled = settledClaim(before, uid, now)
                yield()
                if (matches.value[code] !== before) {
                    throw FirebaseFirestoreException("contended", FirebaseFirestoreException.Code.ABORTED)
                }
                if (settled != null) return@rerunOnLostRace settled
                val seat = checkNotNull(before)
                val invited = seat[MatchDocCodec.FIELD_INVITED_UID]
                if (seat[MatchDocCodec.FIELD_CHALLENGER_UID] == uid || (invited != null && invited != uid)) throw denied()
                val participants = (seat[MatchDocCodec.FIELD_PARTICIPANTS] as List<*>) + uid
                val taken = stamp(fields) + (MatchDocCodec.FIELD_PARTICIPANTS to participants.distinct())
                matches.value += code to (seat + taken)
                SeatWrite.Claimed
            }

        /** The next [call] first lets [winner] write, then fails with [code], as a transaction that lost the race. */
        fun loseRaceOnce(call: String, code: FirebaseFirestoreException.Code, winner: suspend () -> Unit) {
            raceLosses[call] = code to winner
        }

        private suspend fun loseRace(call: String) {
            val (code, winner) = raceLosses.remove(call) ?: return
            winner()
            throw FirebaseFirestoreException("lost the race", code)
        }

        override suspend fun cancel(code: String, uid: String): SeatWrite {
            gate("cancel")
            val seat = matches.value[code]
            settledCancel(seat, uid)?.let { return it }
            if (seat?.get(MatchDocCodec.FIELD_CHALLENGER_UID) != uid || seat[MatchDocCodec.FIELD_OPPONENT_UID] != null) throw denied()
            matches.value += code to (seat + (MatchDocCodec.FIELD_CANCELLED to true))
            return SeatWrite.Done
        }

        override suspend fun decline(code: String, uid: String): SeatWrite {
            gate("decline")
            val seat = matches.value[code]
            settledDecline(seat, uid)?.let { return it }
            if (seat?.get(MatchDocCodec.FIELD_INVITED_UID) != uid || seat[MatchDocCodec.FIELD_OPPONENT_UID] != null) throw denied()
            matches.value += code to (seat + (MatchDocCodec.FIELD_DECLINED to true))
            return SeatWrite.Done
        }

        override fun observe(code: String): Flow<MatchSnapshot> = combine(matches, connected) { all, online ->
            when {
                !online -> MatchSnapshot.Unconfirmed
                else -> all[code]?.let(MatchSnapshot::Doc) ?: MatchSnapshot.Absent
            }
        }

        override fun observeMine(uid: String): Flow<Map<String, Map<String, Any?>>> = matches.map { all ->
            all.filterValues { uid in (it[MatchDocCodec.FIELD_PARTICIPANTS] as List<*>) }
        }

        override suspend fun post(code: String, fields: Map<String, Any?>, uid: String, legIndex: Int) {
            failures["post"]?.let { throw it }
            val write = Queued(code, fields, uid, legIndex)
            if (!connected.value) {
                queued += write
                awaitCancellation()
            }
            commit(write)
        }

        /** The result document and the progress entry, or neither: what the rules allow of one batch. */
        private fun commit(write: Queued) {
            val before = matches.value[write.code] ?: throw denied()
            val match = matchOf(write.code, before)
            val result = checkNotNull(MatchDocCodec.decodeResult(write.fields))
            val key = "${write.code}/${MatchDocCodec.resultId(write.legIndex, write.uid)}"
            val seated = write.uid == match.challenger.uid || write.uid == match.opponent?.uid
            val wellFormed = result.hints >= 0 && if (result.gaveUp) result.timeMs == 0L else result.timeMs > 0
            val refused = !seated || match.cancelled || match.declined || result.uid != write.uid ||
                write.legIndex !in match.legs.indices || key in results || !wellFormed ||
                !serverNow().isBefore(match.closesAt) || result.completedAtMs > match.deadline.toEpochMilli()
            if (refused) throw denied()
            results[key] = write.fields
            val legs = match.progress[write.uid].orEmpty() + write.legIndex
            val progress = (match.progress + (write.uid to legs)).mapValues { (_, done) -> done.map(Int::toLong) }
            matches.value += write.code to (before + (MatchDocCodec.FIELD_PROGRESS to progress))
        }

        override suspend fun readResult(code: String, legIndex: Int, uid: String): Map<String, Any?>? {
            gate("readResult")
            val result = results["$code/${MatchDocCodec.resultId(legIndex, uid)}"] ?: return null
            val me = auth()
            val participants = matches.value[code]?.get(MatchDocCodec.FIELD_PARTICIPANTS) as? List<*> ?: emptyList<Any?>()
            val closed = matches.value[code]?.let { !serverNow().isBefore(matchOf(code, it).closesAt) } == true
            val own = me?.let { "$code/${MatchDocCodec.resultId(legIndex, it)}" in results } == true
            if (me != uid && !(me in participants && (own || closed))) throw denied()
            return result
        }
    }

    private var signedIn: String? = null
    private var now: Instant = START
    private val store = FakeMatchStore(serverNow = { now }, auth = { signedIn })

    override val repository: MatchRepository =
        FirestoreMatchRepository(store = store, currentUid = { signedIn }, clock = { now })

    override fun signInAs(uid: String) {
        signedIn = uid
    }

    override fun signOut() {
        signedIn = null
    }

    override fun goOffline() = store.disconnect()

    override fun setNow(now: Instant) {
        this.now = now
    }

    override fun occupyPuzzleCode(code: ShareCode) {
        store.puzzles += code.value
    }

    private fun denied() = FirebaseFirestoreException("denied", FirebaseFirestoreException.Code.PERMISSION_DENIED)

    // ----- Firebase failures -----

    @Test
    fun `a transient store failure on create is offline`() = runTest {
        signInAs(ANN.uid)
        store.failures["create"] = FirebaseFirestoreException("offline", FirebaseFirestoreException.Code.UNAVAILABLE)

        assertEquals(CreateResult.Offline, repository.create(draft()))
        assertEquals(MatchFetch.NotFound, fetch())
    }

    @Test
    fun `a transient store failure on claim is offline`() = runTest {
        created()
        store.failures["claim"] = FirebaseNetworkException("offline")

        assertEquals(ClaimResult.Offline, by(BOB) { claim(code(), BOB) })
        assertMatch(match())
    }

    @Test
    fun `a transient store failure on post is unavailable`() = runTest {
        claimed()
        store.failures["post"] = FirebaseFirestoreException("offline", FirebaseFirestoreException.Code.UNAVAILABLE)

        assertTrue(repository.post(code(), result(BOB.uid)) is PostResult.Unavailable)
        assertEquals(ResultRead.Missing, read(BOB))
    }

    @Test
    fun `a failure that is not transient or denied is rejected`() = runTest {
        created()
        store.failures["cancel"] = FirebaseFirestoreException("gone", FirebaseFirestoreException.Code.NOT_FOUND)

        assertTrue(repository.cancel(code()) is WriteResult.Rejected)
        assertMatch(match())
    }

    // ----- why the rules refused a post -----

    @Test
    fun `a denied post with a completion after the deadline is late`() = runTest {
        claimed()
        store.failures["post"] = denied()

        val tooLate = result(BOB.uid, completedAt = match().deadline.plusMillis(1))

        assertEquals(PostResult.Late, repository.post(code(), tooLate))
    }

    @Test
    fun `a denied post is late from a minute before the close on the device clock and rejected before`() = runTest {
        claimed()
        store.failures["post"] = denied()

        setNow(match().closesAt.minusSeconds(61))
        assertTrue(repository.post(code(), result(BOB.uid)) is PostResult.Rejected)
        setNow(match().closesAt.minusSeconds(60))
        assertEquals(PostResult.Late, repository.post(code(), result(BOB.uid)))
    }

    @Test
    fun `a denied post whose result already exists is a duplicate`() = runTest {
        claimed()
        val first = posted(BOB)
        store.failures["post"] = denied()

        assertEquals(PostResult.Duplicate, repository.post(code(), result(BOB.uid, timeMs = 5_000)))
        assertEquals(ResultRead.Found(first), read(BOB))
    }

    @Test
    fun `a post that never completes offline becomes unavailable after the timeout`() = runTest {
        claimed()
        goOffline()
        val mine = result(BOB.uid)

        assertTrue(repository.post(code(), mine) is PostResult.Unavailable)
        assertEquals(1, store.queuedPosts)

        store.reconnect()
        assertEquals(PostResult.Duplicate, repository.post(code(), mine))
        assertEquals(ResultRead.Found(mine), read(BOB))
        assertMatch(match(opponent = BOB).copy(progress = mapOf(BOB.uid to setOf(0))))
    }

    @Test
    fun `an absent result is missing and one the reader may not see is sealed, by the rules`() = runTest {
        claimed()
        posted(BOB)

        assertEquals(ResultRead.Sealed, by(ANN) { readResult(code(), 0, BOB.uid) })
        assertEquals(ResultRead.Missing, by(ANN) { readResult(code(), 1, BOB.uid) })
    }

    // ----- reads -----

    @Test
    fun `a permission denied read of a result is sealed`() = runTest {
        claimed()
        posted(BOB)
        store.failures["readResult"] = denied()

        assertEquals(ResultRead.Sealed, by(ANN) { readResult(code(), 0, BOB.uid) })
    }

    @Test
    fun `a coroutine cancellation is rethrown, never mapped`() = runTest {
        claimed()
        val calls = listOf<Pair<String, suspend () -> Any?>>(
            "codeTaken" to { repository.puzzleCodeTaken(code()) },
            "create" to { repository.create(draft(code = code(OTHER_CODE), challenger = BOB)) },
            "claim" to { repository.claim(code(), BOB) },
            "cancel" to { repository.cancel(code()) },
            "decline" to { repository.decline(code()) },
            "post" to { repository.post(code(), result(BOB.uid)) },
            "readResult" to { repository.readResult(code(), 0, BOB.uid) },
        )

        for ((name, call) in calls) {
            store.failures[name] = CancellationException("stop")
            try {
                call()
                fail("$name swallowed the cancellation")
            } catch (e: CancellationException) {
                assertEquals("stop", e.message)
            }
            store.failures.clear()
        }
    }

    @Test
    fun `observeMine is null for a signed-out user and re-emits when the store changes`() = runTest {
        signOut()
        assertNull(repository.observeMine().first())

        signInAs(ANN.uid)
        repository.observeMine().test {
            assertEquals(emptyList<Match>(), awaitItem())

            created()
            assertEquals(listOf(match()), awaitItem())

            assertEquals(ClaimResult.Claimed, by(BOB) { claim(code(), BOB) })
            assertEquals(listOf(match(opponent = BOB)), awaitItem())
        }
    }

    // ----- races -----

    @Test
    fun `of two simultaneous claims one wins and the other is rerun and finds the seat taken`() = runTest {
        created()
        val bob = FirestoreMatchRepository(store = store, currentUid = { BOB.uid }, clock = { now })
        val cat = FirestoreMatchRepository(store = store, currentUid = { CAT.uid }, clock = { now })

        val outcomes = listOf(async { bob.claim(code(), BOB) }, async { cat.claim(code(), CAT) }).awaitAll()

        assertEquals(listOf(ClaimResult.Claimed, ClaimResult.Taken), outcomes)
        assertMatch(match(opponent = BOB))
    }

    @Test
    fun `a claim that loses the race to ABORTED is rerun and finds the seat taken`() = runTest {
        lostClaimRace(FirebaseFirestoreException.Code.ABORTED)
    }

    @Test
    fun `a claim that loses the race to FAILED_PRECONDITION is rerun and finds the seat taken`() = runTest {
        lostClaimRace(FirebaseFirestoreException.Code.FAILED_PRECONDITION)
    }

    @Test
    fun `a claim that loses the race to ALREADY_EXISTS is rerun and finds the seat taken`() = runTest {
        lostClaimRace(FirebaseFirestoreException.Code.ALREADY_EXISTS)
    }

    @Test
    fun `a create that loses the race to ABORTED is rerun and finds the code taken`() = runTest {
        lostCreateRace(FirebaseFirestoreException.Code.ABORTED)
    }

    @Test
    fun `a create that loses the race to FAILED_PRECONDITION is rerun and finds the code taken`() = runTest {
        lostCreateRace(FirebaseFirestoreException.Code.FAILED_PRECONDITION)
    }

    @Test
    fun `a create that loses the race to ALREADY_EXISTS is rerun and finds the code taken`() = runTest {
        lostCreateRace(FirebaseFirestoreException.Code.ALREADY_EXISTS)
    }

    private fun repositoryOf(uid: String) = FirestoreMatchRepository(store = store, currentUid = { uid }, clock = { now })

    private suspend fun lostClaimRace(lost: FirebaseFirestoreException.Code) {
        created()
        store.loseRaceOnce("claim", lost) { assertEquals(ClaimResult.Claimed, repositoryOf(CAT.uid).claim(code(), CAT)) }

        assertEquals(ClaimResult.Taken, repositoryOf(BOB.uid).claim(code(), BOB))
        assertMatch(match(opponent = CAT))
    }

    private suspend fun lostCreateRace(lost: FirebaseFirestoreException.Code) {
        signInAs(ANN.uid)
        store.loseRaceOnce("create", lost) {
            assertEquals(CreateResult.Created(code()), repositoryOf(BOB.uid).create(draft(challenger = BOB)))
        }

        assertEquals(CreateResult.CodeTaken, repository.create(draft()))
        assertEquals(BOB, (fetch() as MatchFetch.Found).match.challenger)
    }
}
