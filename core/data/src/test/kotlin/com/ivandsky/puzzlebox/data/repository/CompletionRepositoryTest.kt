package com.ivandsky.puzzlebox.data.repository

import androidx.room.Room
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.game.TestGames.Ref
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import com.ivandsky.puzzlebox.testing.game.TestGames.beta
import com.ivandsky.puzzlebox.data.model.PuzzleRefKeys
import com.ivandsky.puzzlebox.database.CompletionEntity
import com.ivandsky.puzzlebox.database.PuzzleBoxDatabase
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.CompletionSource
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
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
 * The pinned CompletionRepository contract over real Room: the play-twice test —
 * first win is the record and stays immutable after faster play-agains; play-again rows are kept
 * for stats and never leaderboard-eligible.
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

    private fun completion(
        day: LocalDate,
        puzzleNumber: Int?,
        timeMs: Long,
        hints: Int,
        matchCode: String? = null,
        source: CompletionSource = CompletionSource.App,
    ) =
        CompletionRecord(
            puzzleRef = Ref(alpha, PuzzleKey.Daily(day, puzzleNumber)),
            time = timeMs,
            hints = hints,
            stats = RecordStats(schemaVersion = 1, blob = """{"v":1,"mistakes":3}"""),
            matchCode = matchCode,
            source = source,
        )

    @Test
    fun `play twice - record immutable, play-again kept`() = runTest {
        val day = LocalDate.of(2026, 10, 31)
        val ref = Ref(alpha, PuzzleKey.Daily(day, 592))

        repository.onCompleted(completion(day, 592, timeMs = 95_000, hints = 2))
        nowMs += 60_000
        // The play-again is objectively better (71_000 < 95_000) — the record must not move.
        repository.onCompleted(completion(day, 592, timeMs = 71_000, hints = 0))

        val records = repository.observeRecords().first()
        assertEquals(1, records.size)
        val record = records.single()
        assertEquals(ref, record.puzzleRef)
        assertEquals(95_000L, record.time)
        assertEquals(2, record.hints)
        assertTrue(record.isRecord)
        assertEquals(Instant.ofEpochMilli(7_000L), record.firstCompletedAt)

        val playAgains = repository.observePlayAgains(ref).first()
        assertEquals(1, playAgains.size)
        val playAgain = playAgains.single()
        assertEquals(ref, playAgain.puzzleRef)
        assertEquals(71_000L, playAgain.time)
        assertEquals(0, playAgain.hints)
        assertFalse(playAgain.isRecord)
        assertNull(playAgain.firstCompletedAt)
    }

    @Test
    fun `distinct refs keep distinct records and play-again stores`() = runTest {
        val day = LocalDate.of(2026, 10, 31)
        repository.onCompleted(completion(day, 592, timeMs = 95_000, hints = 2))
        nowMs += 1_000
        repository.onCompleted(completion(day, 593, timeMs = 88_000, hints = 1))
        nowMs += 1_000
        // A play-again of 592 must not leak into 593's play-again list.
        repository.onCompleted(completion(day, 592, timeMs = 90_000, hints = 0))

        val records = repository.observeRecords().first()
        assertEquals(2, records.size)
        assertEquals(listOf(593, 592), records.map { (it.puzzleRef.key as PuzzleKey.Daily).puzzleNumber })

        assertEquals(1, repository.observePlayAgains(Ref(alpha, PuzzleKey.Daily(day, 592))).first().size)
        assertEquals(0, repository.observePlayAgains(Ref(alpha, PuzzleKey.Daily(day, 593))).first().size)
    }

    @Test
    fun `empty stores observe empty before any completion`() = runTest {
        val day = LocalDate.of(2026, 10, 31)
        assertTrue(repository.observeRecords().first().isEmpty())
        assertTrue(repository.observePlayAgains(Ref(alpha, PuzzleKey.Daily(day, 592))).first().isEmpty())
    }

    @Test
    fun `best time is the game's fastest first solve, never a play-again, never merged across games`() = runTest {
        val day = LocalDate.of(2026, 10, 31)
        assertNull(repository.best(alpha))

        repository.onCompleted(completion(day, 592, timeMs = 95_000, hints = 2))
        repository.onCompleted(completion(day, 592, timeMs = 71_000, hints = 0)) // play-again
        repository.onCompleted(completion(day.plusDays(1), 593, timeMs = 88_000, hints = 1))
        // Faster rows of other games: a different id, a longer id sharing the prefix, and
        // an id whose `_` would be a LIKE wildcard matching "alpha".
        repository.onCompleted(completion(day, 592, timeMs = 30_000, hints = 0).copy(puzzleRef = Ref(beta, PuzzleKey.Daily(day, 592))))
        repository.onCompleted(completion(day, 592, timeMs = 20_000, hints = 0).copy(puzzleRef = Ref(GameId("alpha2"), PuzzleKey.Daily(day, 592))))

        assertEquals("the 71 s replay never ranks", 88_000L, repository.best(alpha)?.timeMs)
        assertEquals(30_000L, repository.best(beta)?.timeMs)
        assertEquals(20_000L, repository.best(GameId("alpha2"))?.timeMs)
        assertNull(repository.best(GameId("alph_")))
    }

    @Test
    fun `a completion with a match code reads back with it`() = runTest {
        val day = LocalDate.of(2026, 10, 31)
        repository.onCompleted(completion(day, 592, timeMs = 95_000, hints = 0, matchCode = "ABC123"))

        assertEquals("ABC123", repository.observeRecords().first().single().matchCode)
    }

    @Test
    fun `a play-again keeps its own match code`() = runTest {
        val day = LocalDate.of(2026, 10, 31)
        val ref = Ref(alpha, PuzzleKey.Daily(day, 592))
        repository.onCompleted(completion(day, 592, timeMs = 95_000, hints = 0))
        nowMs += 1_000
        repository.onCompleted(completion(day, 592, timeMs = 71_000, hints = 0, matchCode = "XYZ789"))

        assertNull(repository.observeRecords().first().single().matchCode)
        assertEquals("XYZ789", repository.observePlayAgains(ref).first().single().matchCode)
    }

    @Test
    fun `a LinkedIn import reads back as LinkedIn and an app completion as App`() = runTest {
        val day = LocalDate.of(2026, 10, 31)
        repository.onCompleted(completion(day, 592, timeMs = 95_000, hints = 0, source = CompletionSource.LinkedIn))
        nowMs += 1_000
        repository.onCompleted(completion(day, 593, timeMs = 88_000, hints = 0))

        val sources = repository.observeRecords().first().associate { (it.puzzleRef.key as PuzzleKey.Daily).puzzleNumber to it.source }
        assertEquals(mapOf(592 to CompletionSource.LinkedIn, 593 to CompletionSource.App), sources)
    }

    @Test
    fun `a play-again keeps the source it was stored with`() = runTest {
        val day = LocalDate.of(2026, 10, 31)
        val ref = Ref(alpha, PuzzleKey.Daily(day, 592))
        repository.onCompleted(completion(day, 592, timeMs = 95_000, hints = 0))
        nowMs += 1_000
        repository.onCompleted(completion(day, 592, timeMs = 71_000, hints = 0, source = CompletionSource.LinkedIn))

        assertEquals(CompletionSource.App, repository.observeRecords().first().single().source)
        assertEquals(CompletionSource.LinkedIn, repository.observePlayAgains(ref).first().single().source)
    }

    @Test
    fun `a row with a source this build does not know reads back as App`() = runTest {
        val day = LocalDate.of(2026, 10, 31)
        db.completionDao().insert(
            CompletionEntity(
                puzzleRef = PuzzleRefKeys.encode(Ref(alpha, PuzzleKey.Daily(day, 592))),
                timeMs = 95_000,
                hints = 0,
                statsVersion = 1,
                statsBlob = """{"v":1}""",
                completedAtMs = 7_000,
                firstCompletedAtMs = 7_000,
                isRecord = true,
                source = "future",
            ),
        )

        assertEquals(CompletionSource.App, repository.observeRecords().first().single().source)
    }

    @Test
    fun `re-importing a ref inserts nothing and keeps the first import`() = runTest {
        val day = LocalDate.of(2026, 10, 31)
        repository.onCompleted(completion(day, 592, timeMs = 95_000, hints = 0, source = CompletionSource.LinkedIn))
        nowMs += 60_000
        repository.onCompleted(completion(day, 592, timeMs = 71_000, hints = 1, source = CompletionSource.LinkedIn))

        val imports = repository.observeImports().first()
        assertEquals(1, imports.size)
        assertEquals(95_000L, imports.single().time)
        assertEquals(0, imports.single().hints)
    }

    @Test
    fun `an import onto an app record is a play-again and leaves the record untouched`() = runTest {
        val day = LocalDate.of(2026, 10, 31)
        repository.onCompleted(completion(day, 592, timeMs = 95_000, hints = 2))
        nowMs += 1_000
        repository.onCompleted(completion(day, 592, timeMs = 71_000, hints = 0, source = CompletionSource.LinkedIn))

        val record = repository.observeRecords().first().single()
        assertEquals(95_000L, record.time)
        assertEquals(2, record.hints)
        assertEquals(CompletionSource.App, record.source)
        val import = repository.observeImports().first().single()
        assertFalse(import.isRecord)
        assertEquals(71_000L, import.time)
    }

    @Test
    fun `an app solve after an import is a play-again`() = runTest {
        val day = LocalDate.of(2026, 10, 31)
        val ref = Ref(alpha, PuzzleKey.Daily(day, 592))
        repository.onCompleted(completion(day, 592, timeMs = 95_000, hints = 0, source = CompletionSource.LinkedIn))
        nowMs += 1_000
        repository.onCompleted(completion(day, 592, timeMs = 71_000, hints = 0))

        val record = repository.observeRecords().first().single()
        assertTrue(record.isRecord)
        assertEquals(CompletionSource.LinkedIn, record.source)
        assertEquals(CompletionSource.App, repository.observePlayAgains(ref).first().single().source)
    }

    @Test
    fun `an import with no other completion is a record and an import`() = runTest {
        val day = LocalDate.of(2026, 10, 31)
        repository.onCompleted(completion(day, 592, timeMs = 95_000, hints = 0, source = CompletionSource.LinkedIn))

        val record = repository.observeRecords().first().single()
        assertTrue(record.isRecord)
        assertEquals(CompletionSource.LinkedIn, record.source)
        assertEquals(listOf(record), repository.observeImports().first())
    }

    @Test
    fun `room keeps one import and hides the play-again from records`() = runTest {
        assertImportContract(repository)
    }

    @Test
    fun `fake keeps one import and hides the play-again from records`() = runTest {
        assertImportContract(FakeCompletionRepository())
    }

    /** One app row and a LinkedIn import of the same ref, imported twice: the import stays a single non-record row. */
    private suspend fun assertImportContract(repository: CompletionRepository) {
        val day = LocalDate.of(2026, 10, 31)
        repository.onCompleted(completion(day, 592, timeMs = 95_000, hints = 2).copy(isRecord = true))
        val import = completion(day, 592, timeMs = 71_000, hints = 0, source = CompletionSource.LinkedIn)
        repository.onCompleted(import)
        repository.onCompleted(import)

        assertEquals(listOf(CompletionSource.App), repository.observeRecords().first().map { it.source })
        assertEquals(listOf(CompletionSource.LinkedIn), repository.observeImports().first().map { it.source })
    }
}
