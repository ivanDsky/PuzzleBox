package com.ivandsky.puzzlebox.data.match

import androidx.room.Room
import com.ivandsky.puzzlebox.testing.room.RoomQueryThread
import com.ivandsky.puzzlebox.database.PuzzleBoxDatabase
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.testing.repository.FakeMatchLocalRepository
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The match-local contract, run on [RoomMatchLocalRepository] over in-memory Room and on the fake. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [34])
class MatchLocalRepositoryContractTest(private val adapter: String) {

    private var db: PuzzleBoxDatabase? = null
    private var now = Instant.ofEpochMilli(1_000L)
    private val clock = { now }

    private val repository: MatchLocalRepository by lazy {
        when (adapter) {
            ROOM -> RoomMatchLocalRepository(database().matchLocalDao(), clock)
            else -> FakeMatchLocalRepository(clock)
        }
    }

    @After
    fun tearDown() {
        db?.close()
    }

    @Test
    fun `remember stores a new match with open time and no reveal`() = runTest {
        repository.remember(A, LocalRole.Invite, "Ann")

        assertEquals(
            listOf(LocalMatch(A, LocalRole.Invite, "Ann", Instant.ofEpochMilli(1_000L), null, false)),
            repository.observeAll().first(),
        )
    }

    @Test
    fun `remember never downgrades participant to invite`() = runTest {
        repository.remember(A, LocalRole.Participant, null)
        repository.remember(A, LocalRole.Invite, null)

        assertEquals(LocalRole.Participant, only().role)
    }

    @Test
    fun `remember upgrades invite to participant`() = runTest {
        repository.remember(A, LocalRole.Invite, null)
        repository.remember(A, LocalRole.Participant, null)

        assertEquals(LocalRole.Participant, only().role)
    }

    @Test
    fun `remember keeps the opponent name when the new one is null`() = runTest {
        repository.remember(A, LocalRole.Invite, "Ann")
        repository.remember(A, LocalRole.Invite, null)

        assertEquals("Ann", only().opponentName)
    }

    @Test
    fun `remember replaces the opponent name when given`() = runTest {
        repository.remember(A, LocalRole.Invite, "Ann")
        repository.remember(A, LocalRole.Invite, "Anna")

        assertEquals("Anna", only().opponentName)
    }

    @Test
    fun `remember keeps the first open time and the dismissed flag`() = runTest {
        repository.remember(A, LocalRole.Invite, null)
        repository.dismiss(A)
        now = Instant.ofEpochMilli(9_000L)
        repository.remember(A, LocalRole.Participant, "Ann")

        val row = only()
        assertEquals(Instant.ofEpochMilli(1_000L), row.openedAt)
        assertTrue(row.dismissed)
    }

    @Test
    fun `markRevealSeen sets the time and the scores once`() = runTest {
        repository.remember(A, LocalRole.Participant, null)
        assertNull(only().myScore)
        assertNull(only().theirScore)
        repository.markRevealSeen(A, Instant.ofEpochMilli(5_000L), 2, 1)
        repository.markRevealSeen(A, Instant.ofEpochMilli(7_000L), 0, 3)

        assertEquals(Instant.ofEpochMilli(5_000L), only().revealSeenAt)
        assertEquals(2, only().myScore)
        assertEquals(1, only().theirScore)
    }

    @Test
    fun `markRevealSeen remembers a duel this device never stored as a participant`() = runTest {
        repository.markRevealSeen(A, Instant.ofEpochMilli(5_000L), 2, 1)

        val row = only()
        assertEquals(LocalRole.Participant, row.role)
        assertEquals(Instant.ofEpochMilli(5_000L), row.revealSeenAt)
        assertEquals(2, row.myScore)
        assertEquals(1, row.theirScore)
        assertFalse(row.dismissed)
    }

    @Test
    fun `dismiss is a no-op for an unknown code`() = runTest {
        repository.dismiss(A)

        assertTrue(repository.observeAll().first().isEmpty())
    }

    @Test
    fun `dismiss marks the row and observeAll still lists it`() = runTest {
        repository.remember(A, LocalRole.Invite, null)
        repository.dismiss(A)

        assertTrue(only().dismissed)
        assertNull(only().revealSeenAt)
    }

    @Test
    fun `observeAll is newest first and re-emits on change`() = runTest {
        val seen = Channel<List<ShareCode>>(Channel.UNLIMITED)
        val collector = launch(Dispatchers.Default) {
            repository.observeAll().collect { rows -> seen.send(rows.map { it.code }) }
        }
        suspend fun awaitRows(expected: List<ShareCode>) = withContext(Dispatchers.Default) {
            withTimeout(5_000L) { while (seen.receive() != expected) Unit }
        }

        awaitRows(emptyList())
        repository.remember(A, LocalRole.Invite, null)
        awaitRows(listOf(A))
        now = Instant.ofEpochMilli(2_000L)
        repository.remember(B, LocalRole.Invite, null)
        awaitRows(listOf(B, A))
        collector.cancel()
    }

    @Test
    fun `opponentNames maps only rows that have a name`() = runTest {
        repository.remember(A, LocalRole.Invite, "Ann")
        repository.remember(B, LocalRole.Invite, null)

        assertEquals(mapOf(A.value to "Ann"), repository.opponentNames().first())
    }

    private suspend fun only(): LocalMatch = repository.observeAll().first().single()

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

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun adapters(): List<Array<Any>> = listOf(arrayOf(ROOM), arrayOf("fake"))
    }
}
