package com.ivandsky.puzzlebox.data.cloud

import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.testing.cloud.FakeCloudSpace
import com.ivandsky.puzzlebox.testing.cloud.FakeSyncStateStore
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** SC13, engine half: the erase steps and what they leave alone, over [FakeCloudSpace] and [FakeSyncStateStore]. */
class CloudEraseTest {

    private class RecordingKind(override val kind: String, val rows: List<String>) : SyncedKind {
        val forgotten = mutableListOf<String>()

        override fun pendingIds(uid: String): Flow<Set<String>> = emptyFlow()
        override suspend fun adopt(uid: String) = Unit
        override suspend fun pending(uid: String): List<CloudDoc> = emptyList()
        override suspend fun apply(uid: String, docs: List<CloudDoc>): Int = 0
        override suspend fun acknowledge(uid: String, uploaded: List<CloudDoc>) = Unit
        override suspend fun forget(uid: String) {
            forgotten += uid
        }
    }

    private val identity = FakeIdentityService(Identity(uid = ANN, displayName = "Ann", photoUrl = null))
    private val space = FakeCloudSpace(identity)
    private val store = FakeSyncStateStore()
    private val drafts = RecordingKind("drafts", listOf("d1", "d2"))
    private val boards = RecordingKind("boards", listOf("b1"))
    private val erase = CloudErase(space, listOf(drafts, boards), store, identity)

    private suspend fun seed() {
        space.put(ANN, "drafts", listOf(CloudDoc("d1", mapOf("n" to 1L)), CloudDoc("d2", mapOf("n" to 2L))))
        space.put(ANN, "boards", listOf(CloudDoc("b1", mapOf("n" to 3L))))
        space.put(ANN, "other", listOf(CloudDoc("o1", mapOf("n" to 4L))))
        store.setCursor(ANN, "drafts", 10L)
        store.setCursor(ANN, "boards", 20L)
        store.markRestoreShown(ANN)
        store.setLastUid(ANN)
    }

    @Test
    fun `ok empties the registered kinds, forgets everywhere and signs out`() = runTest {
        seed()

        val result = erase.erase(ANN)

        assertEquals(CloudResult.Ok(Unit), result)
        assertTrue(space.docs(ANN, "drafts").isEmpty())
        assertTrue(space.docs(ANN, "boards").isEmpty())
        assertEquals(listOf(ANN), drafts.forgotten)
        assertEquals(listOf(ANN), boards.forgotten)
        assertNull(store.cursor(ANN, "drafts"))
        assertNull(store.cursor(ANN, "boards"))
        assertFalse(store.restoreShown(ANN))
        assertEquals(ANN, store.lastUid.value)
        assertEquals(1, identity.signOutCalls.size)
        assertNull(identity.currentIdentity)
    }

    @Test
    fun `ok leaves local rows and other kinds alone`() = runTest {
        seed()

        erase.erase(ANN)

        assertEquals(listOf("d1", "d2"), drafts.rows)
        assertEquals(listOf("b1"), boards.rows)
        assertEquals(listOf("o1"), space.docs(ANN, "other").map { it.id })
    }

    @Test
    fun `ok empties the whole space when every kind is registered`() = runTest {
        space.put(ANN, "drafts", listOf(CloudDoc("d1", mapOf("n" to 1L))))

        CloudErase(space, listOf(drafts), store, identity).erase(ANN)

        assertTrue(space.isEmpty(ANN))
    }

    @Test
    fun `offline returns offline with nothing forgotten and still signed in`() = runTest {
        assertNothingChanges(CloudResult.Offline)
    }

    @Test
    fun `denied returns denied with nothing forgotten and still signed in`() = runTest {
        assertNothingChanges(CloudResult.Denied)
    }

    @Test
    fun `exhausted returns exhausted with nothing forgotten and still signed in`() = runTest {
        assertNothingChanges(CloudResult.Exhausted)
    }

    @Test
    fun `wrong account returns wrong account and changes nothing`() = runTest {
        seed()

        val result = erase.erase(BOB)

        assertEquals(CloudResult.WrongAccount, result)
        assertEquals(2, space.docs(ANN, "drafts").size)
        assertTrue(drafts.forgotten.isEmpty() && boards.forgotten.isEmpty())
        assertTrue(identity.signOutCalls.isEmpty())
    }

    @Test
    fun `a retry after offline erases`() = runTest {
        seed()
        space.failNext(CloudResult.Offline)
        assertEquals(CloudResult.Offline, erase.erase(ANN))

        assertEquals(CloudResult.Ok(Unit), erase.erase(ANN))

        assertTrue(space.docs(ANN, "drafts").isEmpty())
        assertEquals(listOf(ANN), drafts.forgotten)
    }

    private suspend fun assertNothingChanges(failure: CloudResult<Nothing>) {
        seed()
        space.failNext(failure)

        val result = erase.erase(ANN)

        assertEquals(failure, result)
        assertEquals(2, space.docs(ANN, "drafts").size)
        assertEquals(1, space.docs(ANN, "boards").size)
        assertTrue(drafts.forgotten.isEmpty() && boards.forgotten.isEmpty())
        assertEquals(10L, store.cursor(ANN, "drafts"))
        assertEquals(20L, store.cursor(ANN, "boards"))
        assertTrue(store.restoreShown(ANN))
        assertTrue(identity.signOutCalls.isEmpty())
        assertNotNull(identity.currentIdentity)
    }

    private companion object {
        const val ANN = "ann"
        const val BOB = "bob"
    }
}
