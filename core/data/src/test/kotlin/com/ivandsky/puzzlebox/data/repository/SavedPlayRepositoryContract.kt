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

    protected fun leg(matchCode: String, legIndex: Int, gameId: String = "zip") = PlaySlot.Leg(GameId(gameId), matchCode, legIndex)

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

    @Test
    fun `leg slot round-trips and is observed`() = runTest {
        repository.save(leg("m1", 0), byteArrayOf(4, 5), elapsedMs = 30L)

        assertArrayEquals(byteArrayOf(4, 5), repository.load(leg("m1", 0)))
        assertNull(repository.load(leg("m1", 1)))
        assertEquals(mapOf<PlaySlot, Long>(leg("m1", 0) to 30L), repository.observeInProgress().first())

        repository.save(leg("m1", 0), byteArrayOf(6), elapsedMs = 45L)
        assertArrayEquals(byteArrayOf(6), repository.load(leg("m1", 0)))
        assertEquals(mapOf<PlaySlot, Long>(leg("m1", 0) to 45L), repository.observeInProgress().first())
    }

    @Test
    fun `ninth leg save prunes the oldest leg`() = runTest {
        val legs = (0..8).map { leg("m${it / 3}", it % 3, gameId = if (it % 2 == 0) "zip" else "queens") }
        legs.forEachIndexed { n, slot -> repository.save(slot, byteArrayOf(n.toByte()), 0L) }

        assertNull(repository.load(legs.first()))
        assertArrayEquals(byteArrayOf(8), repository.load(legs.last()))
        assertEquals(legs.drop(1).toSet(), repository.observeInProgress().first().keys)
    }

    @Test
    fun `leg saves never evict a daily or friend save`() = runTest {
        repository.save(slot("zip", 1), byteArrayOf(7), elapsedMs = 0L)
        repository.save(custom("p1", gameId = "zip"), byteArrayOf(8), elapsedMs = 0L)
        val legs = (0..8).map { leg("m$it", 0) }
        legs.forEach { repository.save(it, byteArrayOf(1), 0L) }

        assertArrayEquals(byteArrayOf(7), repository.load(slot("zip", 1)))
        assertArrayEquals(byteArrayOf(8), repository.load(custom("p1", gameId = "zip")))
        assertEquals(
            legs.drop(1).toSet() + slot("zip", 1) + custom("p1", gameId = "zip"),
            repository.observeInProgress().first().keys,
        )
    }

    @Test
    fun `daily and friend saves never evict a leg`() = runTest {
        repository.save(leg("m1", 0), byteArrayOf(7), elapsedMs = 0L)
        for (n in 1..9) {
            repository.save(slot("zip", n), byteArrayOf(n.toByte()), 0L)
            repository.save(custom("p$n", gameId = "zip"), byteArrayOf(n.toByte()), 0L)
        }

        assertArrayEquals(byteArrayOf(7), repository.load(leg("m1", 0)))
        assertEquals(true, leg("m1", 0) in repository.observeInProgress().first())
    }

    @Test
    fun `a leg is keyed by match and index`() = runTest {
        repository.save(leg("m1", 0), byteArrayOf(1), elapsedMs = 10L)
        repository.save(leg("m2", 0), byteArrayOf(2), elapsedMs = 20L)
        repository.save(leg("m1", 1), byteArrayOf(3), elapsedMs = 30L)

        assertArrayEquals(byteArrayOf(1), repository.load(leg("m1", 0)))
        assertArrayEquals(byteArrayOf(2), repository.load(leg("m2", 0)))
        assertArrayEquals(byteArrayOf(3), repository.load(leg("m1", 1)))
        assertEquals(
            mapOf<PlaySlot, Long>(leg("m1", 0) to 10L, leg("m2", 0) to 20L, leg("m1", 1) to 30L),
            repository.observeInProgress().first(),
        )
    }

    @Test
    fun `deleting a leg leaves other legs`() = runTest {
        repository.save(leg("m1", 0), byteArrayOf(1), elapsedMs = 0L)
        repository.save(leg("m1", 1), byteArrayOf(2), elapsedMs = 0L)
        repository.save(leg("m2", 0), byteArrayOf(3), elapsedMs = 0L)

        repository.delete(leg("m1", 0))
        repository.delete(leg("m1", 0))

        assertNull(repository.load(leg("m1", 0)))
        assertArrayEquals(byteArrayOf(2), repository.load(leg("m1", 1)))
        assertArrayEquals(byteArrayOf(3), repository.load(leg("m2", 0)))
        assertEquals(setOf(leg("m1", 1), leg("m2", 0)), repository.observeInProgress().first().keys)
    }
}
