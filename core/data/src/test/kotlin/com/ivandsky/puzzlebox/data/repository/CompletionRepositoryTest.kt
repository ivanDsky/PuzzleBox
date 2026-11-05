package com.ivandsky.puzzlebox.data.repository

import androidx.room.Room
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.game.TestGames.Ref
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import com.ivandsky.puzzlebox.testing.game.TestGames.beta
import com.ivandsky.puzzlebox.database.PuzzleBoxDatabase
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.model.GameId
import java.time.Instant
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

/**
 * The pinned CompletionRepository contract over real Room (A17): the DoD play-twice test —
 * first win is the record and stays immutable after faster replays; replay rows are kept
 * for stats and never leaderboard-eligible (A5).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CompletionRepositoryTest {

    private lateinit var db: PuzzleBoxDatabase
    private lateinit var repository: RoomCompletionRepository

    private var nowMs = 7_000L

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), PuzzleBoxDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomCompletionRepository(db, db.completionDao(), TestGames.registry(listOf(alpha)), clock = { nowMs })
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun completion(day: LocalDate, puzzleNumber: Int?, timeMs: Long, hints: Int) =
        CompletionRecord(
            puzzleRef = Ref(alpha, day, puzzleNumber),
            time = timeMs,
            hints = hints,
            stats = RecordStats(schemaVersion = 1, blob = """{"v":1,"mistakes":3}"""),
        )

    @Test
    fun `play twice - record immutable, replay kept`() = runTest {
        val day = LocalDate.of(2026, 10, 31)
        val ref = Ref(alpha, day, 592)

        repository.onCompleted(completion(day, 592, timeMs = 95_000, hints = 2))
        nowMs += 60_000
        // The replay is objectively better (A3: 71_000 < 95_000) — the record must not move.
        repository.onCompleted(completion(day, 592, timeMs = 71_000, hints = 0))

        val records = repository.observeRecords().first()
        assertEquals(1, records.size)
        val record = records.single()
        assertEquals(ref, record.puzzleRef)
        assertEquals(95_000L, record.time)
        assertEquals(2, record.hints)
        assertTrue(record.isRecord)
        assertEquals(Instant.ofEpochMilli(7_000L), record.firstCompletedAt)

        val replays = repository.observeReplays(ref).first()
        assertEquals(1, replays.size)
        val replay = replays.single()
        assertEquals(ref, replay.puzzleRef)
        assertEquals(71_000L, replay.time)
        assertEquals(0, replay.hints)
        assertFalse(replay.isRecord)
        assertNull(replay.firstCompletedAt)
    }

    @Test
    fun `distinct refs keep distinct records and replay stores`() = runTest {
        val day = LocalDate.of(2026, 10, 31)
        repository.onCompleted(completion(day, 592, timeMs = 95_000, hints = 2))
        nowMs += 1_000
        repository.onCompleted(completion(day, 593, timeMs = 88_000, hints = 1))
        nowMs += 1_000
        // A replay of 592 must not leak into 593's replay list.
        repository.onCompleted(completion(day, 592, timeMs = 90_000, hints = 0))

        val records = repository.observeRecords().first()
        assertEquals(2, records.size)
        assertEquals(listOf(593, 592), records.map { it.puzzleRef.puzzleNumber })

        assertEquals(1, repository.observeReplays(Ref(alpha, day, 592)).first().size)
        assertEquals(0, repository.observeReplays(Ref(alpha, day, 593)).first().size)
    }

    @Test
    fun `empty stores observe empty before any completion`() = runTest {
        val day = LocalDate.of(2026, 10, 31)
        assertTrue(repository.observeRecords().first().isEmpty())
        assertTrue(repository.observeReplays(Ref(alpha, day, 592)).first().isEmpty())
    }

    @Test
    fun `best time is the game's fastest first solve, never a replay, never merged across games`() = runTest {
        val day = LocalDate.of(2026, 10, 31)
        assertNull(repository.best(alpha))

        repository.onCompleted(completion(day, 592, timeMs = 95_000, hints = 2))
        repository.onCompleted(completion(day, 592, timeMs = 71_000, hints = 0)) // replay
        repository.onCompleted(completion(day.plusDays(1), 593, timeMs = 88_000, hints = 1))
        // Faster rows of other games: a different id, a longer id sharing the prefix, and
        // an id whose `_` would be a LIKE wildcard matching "alpha".
        repository.onCompleted(completion(day, 592, timeMs = 30_000, hints = 0).copy(puzzleRef = Ref(beta, day, 592)))
        repository.onCompleted(completion(day, 592, timeMs = 20_000, hints = 0).copy(puzzleRef = Ref(GameId("alpha2"), day, 592)))

        assertEquals("the 71 s replay never ranks", 88_000L, repository.best(alpha)?.timeMs)
        assertEquals(30_000L, repository.best(beta)?.timeMs)
        assertEquals(20_000L, repository.best(GameId("alpha2"))?.timeMs)
        assertNull(repository.best(GameId("alph_")))
    }
}
