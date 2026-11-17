package com.ivandsky.puzzlebox.data.repository

import androidx.room.Room
import com.ivandsky.puzzlebox.database.PuzzleBoxDatabase
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.CompletionSource
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.testing.cloud.FakeOwnerScope
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.room.RoomQueryThread
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * `observeSolves` reads every solve of one puzzle, the record and its play-agains and a LinkedIn import alike, newest
 * first, over real Room and [FakeCompletionRepository]; another day's puzzle never leaks in.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ObserveSolvesTest {

    private val queens = GameId("queens")
    private val day = LocalDate.of(2026, 11, 6)
    private val ref = TestGames.Ref(queens, PuzzleKey.Daily(day, 7))
    private var nowMs = 1_000L

    private val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), PuzzleBoxDatabase::class.java)
        .allowMainThreadQueries()
        .setQueryExecutor(RoomQueryThread.executor)
        .build()

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `room observeSolves returns this puzzle's every solve newest first`() = runTest {
        val registry = TestGames.registry(listOf(queens))
        val repository = RoomCompletionRepository(db, db.completionDao(), db.completionReplayDao(), registry, FakeOwnerScope(), clock = { nowMs })

        assertContract(repository)
    }

    @Test
    fun `fake observeSolves returns this puzzle's every solve newest first`() = runTest {
        assertContract(FakeCompletionRepository())
    }

    private suspend fun assertContract(repository: CompletionRepository) {
        repository.onCompleted(solve(ref, 114_000, CompletionSource.LinkedIn))
        nowMs += 1_000
        repository.onCompleted(solve(ref, 101_000))
        repository.onCompleted(solve(TestGames.Ref(queens, PuzzleKey.Daily(day.plusDays(1), 8)), 5_000))
        nowMs += 1_000
        repository.onCompleted(solve(ref, 120_000))

        val rows = repository.observeSolves(ref).first()

        assertEquals(listOf(120_000L, 101_000L, 114_000L), rows.map { it.time })
        assertEquals(listOf(CompletionSource.App, CompletionSource.App, CompletionSource.LinkedIn), rows.map { it.source })
    }

    private fun solve(ref: TestGames.Ref, timeMs: Long, source: CompletionSource = CompletionSource.App) = CompletionRecord(
        puzzleRef = ref,
        time = timeMs,
        hints = 0,
        stats = RecordStats(schemaVersion = 1, blob = "{}"),
        source = source,
    )
}
