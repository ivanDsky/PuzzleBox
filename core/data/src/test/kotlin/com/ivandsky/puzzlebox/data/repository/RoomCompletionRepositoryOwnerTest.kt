package com.ivandsky.puzzlebox.data.repository

import androidx.room.Room
import com.ivandsky.puzzlebox.testing.room.RoomQueryThread
import com.ivandsky.puzzlebox.database.PuzzleBoxDatabase
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.CompletionSource
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.testing.cloud.FakeOwnerScope
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.game.TestGames.Ref
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import java.time.LocalDate
import com.ivandsky.puzzlebox.data.cloud.LateLastUidSyncStateStore
import com.ivandsky.puzzlebox.data.cloud.SignedInOwnerScope
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
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
 * Completions are owner-scoped (ADR-0029): readers show the visible owner's rows plus ownerless ones, new rows are
 * stamped with the writer uid, and the first win is decided inside the visible set.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomCompletionRepositoryOwnerTest {

    private val day = LocalDate.of(2026, 11, 1)
    private val ref = Ref(alpha, PuzzleKey.Daily(day, 592))
    private val scope = FakeOwnerScope()
    private var nowMs = 1_000L
    private lateinit var db: PuzzleBoxDatabase
    private lateinit var repository: RoomCompletionRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), PuzzleBoxDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(RoomQueryThread.executor)
            .build()
        repository = RoomCompletionRepository(
            db,
            db.completionDao(),
            db.completionReplayDao(),
            TestGames.registry(listOf(alpha)),
            scope,
            clock = { nowMs },
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun completion(
        timeMs: Long,
        key: PuzzleKey = ref.key,
        source: CompletionSource = CompletionSource.App,
    ) = CompletionRecord(
        puzzleRef = Ref(alpha, key),
        time = timeMs,
        hints = 0,
        stats = RecordStats(schemaVersion = 1, blob = """{"v":1}"""),
        source = source,
    )

    private suspend fun finishAs(uid: String?, timeMs: Long, key: PuzzleKey = ref.key, source: CompletionSource = CompletionSource.App) {
        scope.writerUid = uid
        repository.onCompleted(completion(timeMs, key, source))
        nowMs += 1_000
    }

    /**
     * Runs [DRAIN_ROUNDS] no-op tasks through the one Room thread, one after another. A transaction's queries reach that
     * thread one hop at a time, so every round lets work already on it advance; nothing here waits on the clock.
     */
    private suspend fun drainRoomThread() {
        repeat(DRAIN_ROUNDS) {
            suspendCancellableCoroutine { done -> RoomQueryThread.executor.execute { done.resume(Unit) } }
        }
    }

    private fun storedOwners(): List<Pair<String?, Boolean>> =
        db.query("SELECT ownerUid, isRecord FROM completions ORDER BY id", null).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add((if (cursor.isNull(0)) null else cursor.getString(0)) to (cursor.getInt(1) == 1))
                }
            }
        }

    @Test
    fun `a new completion is stamped with the writer uid`() = runTest {
        finishAs("A", 90_000)
        finishAs(null, 80_000, PuzzleKey.Daily(day, 593))

        assertEquals(listOf<Pair<String?, Boolean>>("A" to true, null to true), storedOwners())
    }

    @Test
    fun `rows of another owner are hidden from every reader`() = runTest {
        val custom = PuzzleKey.Custom("K7Q2MX")
        finishAs("A", 90_000)
        finishAs("A", 70_000) // replay of the same ref
        finishAs("A", 60_000, custom)
        finishAs("A", 50_000, PuzzleKey.Daily(day, 600), CompletionSource.LinkedIn)

        scope.visibleOwner = "B"

        assertTrue(repository.observeRecords().first().isEmpty())
        assertTrue(repository.observePlayAgains(ref).first().isEmpty())
        assertTrue(repository.observeCustom(alpha, "K7Q2MX").first().isEmpty())
        assertTrue(repository.observeImports().first().isEmpty())
        assertNull(repository.best(alpha))

        scope.visibleOwner = "A"

        assertEquals(3, repository.observeRecords().first().size)
        assertEquals(1, repository.observePlayAgains(ref).first().size)
        assertEquals(1, repository.observeCustom(alpha, "K7Q2MX").first().size)
        assertEquals(1, repository.observeImports().first().size)
        assertEquals(50_000L, repository.best(alpha)?.timeMs)
    }

    @Test
    fun `ownerless rows are visible to every owner`() = runTest {
        finishAs(null, 90_000)

        scope.visibleOwner = "A"
        assertEquals(1, repository.observeRecords().first().size)
        assertEquals(90_000L, repository.best(alpha)?.timeMs)

        scope.visibleOwner = "B"
        assertEquals(1, repository.observeRecords().first().size)
        assertEquals(90_000L, repository.best(alpha)?.timeMs)
    }

    @Test
    fun `an owner change re-emits an open observer`() = runTest {
        finishAs("A", 90_000)
        scope.visibleOwner = "A"
        val seen = Channel<Int>(Channel.UNLIMITED)
        backgroundScope.launch {
            repository.observeRecords().collect { seen.send(it.size) }
        }
        assertEquals(1, seen.receive())

        scope.visibleOwner = "B"

        assertEquals(0, seen.receive())
    }

    @Test
    fun `B's first finish of a puzzle A finished is B's record`() = runTest {
        finishAs("A", 90_000)

        scope.visibleOwner = "B"
        finishAs("B", 100_000)

        assertEquals(listOf<Pair<String?, Boolean>>("A" to true, "B" to true), storedOwners())
        val visible = repository.observeRecords().first()
        assertEquals(listOf(100_000L), visible.map { it.time })
        assertTrue(visible.single().isRecord)

        // B's second finish is B's replay, not a second record.
        finishAs("B", 80_000)
        assertEquals(listOf(true, true, false), storedOwners().map { it.second })
        assertEquals(listOf(80_000L), repository.observePlayAgains(ref).first().map { it.time })
    }

    @Test
    fun `a LinkedIn import is idempotent per owner, not across owners`() = runTest {
        val key = PuzzleKey.Daily(day, 600)
        finishAs("A", 50_000, key, CompletionSource.LinkedIn)
        finishAs("A", 50_000, key, CompletionSource.LinkedIn)
        scope.visibleOwner = "B"
        finishAs("B", 55_000, key, CompletionSource.LinkedIn)

        assertEquals(listOf<Pair<String?, Boolean>>("A" to true, "B" to true), storedOwners())
    }

    @Test
    fun `a LinkedIn import before the last uid has loaded waits and dedupes against that account only`() = runTest {
        val key = PuzzleKey.Daily(day, 600)
        finishAs("B", 55_000, key, CompletionSource.LinkedIn)
        val store = LateLastUidSyncStateStore()
        val ownerScope = SignedInOwnerScope(
            FakeIdentityService(), store, CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob()),
        )
        val cold = RoomCompletionRepository(
            db, db.completionDao(), db.completionReplayDao(), TestGames.registry(listOf(alpha)), ownerScope, clock = { nowMs },
        )

        val import = launch { cold.onCompleted(completion(50_000, key, CompletionSource.LinkedIn)) }
        runCurrent()
        // An import that did not wait for the owner would by now have run its queries on Room's thread and finished.
        drainRoomThread()
        assertFalse("the import must wait for the last uid", import.isCompleted)
        assertEquals(listOf<Pair<String?, Boolean>>("B" to true), storedOwners())

        store.load("A")
        import.join()

        // A has no import of this puzzle yet, so it lands; B's row is not A's to dedupe against.
        assertEquals(2, storedOwners().size)
    }

    @Test
    fun `flag-off scope shows every owner's rows`() = runTest {
        finishAs("A", 90_000)
        finishAs("B", 80_000, PuzzleKey.Daily(day, 593))

        scope.visibleOwner = null

        assertEquals(2, repository.observeRecords().first().size)
        assertEquals(80_000L, repository.best(alpha)?.timeMs)
    }

    private companion object {
        /** Far more hops than `onCompleted` takes to finish its queries. */
        const val DRAIN_ROUNDS = 200
    }
}
