package com.ivandsky.puzzlebox.app.widget

import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CachedWidgetReaderTest {

    private val today = LocalDate.parse("2026-11-21")
    private val yesterday = today.minusDays(1)

    private fun reader(store: WidgetSnapshotStore, read: suspend () -> WidgetState? = { null }) =
        CachedWidgetReader(read = read, store = store, puzzleDay = { today })

    @Test
    fun `a first draw with nothing cached is the empty state`() = runTest {
        assertNull(reader(MemoryWidgetSnapshotStore()).cached())
    }

    @Test
    fun `today's cached picture draws as it was saved`() = runTest {
        val store = MemoryWidgetSnapshotStore(WidgetSnapshot(today, WidgetSamples.mixed))

        assertEquals(WidgetSamples.mixed, reader(store).cached())
    }

    @Test
    fun `an earlier day's picture draws as a new day, unsolved with no ranks and the streak as stored`() = runTest {
        val store = MemoryWidgetSnapshotStore(WidgetSnapshot(yesterday, WidgetSamples.default))

        val state = reader(store).cached()

        assertEquals(WidgetSamples.default.rings.map { it.game }, state?.rings?.map { it.game })
        assertEquals(WidgetSamples.default.rings.map { it.bestMs }, state?.rings?.map { it.bestMs })
        assertEquals(List(5) { TodayStatus.NotStarted }, state?.rings?.map { it.status })
        assertEquals(List(5) { WidgetRank.Missing }, state?.rings?.map { it.rank })
        assertEquals(List(5) { false }, state?.rings?.map { it.newBest })
        assertEquals(WidgetSamples.default.streak, state?.streak)
        assertEquals(WidgetUpNext.Play(GameId("zip"), "Zip"), state?.upNext)
        assertNull(state?.nextRollover)
    }

    @Test
    fun `an earlier day's picture with no games has nothing up next`() = runTest {
        val store = MemoryWidgetSnapshotStore(WidgetSnapshot(yesterday, WidgetSamples.default.copy(rings = emptyList())))

        assertEquals(WidgetUpNext.Unknown, reader(store).cached()?.upNext)
    }

    @Test
    fun `a fresh read is drawn and saved as today's picture`() = runTest {
        val store = MemoryWidgetSnapshotStore(WidgetSnapshot(yesterday, WidgetSamples.default))

        val state = reader(store) { WidgetSamples.mixed }.fresh()

        assertEquals(WidgetSamples.mixed, state)
        assertEquals(WidgetSnapshot(today, WidgetSamples.mixed), store.snapshot)
    }

    @Test
    fun `a failed read gives null and keeps the cached picture`() = runTest {
        val cached = WidgetSnapshot(today, WidgetSamples.default)
        val store = MemoryWidgetSnapshotStore(cached)

        assertNull(reader(store) { null }.fresh())
        assertEquals(cached, store.snapshot)
    }

    @Test
    fun `a failed rank read keeps today's cached rank`() = runTest {
        val store = MemoryWidgetSnapshotStore(WidgetSnapshot(today, WidgetSamples.default))

        val state = reader(store) { WidgetSamples.rankMissing }.fresh()

        assertEquals(WidgetSamples.default.rings.map { it.rank }, state?.rings?.map { it.rank })
        assertEquals(WidgetSamples.default, store.snapshot?.state)
    }

    @Test
    fun `a rank from an earlier day is never kept`() = runTest {
        val store = MemoryWidgetSnapshotStore(WidgetSnapshot(yesterday, WidgetSamples.default))

        val state = reader(store) { WidgetSamples.rankMissing }.fresh()

        assertEquals(WidgetSamples.rankMissing, state)
    }

    @Test
    fun `signing out drops the cached rank`() = runTest {
        val store = MemoryWidgetSnapshotStore(WidgetSnapshot(today, WidgetSamples.default))

        assertEquals(WidgetSamples.signedOut, reader(store) { WidgetSamples.signedOut }.fresh())
    }
}
