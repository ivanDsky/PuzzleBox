package com.ivandsky.puzzlebox.data.repository

import androidx.room.Room
import com.ivandsky.puzzlebox.testing.cloud.FakeOwnerScope
import com.ivandsky.puzzlebox.testing.room.RoomQueryThread
import app.cash.turbine.test
import com.ivandsky.puzzlebox.data.model.Draft
import com.ivandsky.puzzlebox.data.model.TestSolve
import com.ivandsky.puzzlebox.database.PuzzleBoxDatabase
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.repository.FakeDraftRepository
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

private const val REEMIT_WINDOW_MS = 300L

/**
 * Behavior every [DraftRepository] adapter must pass (S15). Subclasses supply a fresh, empty
 * [repository]; [DraftRepositoryContractTest] runs it on Room and on the `:core:testing` fake.
 */
abstract class DraftRepositoryContract {

    protected abstract val repository: DraftRepository

    protected fun draft(
        id: String,
        updatedAtMs: Long = 1_000L,
        blob: ByteArray = byteArrayOf(1, 2, 3),
        testSolve: TestSolve? = null,
        authorName: String = "",
        note: String = "",
    ) = Draft(
        id = id,
        gameId = GameId("queens"),
        variant = VariantId("queens/n8"),
        draftFormatVersion = 1,
        blob = blob,
        title = "Draft $id",
        updatedAt = Instant.ofEpochMilli(updatedAtMs),
        testSolve = testSolve,
        reservedCode = null,
        imported = false,
        authorName = authorName,
        note = note,
    )

    @Test
    fun `upsert then get returns an equal draft`() = runTest {
        val saved = draft("d1", blob = byteArrayOf(9, 8, 7), testSolve = TestSolve("layout-a", 42_000L, 2))
            .copy(reservedCode = "ABC123", imported = true)

        repository.upsert(saved)

        val loaded = repository.get("d1")
        assertEquals(saved, loaded)
        assertArrayEquals(byteArrayOf(9, 8, 7), loaded?.blob)
        assertEquals(TestSolve("layout-a", 42_000L, 2), loaded?.testSolve)
        assertNull(repository.get("missing"))
    }

    @Test
    fun `a draft without a test solve round-trips with none`() = runTest {
        repository.upsert(draft("d1"))

        assertNull(repository.get("d1")?.testSolve)
    }

    @Test
    fun `author name and note round-trip`() = runTest {
        repository.upsert(draft("d1", authorName = "Ivan", note = "Mind the corner"))

        val loaded = repository.get("d1")
        assertEquals("Ivan", loaded?.authorName)
        assertEquals("Mind the corner", loaded?.note)
    }

    @Test
    fun `upsert of an existing id replaces it`() = runTest {
        repository.upsert(draft("d1", blob = byteArrayOf(1)))
        repository.upsert(draft("d1", updatedAtMs = 2_000L, blob = byteArrayOf(2, 2)))

        assertEquals(draft("d1", updatedAtMs = 2_000L, blob = byteArrayOf(2, 2)), repository.get("d1"))
        assertEquals(listOf("d1"), repository.observeAll().first().map { it.id })
    }

    @Test
    fun `observeAll orders newest first`() = runTest {
        repository.upsert(draft("old", updatedAtMs = 1_000L))
        repository.upsert(draft("newest", updatedAtMs = 3_000L))
        repository.upsert(draft("middle", updatedAtMs = 2_000L))

        assertEquals(listOf("newest", "middle", "old"), repository.observeAll().first().map { it.id })
    }

    @Test
    fun `observe tracks one draft through upsert and delete`() = runTest {
        val one = repository.observe("d1")
        assertNull(one.first())

        repository.upsert(draft("d1"))
        assertEquals(draft("d1"), one.first())

        repository.delete("d1")
        assertNull(one.first())
    }

    @Test
    fun `a live observer sees the draft arrive and leave, and nothing for other drafts`() = runTest {
        repository.observe("d1").test {
            assertNull(awaitItem())

            repository.upsert(draft("d1"))
            assertEquals(draft("d1"), awaitItem())

            repository.upsert(draft("d2"))
            // Room re-queries on its own threads: give a stray re-emission real time to land.
            withContext(Dispatchers.Default) { delay(REEMIT_WINDOW_MS) }
            expectNoEvents()

            repository.delete("d1")
            assertNull(awaitItem())
        }
    }

    @Test
    fun `updatedAt is kept to the millisecond`() = runTest {
        val subMillis = Instant.ofEpochSecond(1_700_000_000L, 123_456_789L)
        val millis = Instant.ofEpochSecond(1_700_000_000L, 123_000_000L)

        repository.upsert(draft("d1").copy(updatedAt = subMillis))

        assertEquals(millis, repository.get("d1")?.updatedAt)
        assertEquals(millis, repository.observe("d1").first()?.updatedAt)
        assertEquals(listOf(millis), repository.observeAll().first().map { it.updatedAt })
    }

    @Test
    fun `delete removes the draft and is idempotent`() = runTest {
        repository.upsert(draft("d1"))
        repository.upsert(draft("d2"))

        repository.delete("d1")
        repository.delete("d1")
        repository.delete("missing")

        assertNull(repository.get("d1"))
        assertEquals(listOf("d2"), repository.observeAll().first().map { it.id })
    }
}

/** Runs [DraftRepositoryContract] on [RoomDraftRepository] over in-memory Room and on [FakeDraftRepository]. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [34])
class DraftRepositoryContractTest(private val adapter: String) : DraftRepositoryContract() {

    private var db: PuzzleBoxDatabase? = null

    override val repository: DraftRepository by lazy {
        when (adapter) {
            ROOM -> {
                val database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), PuzzleBoxDatabase::class.java)
                    .allowMainThreadQueries()
                    .setQueryExecutor(RoomQueryThread.executor)
                    .build()
                db = database
                RoomDraftRepository(database, database.draftDao(), database.syncTombstoneDao(), FakeOwnerScope())
            }
            else -> FakeDraftRepository()
        }
    }

    @After
    fun tearDown() {
        db?.close()
    }

    companion object {
        private const val ROOM = "room"

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun adapters(): List<Array<Any>> = listOf(arrayOf(ROOM), arrayOf("fake"))
    }
}
