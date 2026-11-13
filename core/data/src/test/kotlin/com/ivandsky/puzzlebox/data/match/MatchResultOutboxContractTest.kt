package com.ivandsky.puzzlebox.data.match

import androidx.room.Room
import com.ivandsky.puzzlebox.testing.room.RoomQueryThread
import com.ivandsky.puzzlebox.database.PuzzleBoxDatabase
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.model.SolveReplay
import com.ivandsky.puzzlebox.testing.repository.FakeMatchResultOutbox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The outbox contract, run on [RoomMatchResultOutbox] over in-memory Room and on the fake. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [34])
class MatchResultOutboxContractTest(private val adapter: String) {

    private var db: PuzzleBoxDatabase? = null

    private val outbox: MatchResultOutbox by lazy {
        when (adapter) {
            ROOM -> RoomMatchResultOutbox(database().matchResultDao())
            else -> FakeMatchResultOutbox()
        }
    }

    @After
    fun tearDown() {
        db?.close()
    }

    @Test
    fun `enqueue stores a pending result with zero attempts`() = runTest {
        val gaveUp = leg("me", 0, completedAt = 10L, gaveUp = true, timeMs = 0L)
        outbox.enqueue(A, gaveUp)
        outbox.enqueue(A, leg("me", 1, completedAt = 20L))

        assertEquals(
            listOf(
                OutboxResult(A, gaveUp, ResultPostState.Pending, 0),
                OutboxResult(A, leg("me", 1, completedAt = 20L), ResultPostState.Pending, 0),
            ),
            outbox.observe(A).first(),
        )
    }

    @Test
    fun `enqueue twice for the same puzzle and player keeps the first`() = runTest {
        val first = leg("me", 0, completedAt = 10L, timeMs = 5_000L)
        outbox.enqueue(A, first)
        outbox.update(A, 0, "me", ResultPostState.Posted, 2)
        outbox.enqueue(A, leg("me", 0, completedAt = 99L, timeMs = 1L))

        assertEquals(listOf(OutboxResult(A, first, ResultPostState.Posted, 2)), outbox.observe(A).first())
    }

    @Test
    fun `the same puzzle for the other player is a separate row`() = runTest {
        outbox.enqueue(A, leg("me", 0, completedAt = 10L))
        outbox.enqueue(A, leg("you", 0, completedAt = 11L))

        assertEquals(setOf("me", "you"), outbox.observe(A).first().map { it.result.uid }.toSet())
        assertEquals(2, outbox.observe(A).first().size)
    }

    @Test
    fun `a queued result keeps its replay`() = runTest {
        val replay = requireNotNull(SolveReplay.of(byteArrayOf(1, 2, 3, 4)))
        val withReplay = leg("me", 0, completedAt = 10L).copy(replay = replay)
        outbox.enqueue(A, withReplay)

        assertEquals(listOf(OutboxResult(A, withReplay, ResultPostState.Pending, 0)), outbox.observe(A).first())
        assertEquals(replay, outbox.observe(A).first().single().result.replay)
    }

    @Test
    fun `a result without a replay reads null`() = runTest {
        outbox.enqueue(A, leg("me", 0, completedAt = 10L))

        assertNull(outbox.observe(A).first().single().result.replay)
    }

    @Test
    fun `update changes state and attempts, and nothing else`() = runTest {
        val mine = leg("me", 0, completedAt = 10L)
        val other = leg("me", 1, completedAt = 20L)
        outbox.enqueue(A, mine)
        outbox.enqueue(A, other)

        outbox.update(A, 0, "me", ResultPostState.Late, 3)

        assertEquals(
            listOf(
                OutboxResult(A, mine, ResultPostState.Late, 3),
                OutboxResult(A, other, ResultPostState.Pending, 0),
            ),
            outbox.observe(A).first(),
        )
    }

    @Test
    fun `update of a missing row does nothing`() = runTest {
        outbox.update(A, 0, "me", ResultPostState.Posted, 1)
        outbox.enqueue(A, leg("me", 0, completedAt = 10L))
        outbox.update(A, 0, "you", ResultPostState.Posted, 1)
        outbox.update(B, 0, "me", ResultPostState.Posted, 1)

        assertEquals(listOf(ResultPostState.Pending), outbox.observeAll().first().map { it.state })
    }

    @Test
    fun `observe lists one match ordered by leg index and ignores other matches`() = runTest {
        outbox.enqueue(A, leg("me", 2, completedAt = 10L))
        outbox.enqueue(A, leg("me", 0, completedAt = 30L))
        outbox.enqueue(B, leg("me", 1, completedAt = 20L))

        assertEquals(listOf(0, 2), outbox.observe(A).first().map { it.result.legIndex })
        assertTrue(outbox.observe(C).first().isEmpty())
    }

    @Test
    fun `observePending lists only pending rows across matches, oldest completion first`() = runTest {
        outbox.enqueue(A, leg("me", 0, completedAt = 30L))
        outbox.enqueue(B, leg("me", 0, completedAt = 10L))
        outbox.enqueue(A, leg("me", 1, completedAt = 20L))
        outbox.update(A, 1, "me", ResultPostState.LocalOnly, 4)

        assertEquals(listOf(B to 0, A to 0), outbox.observePending().first().map { it.code to it.result.legIndex })

        outbox.update(B, 0, "me", ResultPostState.Posted, 1)

        assertEquals(listOf(A to 0), outbox.observePending().first().map { it.code to it.result.legIndex })
    }

    @Test
    fun `observeAll lists every match's rows, newest completion first, and re-emits on enqueue and update`() =
        runTest {
            val seen = Channel<List<Pair<ShareCode, ResultPostState>>>(Channel.UNLIMITED)
            val collector = launch(Dispatchers.Default) {
                outbox.observeAll().collect { rows -> seen.send(rows.map { it.code to it.state }) }
            }
            suspend fun awaitRows(expected: List<Pair<ShareCode, ResultPostState>>) =
                withContext(Dispatchers.Default) {
                    withTimeout(5_000L) { while (seen.receive() != expected) Unit }
                }

            awaitRows(emptyList())
            outbox.enqueue(A, leg("me", 0, completedAt = 10L))
            awaitRows(listOf(A to ResultPostState.Pending))
            outbox.enqueue(B, leg("me", 0, completedAt = 20L))
            awaitRows(listOf(B to ResultPostState.Pending, A to ResultPostState.Pending))
            outbox.update(A, 0, "me", ResultPostState.Posted, 1)
            awaitRows(listOf(B to ResultPostState.Pending, A to ResultPostState.Posted))
            collector.cancel()
        }

    private fun leg(
        uid: String,
        legIndex: Int,
        completedAt: Long,
        timeMs: Long = 4_000L,
        gaveUp: Boolean = false,
    ) = LegResult(uid, legIndex, timeMs, hints = 1, completedAtMs = completedAt, gaveUp = gaveUp)

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
        private val C = requireNotNull(ShareCode.parse("HJK789"))

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun adapters(): List<Array<Any>> = listOf(arrayOf(ROOM), arrayOf("fake"))
    }
}
