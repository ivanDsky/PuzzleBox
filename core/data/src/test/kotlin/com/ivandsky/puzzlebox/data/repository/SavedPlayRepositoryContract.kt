package com.ivandsky.puzzlebox.data.repository

import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PlaySlot
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Behavior every [SavedPlayRepository] adapter must pass (A12). Subclasses supply a fresh,
 * empty [repository]; adapter-specific cases (eviction) live in the subclass.
 */
abstract class SavedPlayRepositoryContract {

    protected abstract val repository: SavedPlayRepository

    protected fun slot(gameId: String, day: Int) = PlaySlot(GameId(gameId), LocalDate.of(2026, 11, day))

    @Test
    fun `saved blob loads back`() = runTest {
        repository.save(slot("zip", 1), byteArrayOf(1, 2, 3), elapsedMs = 0L)

        assertArrayEquals(byteArrayOf(1, 2, 3), repository.load(slot("zip", 1)))
        assertNull(repository.load(slot("zip", 2)))
    }

    @Test
    fun `saving a slot again overwrites its blob`() = runTest {
        repository.save(slot("zip", 1), byteArrayOf(1), elapsedMs = 1_000L)
        repository.save(slot("zip", 1), byteArrayOf(2, 3), elapsedMs = 4_000L)

        assertArrayEquals(byteArrayOf(2, 3), repository.load(slot("zip", 1)))
        assertEquals(mapOf(slot("zip", 1) to 4_000L), repository.observeInProgress().first())
    }

    @Test
    fun `delete is idempotent`() = runTest {
        repository.save(slot("zip", 1), byteArrayOf(1), elapsedMs = 0L)

        repository.delete(slot("zip", 1))
        repository.delete(slot("zip", 1))
        repository.delete(slot("tango", 3))

        assertNull(repository.load(slot("zip", 1)))
        assertEquals(emptyMap<PlaySlot, Long>(), repository.observeInProgress().first())
    }

    @Test
    fun `the newest save is always kept`() = runTest {
        for (day in 1..20) {
            repository.save(slot("zip", day), byteArrayOf(day.toByte()), elapsedMs = 0L)
            assertArrayEquals(byteArrayOf(day.toByte()), repository.load(slot("zip", day)))
        }
        assertEquals(true, slot("zip", 20) in repository.observeInProgress().first())
    }

    @Test
    fun `observeInProgress tracks save and delete`() = runTest {
        val slots = repository.observeInProgress()
        assertEquals(emptyMap<PlaySlot, Long>(), slots.first())

        repository.save(slot("zip", 1), byteArrayOf(1), elapsedMs = 10L)
        repository.save(slot("tango", 1), byteArrayOf(2), elapsedMs = 20L)
        assertEquals(mapOf(slot("zip", 1) to 10L, slot("tango", 1) to 20L), slots.first())

        repository.delete(slot("zip", 1))
        assertEquals(mapOf(slot("tango", 1) to 20L), slots.first())
    }
}
