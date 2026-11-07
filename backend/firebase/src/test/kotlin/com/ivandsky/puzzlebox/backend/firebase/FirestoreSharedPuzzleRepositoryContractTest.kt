package com.ivandsky.puzzlebox.backend.firebase

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.ivandsky.puzzlebox.data.model.SharedPuzzleDoc
import com.ivandsky.puzzlebox.data.repository.PublishResult
import com.ivandsky.puzzlebox.data.repository.SharedFetchResult
import com.ivandsky.puzzlebox.data.repository.SharedPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.UnpublishResult
import com.ivandsky.puzzlebox.testing.contract.SharedPuzzleRepositoryContract
import java.util.Base64
import java.util.Date
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Runs [SharedPuzzleRepositoryContract] against [FirestoreSharedPuzzleRepository] over a fake
 * [SharedPuzzleStore] that behaves like the server: it stamps `createdAt`, keeps the per-author
 * daily count and refuses writes the rules refuse. Then pins the codec envelope and the mapping
 * of real Firebase exceptions. Robolectric: constructing real Firebase exceptions reaches android.*.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FirestoreSharedPuzzleRepositoryContractTest : SharedPuzzleRepositoryContract() {

    /** Server-side view of `puzzles/{code}` and `authors/{uid}`, with a knob to fail every call. */
    private class FakeSharedPuzzleStore(
        private val auth: () -> String?,
        private val serverNow: Timestamp,
    ) : SharedPuzzleStore {
        val puzzles = LinkedHashMap<String, Map<String, Any?>>()
        val authors = HashMap<String, Map<String, Any?>>()
        var failure: Exception? = null

        fun write(code: String, fields: Map<String, Any?>) {
            puzzles[code] = fields.mapValues { (_, value) -> if (value == FieldValue.serverTimestamp()) serverNow else value }
        }

        override suspend fun publish(code: String, fields: Map<String, Any?>, uid: String, utcDay: String): StoreWrite {
            failure?.let { throw it }
            puzzles[code]?.let { return StoreWrite.Exists(it[SharedPuzzleDocCodec.FIELD_AUTHOR_UID] as String) }
            val count = nextPublishCount(authors[uid], utcDay) ?: return StoreWrite.CapReached
            write(code, fields)
            authors[uid] = authorFields(utcDay, count)
            return StoreWrite.Created
        }

        override suspend fun unpublish(code: String) {
            failure?.let { throw it }
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
    }

    private var signedIn: String? = null
    private var today = DAY
    private val serverNow = Timestamp(Date(doc().createdAtMs))
    private val store = FakeSharedPuzzleStore(auth = { signedIn }, serverNow = serverNow)

    override val repository: SharedPuzzleRepository =
        FirestoreSharedPuzzleRepository(store = store, currentUid = { signedIn }, utcDay = { today })

    override fun signInAs(uid: String) {
        signedIn = uid
    }

    override suspend fun seed(doc: SharedPuzzleDoc) {
        store.write(doc.code.value, SharedPuzzleDocCodec.encode(doc))
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
        assertEquals(authorFields(NEXT_DAY, 1), store.authors[AUTHOR])
    }

    @Test
    fun `a retry of an existing own code does not count against the cap`() = runTest {
        signInAs(AUTHOR)
        repository.publish(doc())

        assertEquals(PublishResult.Published(code()), repository.publish(doc()))
        assertEquals(authorFields(DAY, 1), store.authors[AUTHOR])
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
    }

    @Test
    fun `a network failure maps to Offline on every call`() = runTest {
        signInAs(AUTHOR)
        store.failure = FirebaseNetworkException("offline")

        assertEquals(PublishResult.Offline, repository.publish(doc()))
        assertEquals(UnpublishResult.Offline, repository.unpublish(code()))
        assertEquals(SharedFetchResult.Offline, repository.fetch(code()))
    }

    @Test
    fun `a PERMISSION_DENIED commit below the cap is Rejected`() = runTest {
        signInAs(AUTHOR)
        store.failure = FirebaseFirestoreException("denied", FirebaseFirestoreException.Code.PERMISSION_DENIED)

        assertTrue(repository.publish(doc()) is PublishResult.Rejected)
        assertTrue(repository.unpublish(code()) is UnpublishResult.Rejected)
    }

    private companion object {
        const val DAY = "2026-11-06"
        const val NEXT_DAY = "2026-11-07"
        const val CAP_SUFFIXES = "0123456789ABCDEFGHJKM"
    }
}
