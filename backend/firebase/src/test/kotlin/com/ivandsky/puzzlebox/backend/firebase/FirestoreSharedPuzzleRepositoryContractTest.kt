package com.ivandsky.puzzlebox.backend.firebase

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.ivandsky.puzzlebox.data.model.SharedPuzzleDoc
import com.ivandsky.puzzlebox.data.repository.COMMUNITY_WINDOW_MS
import com.ivandsky.puzzlebox.data.repository.CommunityFilter
import com.ivandsky.puzzlebox.data.repository.CommunityListResult
import com.ivandsky.puzzlebox.data.repository.PublishResult
import com.ivandsky.puzzlebox.data.repository.ReportResult
import com.ivandsky.puzzlebox.data.repository.SharedFetchResult
import com.ivandsky.puzzlebox.data.repository.SharedPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.UnpublishResult
import com.ivandsky.puzzlebox.testing.contract.SharedPuzzleCommunityContract
import com.ivandsky.puzzlebox.game.editor.Difficulty
import java.io.File
import java.util.Base64
import java.util.Date
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Runs [SharedPuzzleCommunityContract], and so the whole shared-puzzle contract, against
 * [FirestoreSharedPuzzleRepository] over a fake [SharedPuzzleStore] that behaves like the server:
 * it stamps `createdAt`, keeps the per-author daily count and refuses the writes, lists and
 * reports the rules refuse. Then pins the codec envelope, the mapping of real Firebase exceptions
 * and the index each list query needs.
 * Robolectric: constructing real Firebase exceptions reaches android.*.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FirestoreSharedPuzzleRepositoryContractTest : SharedPuzzleCommunityContract() {

    /**
     * Server-side view of `puzzles/{code}`, its reports and `authors/{uid}`, with a knob to fail
     * every call. The list and report checks spell the rules' own words, not the client's constants.
     */
    private class FakeSharedPuzzleStore(
        private val auth: () -> String?,
        private val serverNow: Timestamp,
    ) : SharedPuzzleStore {
        val puzzles = LinkedHashMap<String, Map<String, Any?>>()
        val authors = HashMap<String, Map<String, Any?>>()
        var failure: Exception? = null
        var unpublishFailure: Exception? = null
        val reports = HashSet<Pair<String, String>>()
        val queries = mutableListOf<CommunityQuery>()

        fun write(code: String, fields: Map<String, Any?>) {
            puzzles[code] = fields.mapValues { (_, value) -> if (value == FieldValue.serverTimestamp()) serverNow else value }
        }

        override suspend fun publish(code: String, fields: Map<String, Any?>, uid: String, utcDay: String): StoreWrite {
            failure?.let { throw it }
            puzzles[code]?.let { return StoreWrite.Exists(it[SharedPuzzleDocCodec.FIELD_AUTHOR_UID] as String, it) }
            val count = nextPublishCount(authors[uid], utcDay) ?: return StoreWrite.CapReached
            write(code, fields)
            authors[uid] = authorFields(utcDay, count, code)
            return StoreWrite.Created
        }

        override suspend fun unpublish(code: String) {
            failure?.let { throw it }
            unpublishFailure?.let { throw it }
            val existing = puzzles[code]
                ?: throw FirebaseFirestoreException("no puzzle $code", FirebaseFirestoreException.Code.NOT_FOUND)
            if (existing[SharedPuzzleDocCodec.FIELD_AUTHOR_UID] != auth()) {
                throw FirebaseFirestoreException("not the author", FirebaseFirestoreException.Code.PERMISSION_DENIED)
            }
            puzzles[code] = existing + (SharedPuzzleDocCodec.FIELD_UNPUBLISHED to true)
        }

        override suspend fun get(code: String): Map<String, Any?>? {
            failure?.let { throw it }
            return puzzles[code]
        }

        override suspend fun list(query: CommunityQuery): List<Pair<String, Map<String, Any?>>> {
            failure?.let { throw it }
            queries += query
            val provesTheListRule = query.equalTo.contains("visibility" to "public") &&
                query.equalTo.contains("unpublished" to false) &&
                query.limit <= 30
            if (!provesTheListRule) throw denied("the query can return docs the list rule hides")
            return puzzles.entries
                .filter { (_, fields) ->
                    query.equalTo.all { (field, value) -> fields[field] == value } &&
                        query.anyOf?.let { (field, values) -> values.any { it == fields[field] } } != false &&
                        query.createdSince?.let { fields.getValue("createdAt") as Timestamp >= it } != false
                }
                .sortedByDescending { it.value.getValue("createdAt") as Timestamp }
                .take(query.limit)
                .map { it.key to it.value }
        }

        override suspend fun report(code: String, uid: String, fields: Map<String, Any?>) {
            failure?.let { throw it }
            val puzzle = puzzles[code]
            val valid = auth() == uid &&
                puzzle != null &&
                puzzle["authorUid"] != uid &&
                fields == mapOf("formatVersion" to 1, "uid" to uid, "createdAt" to FieldValue.serverTimestamp())
            // The rules allow create only, so a second write to the same reports/{uid} is refused.
            if (!valid || !reports.add(code to uid)) throw denied("report refused")
        }

        private fun denied(message: String) =
            FirebaseFirestoreException(message, FirebaseFirestoreException.Code.PERMISSION_DENIED)
    }

    private var signedIn: String? = null
    private var today = DAY
    private var now = NOW
    private val serverNow = Timestamp(Date(doc().createdAtMs))
    private val store = FakeSharedPuzzleStore(auth = { signedIn }, serverNow = serverNow)

    override val repository: SharedPuzzleRepository = FirestoreSharedPuzzleRepository(
        store = store,
        currentUid = { signedIn },
        utcDay = { today },
        nowMs = { now },
    )

    override fun signInAs(uid: String) {
        signedIn = uid
    }

    override suspend fun seed(doc: SharedPuzzleDoc) {
        val createdAt = SharedPuzzleDocCodec.FIELD_CREATED_AT to Timestamp(Date(doc.createdAtMs))
        store.write(doc.code.value, SharedPuzzleDocCodec.encode(doc) + createdAt)
    }

    override fun goOffline() {
        store.failure = FirebaseFirestoreException("offline", FirebaseFirestoreException.Code.UNAVAILABLE)
    }

    override fun setNow(nowMs: Long) {
        now = nowMs
    }

    // ----- codec -----

    @Test
    fun `the codec round-trips a doc with its payload bytes`() {
        val fields = SharedPuzzleDocCodec.encode(doc()) + (SharedPuzzleDocCodec.FIELD_CREATED_AT to serverNow)

        val found = SharedPuzzleDocCodec.decode(code(), fields) as SharedFetchResult.Found

        assertEquals(doc(), found.doc)
        assertArrayEquals(doc().payload, found.doc.payload)
    }

    @Test
    fun `the codec writes the payload as base64 and createdAt as the server timestamp`() {
        val fields = SharedPuzzleDocCodec.encode(doc())

        assertEquals(Base64.getEncoder().encodeToString(doc().payload), fields[SharedPuzzleDocCodec.FIELD_PAYLOAD])
        assertSame(FieldValue.serverTimestamp(), fields[SharedPuzzleDocCodec.FIELD_CREATED_AT])
        assertEquals(SharedPuzzleDoc.FORMAT_VERSION, (fields[SharedPuzzleDocCodec.FIELD_FORMAT_VERSION] as Number).toInt())
    }

    @Test
    fun `the codec rejects formatVersion 2 as unsupported`() {
        val fields = SharedPuzzleDocCodec.encode(doc()) + mapOf(
            SharedPuzzleDocCodec.FIELD_CREATED_AT to serverNow,
            SharedPuzzleDocCodec.FIELD_FORMAT_VERSION to 2L,
        )

        assertEquals(SharedFetchResult.UnsupportedVersion(2), SharedPuzzleDocCodec.decode(code(), fields))
    }

    @Test
    fun `a v1 doc missing fields or with a broken payload is NotFound`() {
        val fields = SharedPuzzleDocCodec.encode(doc()) + (SharedPuzzleDocCodec.FIELD_CREATED_AT to serverNow)

        assertEquals(SharedFetchResult.NotFound, SharedPuzzleDocCodec.decode(code(), fields - SharedPuzzleDocCodec.FIELD_TITLE))
        assertEquals(
            SharedFetchResult.NotFound,
            SharedPuzzleDocCodec.decode(code(), fields + (SharedPuzzleDocCodec.FIELD_PAYLOAD to "not base64!")),
        )
    }

    // ----- publish -----

    @Test
    fun `the 21st create in a UTC day is DailyCapReached and writes nothing, the next day resets`() = runTest {
        signInAs(AUTHOR)
        val codes = CAP_SUFFIXES.map { code("K7Q2M$it") }

        codes.take(20).forEach { assertEquals(PublishResult.Published(it), repository.publish(doc(code = it))) }
        assertEquals(PublishResult.DailyCapReached, repository.publish(doc(code = codes.last())))
        assertEquals(SharedFetchResult.NotFound, repository.fetch(codes.last()))

        today = NEXT_DAY
        assertEquals(PublishResult.Published(codes.last()), repository.publish(doc(code = codes.last())))
        assertEquals(authorFields(NEXT_DAY, 1, codes.last().value), store.authors[AUTHOR])
    }

    @Test
    fun `a retry of an existing own code does not count against the cap`() = runTest {
        signInAs(AUTHOR)
        repository.publish(doc())

        assertEquals(PublishResult.Published(code()), repository.publish(doc()))
        assertEquals(authorFields(DAY, 1, code().value), store.authors[AUTHOR])
    }

    @Test
    fun `a publish writes the author's bump naming the code it created`() = runTest {
        signInAs(AUTHOR)
        val first = code("K7Q2MA")
        val second = code("K7Q2MB")

        repository.publish(doc(code = first))
        assertEquals(
            mapOf("day" to DAY, "count" to 1L, "last" to first.value),
            store.authors[AUTHOR],
        )

        repository.publish(doc(code = second))
        assertEquals(
            mapOf("day" to DAY, "count" to 2L, "last" to second.value),
            store.authors[AUTHOR],
        )
    }

    @Test
    fun `an existing own code with different content is CodeTaken and its doc is unpublished`() = runTest {
        signInAs(AUTHOR)
        val edits = listOf(
            doc().copy(title = "Edited"),
            doc().copy(note = "Edited note"),
            doc().copy(authorName = "Edited name"),
            doc().copy(authorTimeMs = doc().authorTimeMs + 1),
            doc().copy(payload = doc().payload + 1),
            doc().copy(variant = "other"),
            doc().copy(gameId = "other"),
            doc().copy(shareCodecVersion = doc().shareCodecVersion + 1),
            doc().copy(visibility = SharedPuzzleDoc.Visibility.Public),
            doc().copy(difficulty = Difficulty.Level.Hard),
        )

        edits.forEach {
            repository.publish(doc())
            assertEquals(PublishResult.CodeTaken, repository.publish(it))
            assertEquals(SharedFetchResult.Found(doc(unpublished = true)), repository.fetch(code()))
            store.puzzles.clear()
        }
    }

    @Test
    fun `a failed unpublish still answers CodeTaken, leaves the doc and lets a fresh code publish`() = runTest {
        signInAs(AUTHOR)
        repository.publish(doc())
        val fresh = code("ZZZ999")
        store.unpublishFailure = FirebaseFirestoreException("denied", FirebaseFirestoreException.Code.PERMISSION_DENIED)

        assertEquals(PublishResult.CodeTaken, repository.publish(doc(title = "Retry")))
        assertEquals(SharedFetchResult.Found(doc()), repository.fetch(code()))
        assertEquals(PublishResult.Published(fresh), repository.publish(doc(code = fresh, title = "Retry")))
    }

    @Test
    fun `an existing own code with equal content and a later createdAt is Published`() = runTest {
        signInAs(AUTHOR)
        repository.publish(doc())

        assertEquals(PublishResult.Published(code()), repository.publish(doc().copy(createdAtMs = doc().createdAtMs + 1)))
    }

    @Test
    fun `a public puzzle fetches back public with its difficulty and a retry of it is Published`() = runTest {
        signInAs(AUTHOR)
        val public = doc().copy(visibility = SharedPuzzleDoc.Visibility.Public, difficulty = Difficulty.Level.Hard)

        assertEquals(PublishResult.Published(code()), repository.publish(public))
        val fetched = (repository.fetch(code()) as SharedFetchResult.Found).doc
        assertEquals(SharedPuzzleDoc.Visibility.Public, fetched.visibility)
        assertEquals(Difficulty.Level.Hard, fetched.difficulty)
        assertEquals(PublishResult.Published(code()), repository.publish(public))
        assertEquals(authorFields(DAY, 1, code().value), store.authors[AUTHOR])
    }

    @Test
    fun `a public retry of a link-only puzzle is CodeTaken, so a link-only code never turns public`() = runTest {
        signInAs(AUTHOR)
        repository.publish(doc())

        assertEquals(PublishResult.CodeTaken, repository.publish(doc().copy(visibility = SharedPuzzleDoc.Visibility.Public)))
        assertEquals(SharedPuzzleDoc.Visibility.LinkOnly, (repository.fetch(code()) as SharedFetchResult.Found).doc.visibility)
    }

    @Test
    fun `signed out publish is SignedOut and unpublish is Rejected`() = runTest {
        signInAs(AUTHOR)
        repository.publish(doc())
        signedIn = null

        assertEquals(PublishResult.SignedOut, repository.publish(doc(code = code("ZZZ999"))))
        assertTrue(repository.unpublish(code()) is UnpublishResult.Rejected)
        assertEquals(SharedFetchResult.Found(doc()), repository.fetch(code()))
    }

    @Test
    fun `a doc authored by someone else than the signed-in user is Rejected`() = runTest {
        signInAs(OTHER)

        assertTrue(repository.publish(doc(authorUid = AUTHOR)) is PublishResult.Rejected)
        assertTrue(store.puzzles.isEmpty())
    }

    @Test
    fun `unpublishing an absent or another author's code is Rejected`() = runTest {
        signInAs(AUTHOR)
        repository.publish(doc())
        signInAs(OTHER)

        assertTrue(repository.unpublish(code()) is UnpublishResult.Rejected)
        assertTrue(repository.unpublish(code("ZZZ999")) is UnpublishResult.Rejected)
        assertEquals(SharedFetchResult.Found(doc()), repository.fetch(code()))
    }

    // ----- Firebase exception mapping -----

    @Test
    fun `UNAVAILABLE maps to Offline on every call`() = runTest {
        signInAs(AUTHOR)
        store.failure = FirebaseFirestoreException("offline", FirebaseFirestoreException.Code.UNAVAILABLE)

        assertEquals(PublishResult.Offline, repository.publish(doc()))
        assertEquals(UnpublishResult.Offline, repository.unpublish(code()))
        assertEquals(SharedFetchResult.Offline, repository.fetch(code()))
        assertEquals(CommunityListResult.Offline, repository.listPublic(CommunityFilter()))
        assertEquals(ReportResult.Offline, repository.report(code()))
    }

    @Test
    fun `a network failure maps to Offline on every call`() = runTest {
        signInAs(AUTHOR)
        store.failure = FirebaseNetworkException("offline")

        assertEquals(PublishResult.Offline, repository.publish(doc()))
        assertEquals(UnpublishResult.Offline, repository.unpublish(code()))
        assertEquals(SharedFetchResult.Offline, repository.fetch(code()))
        assertEquals(CommunityListResult.Offline, repository.listPublic(CommunityFilter()))
        assertEquals(ReportResult.Offline, repository.report(code()))
    }

    @Test
    fun `a PERMISSION_DENIED commit below the cap is Rejected`() = runTest {
        signInAs(AUTHOR)
        store.failure = FirebaseFirestoreException("denied", FirebaseFirestoreException.Code.PERMISSION_DENIED)

        assertTrue(repository.publish(doc()) is PublishResult.Rejected)
        assertTrue(repository.unpublish(code()) is UnpublishResult.Rejected)
    }

    @Test
    fun `a list the server refuses or has no index for is Refused`() = runTest {
        listOf(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            FirebaseFirestoreException.Code.FAILED_PRECONDITION,
        ).forEach {
            store.failure = FirebaseFirestoreException("refused", it)

            assertEquals(CommunityListResult.Refused, repository.listPublic(CommunityFilter()))
        }
    }

    @Test
    fun `a report that fails for another reason than a denial is Rejected with the reason`() = runTest {
        signInAs(OTHER)
        store.failure = FirebaseFirestoreException("bad report", FirebaseFirestoreException.Code.INVALID_ARGUMENT)

        assertEquals(ReportResult.Rejected("bad report"), repository.report(code()))
    }

    // ----- community list -----

    @Test
    fun `every filter asks for a query that has a deployed composite index`() = runTest {
        val filters = listOf(
            CommunityFilter(),
            CommunityFilter(gameId = "queens"),
            CommunityFilter(difficulties = HARD_CHIP),
            CommunityFilter(gameId = "queens", difficulties = HARD_CHIP),
        )

        filters.forEach { filter ->
            repository.listPublic(filter)
            val query = store.queries.last()
            val needed = query.equalTo.map { it.first to "ASCENDING" } +
                listOfNotNull(query.anyOf?.let { it.first to "ASCENDING" }) +
                ("createdAt" to "DESCENDING")

            assertTrue("no index $needed for $filter", needed in deployedPuzzleIndexes())
        }
        assertEquals(4, store.queries.map { it.equalTo.size to (it.anyOf != null) }.distinct().size)
    }

    @Test
    fun `a filter with no window builds no createdSince and the default one starts 24 hours back`() = runTest {
        repository.listPublic(CommunityFilter(windowMs = null))
        repository.listPublic(CommunityFilter())

        assertEquals(
            listOf(null, Timestamp(Date(NOW - COMMUNITY_WINDOW_MS))),
            store.queries.map { it.createdSince },
        )
    }

    @Test
    fun `difficulties go to the server in their stored words, easiest first`() = runTest {
        repository.listPublic(CommunityFilter(difficulties = setOf(Difficulty.Level.Expert, Difficulty.Level.Easy)))

        assertEquals("difficulty" to listOf("easy", "expert"), store.queries.single().anyOf)
    }

    @Test
    fun `a listed doc that does not parse is left out and the rest are returned`() = runTest {
        seed(publicDoc("K7Q2MA", ageMs = 2 * HOUR_MS))
        seed(publicDoc("K7Q2MB", ageMs = 1 * HOUR_MS))
        store.puzzles["K7Q2MB"] = store.puzzles.getValue("K7Q2MB") - SharedPuzzleDocCodec.FIELD_TITLE
        store.puzzles["not a code"] = store.puzzles.getValue("K7Q2MA")

        assertEquals(
            CommunityListResult.Listed(listOf(publicDoc("K7Q2MA", ageMs = 2 * HOUR_MS))),
            repository.listPublic(CommunityFilter()),
        )
    }

    /** Each `puzzles` index in firestore.indexes.json as its fields and their orders. */
    private fun deployedPuzzleIndexes(): List<List<Pair<String, String>>> {
        val indexes = JSONObject(File("firestore.indexes.json").readText()).getJSONArray("indexes")
        return (0 until indexes.length()).map(indexes::getJSONObject)
            .filter { it.getString("collectionGroup") == "puzzles" && it.getString("queryScope") == "COLLECTION" }
            .map { index ->
                val fields = index.getJSONArray("fields")
                (0 until fields.length()).map(fields::getJSONObject).map { it.getString("fieldPath") to it.getString("order") }
            }
    }

    private companion object {
        const val DAY = "2026-11-06"
        const val NEXT_DAY = "2026-11-07"
        const val CAP_SUFFIXES = "0123456789ABCDEFGHJKM"
    }
}
