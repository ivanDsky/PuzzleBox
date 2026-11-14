package com.ivandsky.puzzlebox.data.repository

import androidx.room.Room
import com.ivandsky.puzzlebox.testing.cloud.FakeOwnerScope
import com.ivandsky.puzzlebox.testing.room.RoomQueryThread
import com.ivandsky.puzzlebox.database.CompletionEntity
import com.ivandsky.puzzlebox.database.CompletionReplayEntity
import com.ivandsky.puzzlebox.database.PuzzleBoxDatabase
import com.ivandsky.puzzlebox.data.model.PuzzleRefKeys
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.model.SolveReplay
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.game.TestGames.Ref
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Record and latest replay reads of the completion repository over real in-memory Room. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CompletionReplayReadTest {

    private lateinit var db: PuzzleBoxDatabase
    private lateinit var repository: CompletionRepository

    private val ref = Ref(alpha, PuzzleKey.Daily(LocalDate.of(2026, 5, 1), 1))

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), PuzzleBoxDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(RoomQueryThread.executor)
            .build()
        repository = RoomCompletionRepository(
            db, db.completionDao(), db.completionReplayDao(), TestGames.registry(listOf(alpha)), FakeOwnerScope(),
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun bytes(n: Int, size: Int = 1) = ByteArray(size) { n.toByte() }

    private fun replay(n: Int) = SolveReplay.of(bytes(n))

    private suspend fun solve(at: Long, record: Boolean, replay: ByteArray? = bytes(at.toInt())) {
        val id = db.completionDao().insert(
            CompletionEntity(
                puzzleRef = PuzzleRefKeys.encode(ref),
                timeMs = 1_000L,
                hints = 0,
                statsVersion = 1,
                statsBlob = "{}",
                completedAtMs = at,
                firstCompletedAtMs = if (record) at else null,
                isRecord = record,
            ),
        )
        if (replay != null) db.completionReplayDao().insert(CompletionReplayEntity(id, replay))
    }

    @Test
    fun `one solve is record and latest`() = runTest {
        solve(10, record = true)

        assertEquals(replay(10), repository.recordReplay(ref))
        assertEquals(replay(10), repository.latestReplay(ref))
        assertTrue(repository.latestIsRecord(ref))
    }

    @Test
    fun `two solves split record and latest`() = runTest {
        solve(10, record = true)
        solve(20, record = false)

        assertEquals(replay(10), repository.recordReplay(ref))
        assertEquals(replay(20), repository.latestReplay(ref))
        assertFalse(repository.latestIsRecord(ref))
    }

    @Test
    fun `a solve stored without a replay reads null`() = runTest {
        solve(10, record = true, replay = null)

        assertNull(repository.recordReplay(ref))
        assertNull(repository.latestReplay(ref))
        assertTrue(repository.latestIsRecord(ref))
    }

    @Test
    fun `a puzzle without solves reads null and latest is record`() = runTest {
        assertNull(repository.recordReplay(ref))
        assertNull(repository.latestReplay(ref))
        assertTrue(repository.latestIsRecord(ref))
    }

    @Test
    fun `bytes above the cap read null`() = runTest {
        solve(10, record = true, replay = bytes(1, SolveReplay.MAX_BYTES + 1))

        assertNull(repository.recordReplay(ref))
        assertNull(repository.latestReplay(ref))
    }
}
