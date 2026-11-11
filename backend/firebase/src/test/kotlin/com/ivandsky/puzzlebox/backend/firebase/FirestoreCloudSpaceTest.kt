package com.ivandsky.puzzlebox.backend.firebase

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import com.ivandsky.puzzlebox.data.cloud.CloudDoc
import com.ivandsky.puzzlebox.data.cloud.CloudResult
import com.ivandsky.puzzlebox.data.cloud.CloudSpace
import com.ivandsky.puzzlebox.testing.contract.CloudSpaceContract
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Runs [CloudSpaceContract] against [FirestoreCloudSpace] over a [FakeCloudSpaceStore], then pins what the adapter adds:
 * the error table, the uid check before every commit, the 400-write batches and the SDK value conversion.
 * Robolectric: constructing real Firebase exceptions reaches android.*.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FirestoreCloudSpaceTest : CloudSpaceContract() {

    private var signedIn: String? = null
    private val store = FakeCloudSpaceStore()
    private val adapter = FirestoreCloudSpace(store, currentUid = { signedIn }, timeout = 10.seconds)

    override val space: CloudSpace get() = adapter

    override fun signInAs(uid: String) {
        signedIn = uid
    }

    override fun setNow(nowMs: Long) {
        store.clock = { nowMs }
    }

    private fun doc(id: String, n: Long = 1L) = CloudDoc(id, mapOf("n" to n))

    private fun failure(code: Code) = FirebaseFirestoreException(code.name, code)

    /** One call of each kind: read, put, delete and deleteAll. */
    private val everyCall: List<suspend () -> CloudResult<*>> = listOf(
        { space.changedSince(ANN, "drafts", null) },
        { space.put(ANN, "drafts", listOf(doc("a"))) },
        { space.delete(ANN, "drafts", listOf("a")) },
        { space.deleteAll(ANN, listOf("drafts")) },
    )

    @Test
    fun `the error table maps every failure of every call to a typed result`() = runTest {
        val table = mapOf<Throwable, CloudResult<*>>(
            failure(Code.RESOURCE_EXHAUSTED) to CloudResult.Exhausted,
            failure(Code.PERMISSION_DENIED) to CloudResult.Denied,
            failure(Code.UNAUTHENTICATED) to CloudResult.Denied,
            failure(Code.UNAVAILABLE) to CloudResult.Offline,
            failure(Code.DEADLINE_EXCEEDED) to CloudResult.Offline,
            FirebaseNetworkException("down") to CloudResult.Offline,
            IllegalStateException("not a plain value") to CloudResult.Denied,
        )
        for ((error, expected) in table) {
            val results = everyCall.map { call ->
                store.failNext(error)
                call()
            }
            assertEquals("$error", List(4) { expected }, results)
        }
    }

    @Test
    fun `an exhausted answer is final and is never retried`() = runTest {
        store.failNext(failure(Code.RESOURCE_EXHAUSTED))

        assertEquals(CloudResult.Exhausted, space.put(ANN, "drafts", listOf(doc("a"))))

        assertEquals(1, store.calls.size)
        assertTrue(store.docs(ANN, "drafts").isEmpty())
    }

    @Test
    fun `a call that never answers is offline once the timeout passes`() = runTest {
        for (call in everyCall) {
            store.hangNext()
            assertEquals(CloudResult.Offline, call())
        }
        assertEquals(CloudResult.Ok(Unit), space.put(ANN, "drafts", listOf(doc("a"))))
    }

    @Test
    fun `a different account than the signed in one never reaches the store`() = runTest {
        signInAs(BOB)

        assertEquals(List(4) { CloudResult.WrongAccount }, everyCall.map { it() })

        signedIn = null
        assertEquals(List(4) { CloudResult.WrongAccount }, everyCall.map { it() })
        assertEquals(emptyList<FakeCloudSpaceStore.Call>(), store.calls)
    }

    @Test
    fun `the uid is read again before every batch and a switch stops the rest`() = runTest {
        val docs = (1..CLOUD_BATCH_LIMIT + 1).map { doc("d$it") }
        var commits = 0
        store.afterCommit = { if (++commits == 1) signInAs(BOB) }

        assertEquals(CloudResult.WrongAccount, space.put(ANN, "drafts", docs))

        assertEquals(listOf(FakeCloudSpaceStore.Call("put", "drafts", CLOUD_BATCH_LIMIT)), store.calls)
        assertEquals(CLOUD_BATCH_LIMIT, store.docs(ANN, "drafts").size)
    }

    @Test
    fun `a put and a delete go in batches of at most 400`() = runTest {
        val docs = (1..900).map { doc("d$it") }

        assertEquals(CloudResult.Ok(Unit), space.put(ANN, "drafts", docs))
        assertEquals(listOf(400, 400, 100), store.calls.map { it.size })
        store.calls.clear()

        assertEquals(CloudResult.Ok(Unit), space.delete(ANN, "drafts", docs.map { it.id }.take(801)))
        assertEquals(listOf(400, 400, 1), store.calls.map { it.size })
        assertEquals(99, store.docs(ANN, "drafts").size)
    }

    @Test
    fun `deleteAll lists then deletes each passed kind in batches`() = runTest {
        space.put(ANN, "drafts", (1..450).map { doc("d$it") })
        space.put(ANN, "published", listOf(doc("p")))
        space.put(ANN, "received", listOf(doc("r")))
        store.calls.clear()

        assertEquals(CloudResult.Ok(Unit), space.deleteAll(ANN, listOf("drafts", "published")))

        assertEquals(
            listOf(
                FakeCloudSpaceStore.Call("ids", "drafts", 0),
                FakeCloudSpaceStore.Call("delete", "drafts", 400),
                FakeCloudSpaceStore.Call("delete", "drafts", 50),
                FakeCloudSpaceStore.Call("ids", "published", 0),
                FakeCloudSpaceStore.Call("delete", "published", 1),
            ),
            store.calls,
        )
        assertEquals(1, store.docs(ANN, "received").size)
    }

    @Test
    fun `a failure while deleting a kind stops deleteAll and is the result`() = runTest {
        space.put(ANN, "drafts", listOf(doc("a")))
        space.put(ANN, "published", listOf(doc("b")))
        store.calls.clear()
        store.failNext(failure(Code.UNAVAILABLE))

        assertEquals(CloudResult.Offline, space.deleteAll(ANN, listOf("drafts", "published")))

        assertEquals(1, store.calls.size)
        assertEquals(1, store.docs(ANN, "published").size)
    }

    @Test
    fun `plain values convert to and from the SDK types`() {
        val bytes = byteArrayOf(1, 2, 3)
        val sdk = toFirestoreMap(mapOf("b" to bytes, "list" to listOf(bytes, 1L), "map" to mapOf("k" to bytes), "n" to null))

        assertEquals(Blob.fromBytes(bytes), sdk["b"])
        assertEquals(listOf(Blob.fromBytes(bytes), 1L), sdk["list"])
        assertEquals(mapOf("k" to Blob.fromBytes(bytes)), sdk["map"])

        assertArrayEquals(bytes, toPlainValue(Blob.fromBytes(bytes)) as ByteArray)
        assertEquals(1_700_000_000_123L, toPlainValue(Timestamp(1_700_000_000L, 123_456_000)))
        assertEquals(listOf(1_000L, "x"), toPlainValue(listOf(Timestamp(1L, 0), "x")))
        assertEquals(mapOf("t" to 5L), toPlainValue(mapOf("t" to Timestamp(0L, 5_999_999))))
    }

    @Test
    fun `a cursor is the same instant as a doc stamped within its millisecond`() {
        assertEquals(Timestamp(1_700_000_000L, 123_000_000), timestampOf(1_700_000_000_123L))
        assertEquals(Timestamp(-1L, 999_000_000), timestampOf(-1L))
        assertEquals(-1L, epochMsOf(timestampOf(-1L)))
        assertTrue(timestampOf(1_700_000_000_123L) <= Timestamp(1_700_000_000L, 123_456_000))
    }

    @Test
    fun `a value that is not plain is refused by the SDK conversion`() {
        for (bad in listOf<Any>(1, 2f, mapOf(3L to "x"))) {
            val refused = try {
                toFirestoreMap(mapOf("v" to bad))
                false
            } catch (_: IllegalArgumentException) {
                true
            }
            assertTrue("$bad", refused)
        }
    }

    private companion object {
        const val ANN = "uid-ann"
        const val BOB = "uid-bob"
    }
}
