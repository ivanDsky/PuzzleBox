package com.ivandsky.puzzlebox.data.cloud

import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.testing.cloud.FakeCloudSpace
import com.ivandsky.puzzlebox.testing.contract.CloudSpaceContract
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Runs [CloudSpaceContract] against [FakeCloudSpace], then pins what only the fake's knobs show: the counters, the
 * payload log, scripted failures and the signed-in uid taken from [FakeIdentityService].
 */
class InMemoryFakeCloudSpaceContractTest : CloudSpaceContract() {

    private val identity = FakeIdentityService()
    private var nowMs = 0L
    private val fake = FakeCloudSpace(identity) { nowMs }

    override val space: CloudSpace get() = fake

    override fun signInAs(uid: String) {
        identity.currentIdentity = Identity(uid = uid, displayName = "Fake", photoUrl = null)
    }

    override fun setNow(nowMs: Long) {
        this.nowMs = nowMs
    }

    private fun doc(id: String, n: Long = 1L) = CloudDoc(id, mapOf("n" to n))

    @Test
    fun `reads count one per returned doc and at least one per query`() = runTest {
        fake.put(ANN, "drafts", listOf(doc("a"), doc("b"), doc("c")))

        fake.changedSince(ANN, "drafts", null)
        assertEquals(3, fake.reads)
        fake.changedSince(ANN, "drafts", Long.MAX_VALUE)
        assertEquals(4, fake.reads)
        fake.changedSince(ANN, "empty", null)
        assertEquals(5, fake.reads)
    }

    @Test
    fun `writes count one per doc and deletes one per requested id`() = runTest {
        fake.put(ANN, "drafts", listOf(doc("a"), doc("b")))
        fake.put(ANN, "drafts", listOf(doc("a")))
        assertEquals(3, fake.writes)

        fake.delete(ANN, "drafts", listOf("a", "gone"))
        assertEquals(2, fake.deletes)
        assertEquals(0, fake.reads)
    }

    @Test
    fun `deleteAll counts its enumeration as reads and the removed docs as deletes`() = runTest {
        fake.put(ANN, "drafts", listOf(doc("a"), doc("b")))
        fake.put(ANN, "published", listOf(doc("c")))

        fake.deleteAll(ANN, listOf("drafts", "published", "received"))

        assertEquals(2 + 1 + 1, fake.reads)
        assertEquals(3, fake.deletes)
        assertTrue(fake.isEmpty(ANN))
    }

    @Test
    fun `the payload log keeps every written doc as passed, before merging and stamping`() = runTest {
        fake.put(ANN, "drafts", listOf(CloudDoc("a", mapOf("x" to 1L, "nested" to mapOf("k" to "v")), updatedAtMs = 5L)))
        fake.put(ANN, "published", listOf(doc("p")))

        assertEquals(
            listOf(
                FakeCloudSpace.Written(ANN, "drafts", CloudDoc("a", mapOf("x" to 1L, "nested" to mapOf("k" to "v")), 5L)),
                FakeCloudSpace.Written(ANN, "published", doc("p")),
            ),
            fake.payloads,
        )
    }

    @Test
    fun `a rejected write is not logged and not counted`() = runTest {
        signInAs("someone-else")
        fake.put(ANN, "drafts", listOf(doc("a")))

        assertEquals(emptyList<FakeCloudSpace.Written>(), fake.payloads)
        assertEquals(0, fake.writes)
        assertTrue(fake.isEmpty(ANN))
    }

    @Test
    fun `nobody signed in is the wrong account for every call`() = runTest {
        identity.currentIdentity = null

        assertEquals(CloudResult.WrongAccount, fake.put(ANN, "drafts", listOf(doc("a"))))
        assertEquals(CloudResult.WrongAccount, fake.changedSince(ANN, "drafts", null))
        assertEquals(CloudResult.WrongAccount, fake.delete(ANN, "drafts", listOf("a")))
        assertEquals(CloudResult.WrongAccount, fake.deleteAll(ANN, listOf("drafts")))
        assertEquals(0, fake.reads + fake.writes + fake.deletes)
    }

    @Test
    fun `a scripted failure answers the next call only and changes nothing`() = runTest {
        for (failure in listOf(CloudResult.Offline, CloudResult.Exhausted, CloudResult.Denied)) {
            fake.failNext(failure)
            assertEquals(failure, fake.put(ANN, "drafts", listOf(doc("a"))))
        }
        assertTrue(fake.isEmpty(ANN))
        assertEquals(0, fake.writes)

        assertEquals(CloudResult.Ok(Unit), fake.put(ANN, "drafts", listOf(doc("a"))))
        assertEquals(1, fake.writes)
    }

    @Test
    fun `scripted failures answer any call kind and queue in order`() = runTest {
        fake.failNext(CloudResult.Offline)
        fake.failNext(CloudResult.Denied)

        assertEquals(CloudResult.Offline, fake.changedSince(ANN, "drafts", null))
        assertEquals(CloudResult.Denied, fake.deleteAll(ANN, listOf("drafts")))
        assertEquals(CloudResult.Ok(emptyList<CloudDoc>()), fake.changedSince(ANN, "drafts", null))
        assertEquals(1, fake.reads)
    }

    @Test
    fun `a hung call suspends until cancelled and writes nothing`() = runTest {
        fake.hangNext()

        val timedOut = try {
            withTimeout(50) { fake.put(ANN, "drafts", listOf(doc("a"))) }
            false
        } catch (_: TimeoutCancellationException) {
            true
        }

        assertTrue(timedOut)

        assertTrue(fake.isEmpty(ANN))
        assertEquals(CloudResult.Ok(Unit), fake.put(ANN, "drafts", listOf(doc("a"))))
    }

    @Test
    fun `a stored doc cannot be changed through the arrays a caller passed or got back`() = runTest {
        val bytes = byteArrayOf(1, 2)
        fake.put(ANN, "received", listOf(CloudDoc("a", mapOf("bytes" to bytes))))
        bytes[0] = 99
        (fake.docs(ANN, "received").single().fields["bytes"] as ByteArray)[1] = 99

        assertEquals(listOf<Byte>(1, 2), (fake.docs(ANN, "received").single().fields["bytes"] as ByteArray).toList())
    }

    @Test
    fun `a value that is not a plain cloud value is refused loudly`() = runTest {
        for (bad in listOf<Any>(1, listOf(mapOf("k" to 2f)), mapOf(3L to "x"))) {
            val refused = try {
                fake.put(ANN, "drafts", listOf(CloudDoc("a", mapOf("n" to bad))))
                false
            } catch (_: IllegalArgumentException) {
                true
            }
            assertTrue("$bad", refused)
        }
        assertTrue(fake.isEmpty(ANN))
    }

    private companion object {
        const val ANN = "uid-ann"
    }
}
