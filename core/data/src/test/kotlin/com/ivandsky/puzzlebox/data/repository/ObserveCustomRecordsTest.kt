package com.ivandsky.puzzlebox.data.repository

import androidx.room.Room
import com.ivandsky.puzzlebox.database.PuzzleBoxDatabase
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * `observeCustom` reads one friend puzzle's rows by its `custom/<game>/<id>` key, over real
 * Room and [FakeCompletionRepository]: other codes, other games and dailies never leak in,
 * newest first, and a new completion re-emits.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ObserveCustomRecordsTest {

    private val queens = GameId("queens")
    private val tango = GameId("tango")
    private val code = "K7Q2MX"
    private var nowMs = 1_000L

    private val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), PuzzleBoxDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `room observeCustom emits only that puzzle's rows`() = runTest {
        val registry = TestGames.registry(listOf(queens, tango))
        val repository = RoomCompletionRepository(db, db.completionDao(), db.completionReplayDao(), registry, clock = { nowMs })
        val rows = assertContract(repository)
        val record = rows.last()
        assertTrue(record.isRecord)
        assertEquals(90_000L, record.time)
        assertTrue(rows.dropLast(1).none { it.isRecord })
    }

    @Test
    fun `fake observeCustom emits only that puzzle's rows`() = runTest {
        val rows = assertContract(FakeCompletionRepository())
        assertFalse(rows.isEmpty())
    }

    /** Returns the final emission, newest first. */
    private suspend fun kotlinx.coroutines.test.TestScope.assertContract(
        repository: CompletionRepository,
    ): List<CompletionRecord> {
        repository.onCompleted(custom(queens, code, 90_000))
        nowMs += 1_000
        repository.onCompleted(custom(queens, "OTHER1", 10_000))
        repository.onCompleted(custom(tango, code, 11_000))
        repository.onCompleted(daily(queens, 12_000))
        nowMs += 1_000
        repository.onCompleted(custom(queens, code, 40_000))

        val first = repository.observeCustom(queens, code).first()
        val subscribed = CompletableDeferred<Unit>()
        val emissions = async {
            repository.observeCustom(queens, code).onEach { subscribed.complete(Unit) }.take(2).toList()
        }
        subscribed.await()
        nowMs += 1_000
        repository.onCompleted(custom(queens, code, 20_000))
        val last = emissions.await().last()
        assertEquals(listOf(20_000L, 40_000L, 90_000L), last.map { it.time })
        assertTrue(last.all { it.puzzleRef.gameId == queens && it.puzzleRef.key == PuzzleKey.Custom(code) })
        return last
    }

    private fun custom(game: GameId, id: String, timeMs: Long) = record(TestGames.Ref(game, PuzzleKey.Custom(id)), timeMs)

    private fun daily(game: GameId, timeMs: Long) =
        record(TestGames.Ref(game, PuzzleKey.Daily(LocalDate.of(2026, 11, 6), 1)), timeMs)

    private fun record(ref: TestGames.Ref, timeMs: Long) = CompletionRecord(
        puzzleRef = ref,
        time = timeMs,
        hints = 0,
        stats = RecordStats(schemaVersion = 1, blob = "{}"),
    )
}
