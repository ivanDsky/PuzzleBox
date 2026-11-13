package com.ivandsky.puzzlebox.data.repository

import androidx.room.Room
import com.ivandsky.puzzlebox.testing.room.RoomQueryThread
import com.ivandsky.puzzlebox.data.model.PostState
import com.ivandsky.puzzlebox.database.PuzzleBoxDatabase
import com.ivandsky.puzzlebox.database.ReceivedPuzzleEntity
import com.ivandsky.puzzlebox.testing.cloud.FakeOwnerScope
import com.ivandsky.puzzlebox.testing.repository.FakeReceivedPuzzleRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Runs [ReceivedPuzzleRepositoryContract] on [RoomReceivedPuzzleRepository] over in-memory Room
 * and on [FakeReceivedPuzzleRepository]. Rows holding names this version does not know (written
 * by a newer app before a downgrade) are Room-only, since the port cannot express them.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [34])
class ReceivedPuzzleRepositoryContractTest(private val adapter: String) : ReceivedPuzzleRepositoryContract() {

    private var db: PuzzleBoxDatabase? = null

    override val repository: ReceivedPuzzleRepository by lazy {
        when (adapter) {
            ROOM -> RoomReceivedPuzzleRepository(database().receivedPuzzleDao(), FakeOwnerScope())
            else -> FakeReceivedPuzzleRepository()
        }
    }

    @After
    fun tearDown() {
        db?.close()
    }

    @Test
    fun `room adapter reads an unknown postState name as Pending`() = runTest {
        assumeTrue(adapter == ROOM)
        database().receivedPuzzleDao().upsert(entity("ABC123", postState = "Retracted", difficulty = "Hard"))

        assertEquals(PostState.Pending, repository.get(code("ABC123"))?.postState)
        assertEquals(listOf(PostState.Pending), repository.observeAll().first().map { it.postState })
    }

    @Test
    fun `room adapter reads an unknown difficulty name as none`() = runTest {
        assumeTrue(adapter == ROOM)
        database().receivedPuzzleDao().upsert(entity("ABC123", postState = "Posted", difficulty = "Nightmare"))

        val loaded = repository.get(code("ABC123"))
        assertEquals(PostState.Posted, loaded?.postState)
        assertNull(loaded?.difficulty)
    }

    @Test
    fun `room adapter skips a row whose code no longer parses`() = runTest {
        assumeTrue(adapter == ROOM)
        val dao = database().receivedPuzzleDao()
        dao.upsert(entity("ABC123", postState = "Pending", difficulty = null))
        dao.upsert(entity("not-a-code", postState = "Pending", difficulty = null))

        assertEquals(listOf(code("ABC123")), repository.observeAll().first().map { it.code })
    }

    private fun database(): PuzzleBoxDatabase = db ?: Room
        .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), PuzzleBoxDatabase::class.java)
        .allowMainThreadQueries()
        .setQueryExecutor(RoomQueryThread.executor)
        .build()
        .also { db = it }

    private fun entity(code: String, postState: String, difficulty: String?) = ReceivedPuzzleEntity(
        code = code,
        gameId = "queens",
        variant = "queens/n8",
        sharedFormatVersion = 1,
        puzzleBytes = allBytes(),
        title = "Puzzle",
        authorName = "Ann",
        note = "",
        authorTimeMs = 42_000L,
        authorHints = 0,
        authorUid = "uid-ann",
        openedAt = 1_000L,
        unpublished = false,
        postState = postState,
        postAttempts = 0,
        difficulty = difficulty,
    )

    companion object {
        private const val ROOM = "room"

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun adapters(): List<Array<Any>> = listOf(arrayOf(ROOM), arrayOf("fake"))
    }
}
