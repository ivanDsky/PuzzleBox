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

    protected fun slot(gameId: String, day: Int) = PlaySlot.Daily(GameId(gameId), LocalDate.of(2026, 11, day))

    protected fun custom(puzzleId: String, gameId: String = "queens") = PlaySlot.Custom(GameId(gameId), puzzleId)

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

    @Test
    fun `custom slot round-trips and is observed`() = runTest {
        repository.save(custom("p1"), byteArrayOf(4, 5), elapsedMs = 30L)

        assertArrayEquals(byteArrayOf(4, 5), repository.load(custom("p1")))
        assertNull(repository.load(custom("p2")))
        assertEquals(mapOf<PlaySlot, Long>(custom("p1") to 30L), repository.observeInProgress().first())

        repository.delete(custom("p1"))
        assertNull(repository.load(custom("p1")))
        assertEquals(emptyMap<PlaySlot, Long>(), repository.observeInProgress().first())
    }

    @Test
    fun `ninth custom save prunes the oldest custom slot`() = runTest {
        for (n in 1..9) repository.save(custom("p$n", gameId = if (n % 2 == 0) "zip" else "queens"), byteArrayOf(n.toByte()), 0L)

        assertNull(repository.load(custom("p1", gameId = "queens")))
        assertEquals(
            (2..9).map { custom("p$it", gameId = if (it % 2 == 0) "zip" else "queens") }.toSet(),
            repository.observeInProgress().first().keys,
        )
    }

    @Test
    fun `custom saves never evict a daily`() = runTest {
        repository.save(slot("zip", 1), byteArrayOf(7), elapsedMs = 0L)
        for (n in 1..9) repository.save(custom("p$n", gameId = "zip"), byteArrayOf(n.toByte()), 0L)

        assertArrayEquals(byteArrayOf(7), repository.load(slot("zip", 1)))
        assertEquals(
            (2..9).map { custom("p$it", gameId = "zip") }.toSet() + slot("zip", 1),
            repository.observeInProgress().first().keys,
        )
    }
}
