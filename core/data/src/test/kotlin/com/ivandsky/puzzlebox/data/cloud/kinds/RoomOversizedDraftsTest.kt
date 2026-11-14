package com.ivandsky.puzzlebox.data.cloud.kinds

import androidx.room.Room
import app.cash.turbine.test
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.database.DraftEntity
import com.ivandsky.puzzlebox.database.PuzzleBoxDatabase
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.room.RoomQueryThread
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** [RoomOversizedDrafts] over real in-memory Room: the signed-in account's drafts over the 64 KiB blob cap. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomOversizedDraftsTest {

    private lateinit var db: PuzzleBoxDatabase
    private val identity = FakeIdentityService(initial = Identity(ANN, "Ann", null))
    private lateinit var oversized: RoomOversizedDrafts

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), PuzzleBoxDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(RoomQueryThread.executor)
            .build()
        oversized = RoomOversizedDrafts(db.draftDao(), identity)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun seed(id: String, owner: String? = ANN, size: Int = OVER) =
        db.draftDao().upsert(entity(id, owner, ByteArray(size)))

    @Test
    fun `a draft exactly at the cap is not counted and one byte over is`() = runTest {
        seed("at", size = MAX_BLOB_BYTES)
        seed("over", size = MAX_BLOB_BYTES + 1)

        oversized.count.test {
            assertEquals(1, awaitItem())
        }
    }

    @Test
    fun `no oversized draft counts zero`() = runTest {
        seed("small", size = 10)

        oversized.count.test {
            assertEquals(0, awaitItem())
        }
    }

    @Test
    fun `only the signed-in account's own drafts count`() = runTest {
        seed("mine")
        seed("also-mine")
        seed("bobs", owner = BOB)
        seed("ownerless", owner = null)

        oversized.count.test {
            assertEquals(2, awaitItem())
        }
    }

    @Test
    fun `the count follows edits, deletes and new drafts`() = runTest {
        seed("a")
        oversized.count.test {
            assertEquals(1, awaitItem())

            seed("b")
            assertEquals(2, awaitItem())

            seed("a", size = 10)
            assertEquals(1, awaitItem())

            db.draftDao().delete("b")
            assertEquals(0, awaitItem())
        }
    }

    @Test
    fun `signing out counts zero and another account counts its own`() = runTest {
        seed("a")
        seed("b", owner = BOB)
        seed("c", owner = BOB)

        oversized.count.test {
            assertEquals(1, awaitItem())

            identity.signOut()
            assertEquals(0, awaitItem())

            identity.currentIdentity = Identity(BOB, "Bob", null)
            assertEquals(2, awaitItem())
        }
    }

    private fun entity(id: String, owner: String?, blob: ByteArray) = DraftEntity(
        id = id,
        gameId = "queens",
        variant = "queens/n8",
        draftFormatVersion = 1,
        blob = blob,
        title = "Draft $id",
        updatedAt = 1_000L,
        testLayoutKey = null,
        testTimeMs = null,
        testHints = null,
        reservedCode = null,
        imported = false,
        authorName = "",
        note = "",
        ownerUid = owner,
        uploadedFingerprint = null,
    )

    private companion object {
        const val ANN = "ann"
        const val BOB = "bob"
        const val OVER = MAX_BLOB_BYTES + 1
    }
}
