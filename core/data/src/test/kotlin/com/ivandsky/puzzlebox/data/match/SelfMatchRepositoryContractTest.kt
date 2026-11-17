package com.ivandsky.puzzlebox.data.match

import androidx.room.Room
import com.ivandsky.puzzlebox.data.model.PuzzleRefKeys
import com.ivandsky.puzzlebox.database.PuzzleBoxDatabase
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.model.SolveReplay
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSelfMatchRepository
import com.ivandsky.puzzlebox.testing.room.RoomQueryThread
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The self-duel store contract, run on [RoomSelfMatchRepository] over in-memory Room and on the fake. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [34])
class SelfMatchRepositoryContractTest(private val adapter: String) {

    private var db: PuzzleBoxDatabase? = null

    /** Only alpha is registered, so a beta leg stands for a game this build no longer ships. */
    private val registry = TestGames.registry(listOf(TestGames.alpha))
    private val completions = FakeCompletionRepository()

    private val repository: SelfMatchRepository by lazy {
        when (adapter) {
            ROOM -> RoomSelfMatchRepository(database().selfMatchDao(), completions, registry)
            else -> FakeSelfMatchRepository { ref -> PuzzleRefKeys.decode(ref.wire, registry)?.let { completions.recordReplay(it) } }
        }
    }

    @After
    fun tearDown() {
        db?.close()
    }

    @Test
    fun `holds follows save and remove`() = runTest {
        assertFalse(repository.holds(A))

        repository.save(selfMatch(), past())
        assertTrue(repository.holds(A))
        assertFalse(repository.holds(B))

        repository.remove(A)
        assertFalse(repository.holds(A))
    }

    @Test
    fun `a saved duel reads back as a self duel with past you done`() = runTest {
        repository.save(selfMatch(), past())

        val read = held()
        assertEquals(selfMatch(), read)
        assertTrue(read.isSelf)
        assertEquals(setOf(0, 1, 2), read.progress[SelfSeats.PAST])
        assertEquals(emptySet<Int>(), read.progress[SelfSeats.ME])
    }

    @Test
    fun `an unknown code is not found`() = runTest {
        repository.save(selfMatch(), past())

        assertEquals(MatchFetch.NotFound, repository.observe(B).first())
    }

    @Test
    fun `save keeps the challenger and rebuilds what a self duel fixes`() = runTest {
        val me = Player("ann-uid", "Ann | \"Z\" ~\n100%", "https://x.test/a b?c=d|e&f=~")
        val loose = selfMatch().copy(
            formatVersion = 7,
            createdAt = START.plusNanos(999),
            deadlineHours = 24,
            challenger = me,
            invitedUid = "bob",
            opponent = null,
            includeFriends = false,
            cancelled = true,
            declined = true,
            rematchOf = B,
            progress = mapOf("bob" to setOf(0)),
        )

        repository.save(loose, past())

        assertEquals(selfMatch().copy(challenger = me.copy(uid = SelfSeats.ME), includeFriends = false), held())
    }

    @Test
    fun `a duel without legs round trips`() = runTest {
        repository.save(selfMatch(legs = emptyList()), emptyList())

        assertEquals(selfMatch(legs = emptyList()), held())
    }

    @Test
    fun `observeAll is newest first and re-emits on save, post and remove`() = runTest {
        val seen = Channel<List<Match>>(Channel.UNLIMITED)
        val collector = launch(Dispatchers.Default) { repository.observeAll().collect { seen.send(it) } }
        suspend fun awaitRows(expected: List<Match>) = withContext(Dispatchers.Default) {
            withTimeout(5_000L) { while (seen.receive() != expected) Unit }
        }
        val older = selfMatch(A, START)
        val newer = selfMatch(B, START.plusSeconds(60))

        awaitRows(emptyList())
        repository.save(older, past())
        awaitRows(listOf(older))
        repository.save(newer, past())
        awaitRows(listOf(newer, older))
        repository.post(A, mine(2))
        awaitRows(listOf(newer, older.withMine(2)))
        repository.remove(B)
        awaitRows(listOf(older.withMine(2)))
        collector.cancel()
    }

    @Test
    fun `post stores my result and grows my progress`() = runTest {
        repository.save(selfMatch(), past())

        assertEquals(PostResult.Posted, repository.post(A, mine(1)))
        assertEquals(selfMatch().withMine(1), held())
        assertEquals(PostResult.Posted, repository.post(A, mine(0, timeMs = 30_000L).copy(gaveUp = true)))

        assertEquals(selfMatch().withMine(0, 1), held())
        assertEquals(ResultRead.Found(mine(1)), repository.readResult(A, 1, SelfSeats.ME))
        assertEquals(ResultRead.Found(mine(0, timeMs = 30_000L).copy(gaveUp = true)), repository.readResult(A, 0, SelfSeats.ME))
    }

    @Test
    fun `an open observe re-emits on save, post and remove`() = runTest {
        val seen = Channel<MatchFetch>(Channel.UNLIMITED)
        val collector = launch(Dispatchers.Default) { repository.observe(A).collect { seen.send(it) } }
        suspend fun awaitFetch(expected: MatchFetch) = withContext(Dispatchers.Default) {
            withTimeout(5_000L) { while (seen.receive() != expected) Unit }
        }

        awaitFetch(MatchFetch.NotFound)
        repository.save(selfMatch(), past())
        awaitFetch(MatchFetch.Found(selfMatch()))
        repository.post(A, mine(1))
        awaitFetch(MatchFetch.Found(selfMatch().withMine(1)))
        repository.remove(A)
        awaitFetch(MatchFetch.NotFound)
        collector.cancel()
    }

    @Test
    fun `a repeat post is a duplicate and keeps the first result`() = runTest {
        repository.save(selfMatch(), past())
        repository.post(A, mine(1, timeMs = 40_000L))

        assertEquals(PostResult.Duplicate, repository.post(A, mine(1, timeMs = 1L)))

        assertEquals(ResultRead.Found(mine(1, timeMs = 40_000L)), repository.readResult(A, 1, SelfSeats.ME))
        assertEquals(selfMatch().withMine(1), held())
    }

    @Test
    fun `post is rejected for an unknown code, another seat and a leg out of range`() = runTest {
        repository.save(selfMatch(), past())

        assertTrue(repository.post(B, mine(0)) is PostResult.Rejected)
        assertTrue(repository.post(A, mine(0).copy(uid = SelfSeats.PAST)) is PostResult.Rejected)
        assertTrue(repository.post(A, mine(0).copy(uid = "ann-uid")) is PostResult.Rejected)
        assertTrue(repository.post(A, mine(3)) is PostResult.Rejected)
        assertTrue(repository.post(A, mine(-1)) is PostResult.Rejected)

        assertEquals(selfMatch(), held())
        assertFalse(repository.holds(B))
    }

    @Test
    fun `past you reads before my leg posts with the first solve's replay`() = runTest {
        val first = replay(1)
        completions.onCompleted(record(DAILY), first)
        completions.onCompleted(record(DAILY).copy(time = 10L), replay(2))
        completions.onCompleted(record(CUSTOM), replay(3))
        repository.save(selfMatch(), past())

        assertEquals(ResultRead.Found(past()[0].copy(replay = first)), repository.readResult(A, 0, SelfSeats.PAST))
        assertEquals(ResultRead.Found(past()[1].copy(replay = replay(3))), repository.readResult(A, 1, SelfSeats.PAST))
    }

    @Test
    fun `past you reads without a replay when the first solve has none`() = runTest {
        completions.onCompleted(record(DAILY), replay = null)
        completions.onCompleted(record(DAILY).copy(time = 10L), replay(2))
        repository.save(selfMatch(), past())

        assertEquals(ResultRead.Found(past()[0]), repository.readResult(A, 0, SelfSeats.PAST))
        // No completion row at all, and a game this build does not register.
        assertEquals(ResultRead.Found(past()[1]), repository.readResult(A, 1, SelfSeats.PAST))
        assertEquals(ResultRead.Found(past()[2]), repository.readResult(A, 2, SelfSeats.PAST))
    }

    @Test
    fun `past you is frozen as saved whatever seat and flags the caller passed`() = runTest {
        val loose = past().map { it.copy(uid = "ann-uid", gaveUp = true, replay = replay(9)) }

        repository.save(selfMatch(), loose)

        assertEquals(ResultRead.Found(past()[1]), repository.readResult(A, 1, SelfSeats.PAST))
    }

    @Test
    fun `my result is missing until posted and reads back without its replay`() = runTest {
        repository.save(selfMatch(), past())
        assertEquals(ResultRead.Missing, repository.readResult(A, 1, SelfSeats.ME))

        repository.post(A, mine(1).copy(replay = replay(4)))

        assertEquals(ResultRead.Found(mine(1)), repository.readResult(A, 1, SelfSeats.ME))
        assertEquals(ResultRead.Missing, repository.readResult(A, 0, SelfSeats.ME))
    }

    @Test
    fun `readResult is missing for an unknown code, leg or seat`() = runTest {
        repository.save(selfMatch(), past())
        repository.post(A, mine(0))

        assertEquals(ResultRead.Missing, repository.readResult(B, 0, SelfSeats.PAST))
        assertEquals(ResultRead.Missing, repository.readResult(A, 3, SelfSeats.PAST))
        assertEquals(ResultRead.Missing, repository.readResult(A, -1, SelfSeats.ME))
        assertEquals(ResultRead.Missing, repository.readResult(A, 0, "ann-uid"))
    }

    @Test
    fun `remove forgets the duel, past you and my results`() = runTest {
        repository.save(selfMatch(), past())
        repository.save(selfMatch(B), past())
        repository.post(A, mine(0))

        repository.remove(A)

        assertEquals(MatchFetch.NotFound, repository.observe(A).first())
        assertEquals(ResultRead.Missing, repository.readResult(A, 0, SelfSeats.PAST))
        assertEquals(ResultRead.Missing, repository.readResult(A, 0, SelfSeats.ME))
        assertEquals(listOf(selfMatch(B)), repository.observeAll().first())
        assertEquals(ResultRead.Found(past()[0]), repository.readResult(B, 0, SelfSeats.PAST))
    }

    @Test
    fun `save over a held code replaces the duel and drops my results`() = runTest {
        repository.save(selfMatch(), past())
        repository.post(A, mine(0))
        val again = selfMatch(legs = listOf(CUSTOM, DAILY))
        val slower = past(again.legs).map { it.copy(timeMs = 99_000L) }

        repository.save(again, slower)

        assertEquals(again, held())
        assertEquals(ResultRead.Missing, repository.readResult(A, 0, SelfSeats.ME))
        assertEquals(ResultRead.Found(slower[0]), repository.readResult(A, 0, SelfSeats.PAST))
        assertEquals(ResultRead.Missing, repository.readResult(A, 2, SelfSeats.PAST))
    }

    @Test
    fun `the friend duel calls are not offered and change nothing`() = runTest {
        repository.save(selfMatch(), past())
        val draft = MatchDraft(B, 24, Player("ann-uid", "Ann", null), null, LEGS, includeFriends = true, rematchOf = null)

        assertEquals(false, (repository.puzzleCodeTaken(A) as PuzzleCodeCheck.Unavailable).offline)
        assertTrue(repository.create(draft) is CreateResult.Rejected)
        assertTrue(repository.claim(A, Player("bob-uid", "Bob", null)) is ClaimResult.Rejected)
        assertTrue(repository.cancel(A) is WriteResult.Rejected)
        assertTrue(repository.decline(A) is WriteResult.Rejected)

        assertEquals(listOf(selfMatch()), repository.observeAll().first())
    }

    @Test
    fun `observeMine is an empty list even with duels held`() = runTest {
        repository.save(selfMatch(), past())

        assertEquals(emptyList<Match>(), repository.observeMine().first())
    }

    private suspend fun held(code: ShareCode = A): Match = (repository.observe(code).first() as MatchFetch.Found).match

    /** A self duel exactly as the store reads it back. */
    private fun selfMatch(code: ShareCode = A, createdAt: Instant = START, legs: List<MatchLeg> = LEGS) = Match(
        code = code,
        formatVersion = Match.FORMAT_VERSION,
        createdAt = createdAt,
        deadlineHours = SelfSeats.NO_DEADLINE_HOURS,
        challenger = Player(SelfSeats.ME, "You", null),
        invitedUid = null,
        opponent = Player(SelfSeats.PAST, SelfSeats.PAST_NAME, null),
        legs = legs,
        includeFriends = true,
        cancelled = false,
        declined = false,
        rematchOf = null,
        progress = mapOf(SelfSeats.PAST to legs.indices.toSet(), SelfSeats.ME to emptySet()),
    )

    private fun Match.withMine(vararg played: Int) = copy(progress = progress + (SelfSeats.ME to played.toSet()))

    private fun past(legs: List<MatchLeg> = LEGS) = legs.indices.map { leg ->
        LegResult(SelfSeats.PAST, leg, timeMs = 60_000L + leg, hints = leg, completedAtMs = 1_000L + leg, gaveUp = false)
    }

    private fun mine(leg: Int, timeMs: Long = 50_000L) =
        LegResult(SelfSeats.ME, leg, timeMs, hints = 2, completedAtMs = 9_000L + leg, gaveUp = false)

    private fun record(leg: MatchLeg): CompletionRecord {
        val key = when (val ref = leg.ref) {
            is LegRef.Daily -> PuzzleKey.Daily(ref.day, ref.number)
            is LegRef.Custom -> PuzzleKey.Custom(ref.code.value)
        }
        return CompletionRecord(TestGames.Ref(leg.ref.gameId, key), time = 60_000L, hints = 0, stats = RecordStats(1, "{}"))
    }

    private fun replay(n: Int) = requireNotNull(SolveReplay.of(byteArrayOf(n.toByte(), 2, 3)))

    private fun database(): PuzzleBoxDatabase = db ?: Room
        .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), PuzzleBoxDatabase::class.java)
        .allowMainThreadQueries()
        .setQueryExecutor(RoomQueryThread.executor)
        .build()
        .also { db = it }

    companion object {
        private const val ROOM = "room"
        private val A = requireNotNull(ShareCode.parse("ABC234"))
        private val B = requireNotNull(ShareCode.parse("XYZ567"))
        private val START: Instant = Instant.ofEpochMilli(1_700_000_000_000L)

        private val DAILY = MatchLeg(LegRef.Daily(TestGames.alpha, LocalDate.of(2026, 10, 1), 224), authorUid = null)
        private val CUSTOM =
            MatchLeg(LegRef.Custom(TestGames.alpha, requireNotNull(ShareCode.parse("K7Q2MX"))), authorUid = "ann|uid ~1,2\n100%/é")
        private val UNREGISTERED = MatchLeg(LegRef.Daily(TestGames.beta, LocalDate.of(2026, 9, 30), 7), authorUid = "")
        private val LEGS = listOf(DAILY, CUSTOM, UNREGISTERED)

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun adapters(): List<Array<Any>> = listOf(arrayOf(ROOM), arrayOf("fake"))
    }
}
