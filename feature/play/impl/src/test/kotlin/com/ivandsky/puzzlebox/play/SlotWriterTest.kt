package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.data.repository.SavedPlayRepository
import com.ivandsky.puzzlebox.domain.GetReplaySession
import com.ivandsky.puzzlebox.game.SessionBlob
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import java.time.LocalDate
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One slot's coalesced saves on virtual time (A10): the trailing throttle writes the
 * latest within its window under continuous offers, a solve's discard leaves no row,
 * flush writes at once, and the shared lane orders a released writer's final save
 * before a reopened slot's read and its new writer (patterns-7).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SlotWriterTest {

    private val alpha = TestGames.alpha
    private val today = LocalDate.parse("2026-11-04")
    private val slot = PlaySlot(alpha, today)
    private val lane = WriteLane()

    private fun blob(n: Int) = SessionBlob.encode(alpha, byteArrayOf(n.toByte()))

    /** A save log over the fake; [latency] stands in for a suspending Room call. */
    private class RecordingSavedPlayRepository(
        private val now: () -> Long,
        private val latency: Duration = Duration.ZERO,
        private val disk: FakeSavedPlayRepository = FakeSavedPlayRepository(),
    ) : SavedPlayRepository by disk {
        val writes = mutableListOf<Pair<Long, ByteArray>>()

        override suspend fun save(slot: PlaySlot, blob: ByteArray, elapsedMs: Long) {
            delay(latency)
            writes += now() to blob
            disk.save(slot, blob, elapsedMs)
        }

        /** The stored rows, as last written. */
        val rows: Map<PlaySlot, ByteArray> get() = disk.rows
    }

    private fun TestScope.recording(latency: Duration = Duration.ZERO) = RecordingSavedPlayRepository({ currentTime }, latency)

    private fun TestScope.writer(saved: SavedPlayRepository) = SlotWriter(slot, saved, lane, backgroundScope)

    @Test
    fun `under continuous offers the latest is written within the window`() = runTest {
        val saved = recording()
        val writer = writer(saved)
        val offers = (0 until 10).map { n ->
            writer.offer(blob(n), 0L)
            (currentTime to n).also { advanceTimeBy(100) }
        }
        // The writer runs in backgroundScope, which advanceUntilIdle does not wait for.
        advanceTimeBy(300)
        runCurrent()

        // Every offer is covered within 300 ms by a write of it or a newer blob: a debounce
        // would write nothing until the offers stop.
        val written = saved.writes.map { (at, bytes) -> at to offers.single { blob(it.second).contentEquals(bytes) }.second }
        offers.forEach { (at, n) ->
            assertTrue("offer $n at $at; written=$written", written.any { (writtenAt, m) -> m >= n && writtenAt - at <= 300 })
        }
        assertTrue(saved.writes.size < offers.size)
        assertArrayEquals(blob(9), saved.rows[slot])
    }

    @Test
    fun `discard after offer leaves no row`() = runTest {
        val saved = recording()
        val writer = writer(saved)
        writer.offer(blob(1), 0L)
        advanceTimeBy(301)
        writer.offer(blob(2), 0L)

        writer.discard()
        advanceUntilIdle()

        assertTrue(saved.rows.isEmpty())
        assertEquals(1, saved.writes.size)
    }

    @Test
    fun `flush writes the pending save immediately`() = runTest {
        val saved = recording()
        val writer = writer(saved)
        writer.offer(blob(1), 0L)

        writer.flush()

        assertEquals(listOf(0L), saved.writes.map { it.first })
        assertArrayEquals(blob(1), saved.rows[slot])
        advanceUntilIdle()
        assertEquals(1, saved.writes.size)
    }

    @Test
    fun `the written row carries the latest offer's elapsed time`() = runTest {
        val saved = recording()
        val writer = writer(saved)
        writer.offer(blob(1), 1_000L)
        writer.offer(blob(2), 2_500L)

        writer.flush()

        assertEquals(mapOf(slot to 2_500L), saved.observeInProgress().first())
    }

    @Test
    fun `a release then reopen reads the released save`() = runTest {
        val saved = recording(latency = 50.milliseconds)
        val daily = FakeDailyRepository()
        val games = TestGames.registry(listOf(alpha))
        val opening = SlotOpening(daily, GetReplaySession(daily, games), saved, FakeGameCalendar(today), games, lane)
        val released = writer(saved)
        released.offer(blob(1), 0L)

        // Release: the final save is queued now; its slow write is still in flight.
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { released.flush() }
        val reopened = writer(saved)
        val origin = opening.open(slot)
        reopened.offer(blob(2), 0L)
        reopened.flush()
        runCurrent()

        assertArrayEquals(blob(1), (origin as SlotOpening.Origin.Restored).session)
        assertEquals(0, daily.todayCalls(alpha))
        assertEquals(listOf(1, 2), saved.writes.map { (_, bytes) -> SessionBlob.decode(bytes)!!.session.single().toInt() })
        assertArrayEquals(blob(2), saved.rows[slot])
    }
}
