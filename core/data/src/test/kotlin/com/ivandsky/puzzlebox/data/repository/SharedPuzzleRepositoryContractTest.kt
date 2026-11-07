package com.ivandsky.puzzlebox.data.repository

import com.ivandsky.puzzlebox.data.model.SharedPuzzleDoc
import com.ivandsky.puzzlebox.testing.contract.SharedPuzzleRepositoryContract
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Runs [SharedPuzzleRepositoryContract] against [FakeSharedPuzzleRepository], then pins the
 * knobs only the fake can force: offline, signed out, a commit followed by a timeout, and the
 * per-day publish cap.
 */
class SharedPuzzleRepositoryContractTest : SharedPuzzleRepositoryContract() {

    private val fake = FakeSharedPuzzleRepository()

    override val repository: SharedPuzzleRepository get() = fake

    override fun signInAs(uid: String) {
        fake.signedInUid = uid
    }

    override suspend fun seed(doc: SharedPuzzleDoc) {
        val previous = fake.signedInUid
        fake.signedInUid = doc.authorUid
        fake.publish(doc)
        fake.signedInUid = previous
    }

    @Test
    fun `commit then offline writes one doc and a retry publishes the same code`() = runTest {
        signInAs(AUTHOR)
        fake.commitThenOffline = true

        assertEquals(PublishResult.Offline, fake.publish(doc()))
        assertEquals(mapOf(code() to doc()), fake.docs)

        assertEquals(PublishResult.Published(code()), fake.publish(doc()))
        assertEquals(mapOf(code() to doc()), fake.docs)
    }

    @Test
    fun `a retry after a commit that reached the cap still publishes`() = runTest {
        signInAs(AUTHOR)
        fake.publishesToday = 19
        fake.commitThenOffline = true

        assertEquals(PublishResult.Offline, fake.publish(doc()))
        assertEquals(PublishResult.Published(code()), fake.publish(doc()))
        assertEquals(20, fake.publishesToday)
    }

    @Test
    fun `the 21st publish in a day hits the cap and writes nothing`() = runTest {
        signInAs(AUTHOR)
        val codes = CAP_SUFFIXES.map { code("K7Q2M$it") }

        codes.take(20).forEach { assertEquals(PublishResult.Published(it), fake.publish(doc(code = it))) }
        assertEquals(PublishResult.DailyCapReached, fake.publish(doc(code = codes.last())))
        assertEquals(codes.take(20).toSet(), fake.docs.keys)
        assertEquals(SharedFetchResult.NotFound, fake.fetch(codes.last()))
    }

    @Test
    fun `offline every call is Offline and nothing is written`() = runTest {
        signInAs(AUTHOR)
        fake.publish(doc())
        fake.online = false

        assertEquals(PublishResult.Offline, fake.publish(doc(code = code("ZZZ999"))))
        assertEquals(UnpublishResult.Offline, fake.unpublish(code()))
        assertEquals(SharedFetchResult.Offline, fake.fetch(code()))
        assertEquals(mapOf(code() to doc()), fake.docs)
        assertEquals(1, fake.publishesToday)
    }

    @Test
    fun `signed out publish is SignedOut, unpublish is Rejected, fetch still reads`() = runTest {
        signInAs(AUTHOR)
        fake.publish(doc())
        fake.signedInUid = null

        assertEquals(PublishResult.SignedOut, fake.publish(doc(code = code("ZZZ999"))))
        assertTrue(fake.unpublish(code()) is UnpublishResult.Rejected)
        assertEquals(SharedFetchResult.Found(doc()), fake.fetch(code()))
        assertEquals(mapOf(code() to doc()), fake.docs)
    }

    @Test
    fun `a doc authored by someone else than the signed-in user is Rejected`() = runTest {
        signInAs(OTHER)

        assertTrue(fake.publish(doc(authorUid = AUTHOR)) is PublishResult.Rejected)
        assertEquals(emptyMap<Any, Any>(), fake.docs)
    }

    @Test
    fun `unpublishing another author's or an absent code is Rejected`() = runTest {
        signInAs(AUTHOR)
        fake.publish(doc())
        signInAs(OTHER)

        assertTrue(fake.unpublish(code()) is UnpublishResult.Rejected)
        assertTrue(fake.unpublish(code("ZZZ999")) is UnpublishResult.Rejected)
        assertEquals(mapOf(code() to doc()), fake.docs)
    }

    private companion object {
        const val CAP_SUFFIXES = "0123456789ABCDEFGHJKM"
    }
}
