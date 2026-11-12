package com.ivandsky.puzzlebox.data.repository

import androidx.room.Room
import com.ivandsky.puzzlebox.database.PuzzleBoxDatabase
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.CompletionSource
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.model.SolveReplay
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.game.TestGames.Ref
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.first
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

/** The replay write of the completion repository over real in-memory Room: stored beside the completion, kept as record + latest. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CompletionReplayStoreTest {

    private lateinit var db: PuzzleBoxDatabase
    private lateinit var repository: RoomCompletionRepository

    private var nowMs = 7_000L

    private val day = LocalDate.of(2026, 5, 1)
    private val ref = Ref(alpha, PuzzleKey.Daily(day, 1))

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), PuzzleBoxDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomCompletionRepository(
            db, db.completionDao(), db.completionReplayDao(), TestGames.registry(listOf(alpha)), clock = { nowMs },
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun replay(n: Int) = SolveReplay.of(ByteArray(3) { n.toByte() })

    private fun solve(source: CompletionSource = CompletionSource.App) = CompletionRecord(
        puzzleRef = ref,
        time = 95_000,
        hints = 1,
        stats = RecordStats(schemaVersion = 1, blob = """{"v":1}"""),
        source = source,
    )

    private fun replayRows(): Int =
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM completion_replays").use {
            it.moveToFirst()
            it.getInt(0)
        }

    @Test
    fun `first solve with a replay is record and latest`() = runTest {
        repository.onCompleted(solve(), replay(1))

        assertEquals(replay(1), repository.recordReplay(ref))
        assertEquals(replay(1), repository.latestReplay(ref))
        assertTrue(repository.latestIsRecord(ref))
    }

    @Test
    fun `second solve keeps the record and moves latest`() = runTest {
        repository.onCompleted(solve(), replay(1))
        nowMs += 1_000
        repository.onCompleted(solve(), replay(2))

        assertEquals(replay(1), repository.recordReplay(ref))
        assertEquals(replay(2), repository.latestReplay(ref))
        assertFalse(repository.latestIsRecord(ref))
    }

    @Test
    fun `third solve drops the second replay`() = runTest {
        repository.onCompleted(solve(), replay(1))
        nowMs += 1_000
        repository.onCompleted(solve(), replay(2))
        nowMs += 1_000
        repository.onCompleted(solve(), replay(3))

        assertEquals(replay(1), repository.recordReplay(ref))
        assertEquals(replay(3), repository.latestReplay(ref))
        assertEquals("only the record and the newest stay", 2, replayRows())
    }

    @Test
    fun `a solve without a replay reads null`() = runTest {
        repository.onCompleted(solve())

        assertNull(repository.recordReplay(ref))
        assertNull(repository.latestReplay(ref))
        assertEquals(0, replayRows())
    }

    @Test
    fun `a later solve without a replay leaves the stored replays`() = runTest {
        repository.onCompleted(solve(), replay(1))
        nowMs += 1_000
        repository.onCompleted(solve())

        assertEquals(replay(1), repository.recordReplay(ref))
        assertNull(repository.latestReplay(ref))
        assertEquals(1, replayRows())
    }

    @Test
    fun `a repeated import stores neither a row nor its replay`() = runTest {
        repository.onCompleted(solve(CompletionSource.LinkedIn))
        nowMs += 1_000
        repository.onCompleted(solve(CompletionSource.LinkedIn), replay(1))

        assertEquals(1, repository.observeImports().first().size)
        assertEquals(0, replayRows())
        assertNull(repository.latestReplay(ref))
    }

    @Test
    fun `deleting a completion removes its replay`() = runTest {
        repository.onCompleted(solve(), replay(1))
        assertEquals(1, replayRows())

        db.openHelper.writableDatabase.execSQL("DELETE FROM completions")

        assertEquals(0, replayRows())
        assertNull(repository.recordReplay(ref))
    }

    @Test
    fun `the completion row is the same with and without a replay`() = runTest {
        val other = Ref(alpha, PuzzleKey.Daily(day, 2))
        repository.onCompleted(solve(), replay(1))
        repository.onCompleted(solve().copy(puzzleRef = other))

        val rows = repository.observeRecords().first()
        assertEquals(
            rows.single { it.puzzleRef == ref },
            rows.single { it.puzzleRef == other }.copy(puzzleRef = ref),
        )
    }

    private data class Reads(val record: SolveReplay?, val latest: SolveReplay?, val latestIsRecord: Boolean)

    private suspend fun CompletionRepository.reads() = Reads(recordReplay(ref), latestReplay(ref), latestIsRecord(ref))

    /** Plays [replays] in order on Room and on the fake; both must answer the same. */
    private suspend fun assertFakeMatchesRoom(vararg replays: SolveReplay?): Reads {
        val fake = FakeCompletionRepository()
        replays.forEach {
            repository.onCompleted(solve(), it)
            nowMs += 1_000
            fake.onCompleted(solve(), it)
        }
        assertEquals(repository.reads(), fake.reads())
        return fake.reads()
    }

    @Test
    fun `the fake fills record and latest of one solve like Room`() = runTest {
        assertEquals(Reads(replay(1), replay(1), true), assertFakeMatchesRoom(replay(1)))
    }

    @Test
    fun `the fake moves only latest on a second solve like Room`() = runTest {
        assertEquals(Reads(replay(1), replay(2), false), assertFakeMatchesRoom(replay(1), replay(2)))
    }

    @Test
    fun `the fake keeps record and newest of three solves like Room`() = runTest {
        assertEquals(Reads(replay(1), replay(3), false), assertFakeMatchesRoom(replay(1), replay(2), replay(3)))
    }

    @Test
    fun `the fake reads a replay-less latest as null like Room`() = runTest {
        assertEquals(Reads(replay(1), null, false), assertFakeMatchesRoom(replay(1), null))
    }

    @Test
    fun `the fake keeps a replay-less first solve record-less like Room`() = runTest {
        assertEquals(Reads(null, replay(2), false), assertFakeMatchesRoom(null, replay(2)))
    }
}
