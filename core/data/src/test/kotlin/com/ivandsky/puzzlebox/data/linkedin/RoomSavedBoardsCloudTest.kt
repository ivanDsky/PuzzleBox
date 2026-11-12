package com.ivandsky.puzzlebox.data.linkedin

import androidx.room.Room
import com.ivandsky.puzzlebox.data.cloud.CloudDoc
import com.ivandsky.puzzlebox.data.cloud.CloudResult
import com.ivandsky.puzzlebox.data.cloud.CloudSpace
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.database.linkedin.FetchLogEntity
import com.ivandsky.puzzlebox.database.linkedin.GhostEntity
import com.ivandsky.puzzlebox.database.linkedin.LinkedInDatabase
import com.ivandsky.puzzlebox.database.linkedin.SavedBoardEntity
import com.ivandsky.puzzlebox.testing.cloud.FakeCloudSpace
import com.ivandsky.puzzlebox.testing.cloud.FakeOwnerScope
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import java.security.MessageDigest
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Delete saved boards: the cloud first, the local rows only on Ok, and the store lock around both (A27). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomSavedBoardsCloudTest {

    private lateinit var db: LinkedInDatabase
    private val identity = FakeIdentityService(Identity(uid = ANN, displayName = "Ann", photoUrl = null))
    private val space = FakeCloudSpace(identity)
    private val owner = FakeOwnerScope(visible = ANN, writer = ANN)
    private val lock = LinkedInStoreLock()

    /** What the local tables held at the moment the cloud delete was asked. */
    private var localAtCloudDelete: List<SavedBoardEntity>? = null

    private val watching = object : CloudSpace by space {
        override suspend fun delete(uid: String, kind: String, ids: List<String>): CloudResult<Unit> {
            localAtCloudDelete = db.savedBoardDao().all()
            return space.delete(uid, kind, ids)
        }
    }

    private lateinit var cloud: RoomSavedBoardsCloud

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), LinkedInDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        cloud = RoomSavedBoardsCloud(db, lock, watching, owner)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun seed(day: String, ownerUid: String? = ANN, profileId: String = "me") {
        db.savedBoardDao().upsert(SavedBoardEntity("queens", day, profileId, ownerUid, 1_000L, true, null))
        db.ghostDao().replace("queens", day, listOf(GhostEntity("queens", day, "m1", "Bea", 1_000L, true, false, 1_000L)))
        db.fetchLogDao().put(FetchLogEntity("queens", day, "Board", profileId, 1_000L, "Board"))
        db.fetchLogDao().put(FetchLogEntity("queens", day, "Own", profileId, 1_000L, "Solved"))
    }

    private suspend fun cloudDocs(vararg ids: String) =
        space.put(ANN, "boards", ids.map { CloudDoc(it, mapOf("complete" to true)) })

    private suspend fun daysLeft(): List<String> = db.savedBoardDao().all().map { it.day }

    @Test
    fun `the cloud delete happens first and the local rows go only after it succeeds`() = runTest {
        seed("2026-11-01")
        seed("2026-11-02")
        cloudDocs("queens_2026-11-01_${key("me")}", "queens_2026-11-02_${key("me")}", "queens_2026-10-01_other")

        val result = cloud.deleteEverywhere()

        assertEquals(CloudResult.Ok(Unit), result)
        assertEquals("rows were still there when the cloud was asked", 2, localAtCloudDelete!!.size)
        assertEquals(listOf("queens_2026-10-01_other"), space.docs(ANN, "boards").map { it.id })
        assertTrue(daysLeft().isEmpty())
        assertTrue(db.ghostDao().board("queens", "2026-11-01").isEmpty())
        assertNull(db.fetchLogDao().get("queens", "2026-11-01", "Board", "me"))
        assertNotNull("own results are not boards", db.fetchLogDao().get("queens", "2026-11-01", "Own", "me"))
    }

    @Test
    fun `a failed cloud delete leaves every local row and the next call finds them`() = runTest {
        seed("2026-11-01")
        cloudDocs("queens_2026-11-01_${key("me")}")
        for (failure in listOf(CloudResult.Offline, CloudResult.Exhausted, CloudResult.Denied, CloudResult.WrongAccount)) {
            space.failNext(failure)

            assertEquals(failure, cloud.deleteEverywhere())

            assertEquals(listOf("2026-11-01"), daysLeft())
            assertEquals(1, db.ghostDao().board("queens", "2026-11-01").size)
            assertNotNull(db.fetchLogDao().get("queens", "2026-11-01", "Board", "me"))
            assertEquals(1, space.docs(ANN, "boards").size)
        }

        assertEquals(CloudResult.Ok(Unit), cloud.deleteEverywhere())
        assertTrue(daysLeft().isEmpty())
        assertTrue(space.docs(ANN, "boards").isEmpty())
    }

    @Test
    fun `another account's boards stay here and in the cloud`() = runTest {
        seed("2026-11-01")
        seed("2026-11-02", ownerUid = BEA, profileId = "bea-profile")
        cloudDocs("queens_2026-11-01_${key("me")}", "queens_2026-11-02_${key("bea-profile")}")

        assertEquals(CloudResult.Ok(Unit), cloud.deleteEverywhere())

        assertEquals(listOf("2026-11-02"), daysLeft())
        assertEquals(listOf("queens_2026-11-02_${key("bea-profile")}"), space.docs(ANN, "boards").map { it.id })
    }

    @Test
    fun `nothing to delete spends no cloud call`() = runTest {
        assertEquals(CloudResult.Ok(Unit), cloud.deleteEverywhere())
        assertEquals(0, space.deletes)
    }

    @Test
    fun `signed out, boards read while signed out go at once and none reaches the cloud`() = runTest {
        owner.writerUid = null
        owner.visibleOwner = null
        seed("2026-11-01", ownerUid = null)

        assertEquals(CloudResult.Ok(Unit), cloud.deleteEverywhere())

        assertTrue(daysLeft().isEmpty())
        assertEquals(0, space.deletes)
    }

    @Test
    fun `signed out, a board owned by an account cannot be deleted from the cloud so nothing is`() = runTest {
        owner.writerUid = null
        seed("2026-11-01", ownerUid = ANN)
        seed("2026-11-02", ownerUid = null)

        assertEquals(CloudResult.WrongAccount, cloud.deleteEverywhere())

        assertEquals(listOf("2026-11-01", "2026-11-02"), daysLeft())
        assertEquals(0, space.deletes)
    }

    @Test
    fun `the delete waits for the store lock`() = runBlocking {
        seed("2026-11-01")
        val executor = Executors.newSingleThreadExecutor { Thread(it, "boards-delete-test").apply { isDaemon = true } }
        val scope = CoroutineScope(executor.asCoroutineDispatcher() + SupervisorJob())
        try {
            lateinit var result: Deferred<CloudResult<Unit>>
            lock.withLock {
                result = scope.async { cloud.deleteEverywhere() }
                Thread.sleep(HOLD_MS)
                assertFalse("the delete ran under a held lock", result.isCompleted)
                assertEquals(listOf("2026-11-01"), daysLeft())
            }
            assertEquals(CloudResult.Ok(Unit), result.await())
            assertTrue(daysLeft().isEmpty())
        } finally {
            scope.cancel()
            executor.shutdownNow()
        }
    }

    private fun key(profileId: String) =
        MessageDigest.getInstance("SHA-256").digest(profileId.toByteArray())
            .joinToString("") { "%02x".format(it) }.take(16)

    private companion object {
        const val ANN = "ann"
        const val BEA = "bea"
        const val HOLD_MS = 300L
    }
}
