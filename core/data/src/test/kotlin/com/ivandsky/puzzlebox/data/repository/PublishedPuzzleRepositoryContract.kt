package com.ivandsky.puzzlebox.data.repository

import app.cash.turbine.test
import com.ivandsky.puzzlebox.data.model.PublishedPuzzle
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val REEMIT_WINDOW_MS = 300L

/**
 * Behavior every [PublishedPuzzleRepository] adapter must pass (S15). Subclasses supply a fresh,
 * empty [repository]; [PublishedPuzzleRepositoryContractTest] runs it on Room and on the
 * `:core:testing` fake.
 */
abstract class PublishedPuzzleRepositoryContract {

    protected abstract val repository: PublishedPuzzleRepository

    protected fun code(raw: String): ShareCode = requireNotNull(ShareCode.parse(raw))

    protected fun published(
        raw: String,
        createdAtMs: Long = 1_000L,
        unpublished: Boolean = false,
        solvesCount: Int? = null,
        solvesCountAt: Instant? = null,
    ) = PublishedPuzzle(
        code = code(raw),
        gameId = GameId("queens"),
        variant = VariantId("queens/n8"),
        title = "Puzzle $raw",
        authorTimeMs = 42_000L,
        authorHints = 1,
        createdAt = Instant.ofEpochMilli(createdAtMs),
        unpublished = unpublished,
        solvesCount = solvesCount,
        solvesCountAt = solvesCountAt,
    )

    @Test
    fun `upsert then observe returns an equal row`() = runTest {
        val saved = published("ABC123", solvesCount = 7, solvesCountAt = Instant.ofEpochMilli(5_000L))

        repository.upsert(saved)

        assertEquals(saved, repository.observe(code("ABC123")).first())
        assertNull(repository.observe(code("ZZZ999")).first())
    }

    @Test
    fun `upsert of an existing code replaces it`() = runTest {
        repository.upsert(published("ABC123"))
        repository.upsert(published("ABC123").copy(title = "Renamed"))

        assertEquals(listOf("Renamed"), repository.observeAll().first().map { it.title })
    }

    @Test
    fun `observeAll orders newest createdAt first`() = runTest {
        repository.upsert(published("AAAAAA", createdAtMs = 1_000L))
        repository.upsert(published("CCCCCC", createdAtMs = 3_000L))
        repository.upsert(published("BBBBBB", createdAtMs = 2_000L))

        assertEquals(
            listOf(code("CCCCCC"), code("BBBBBB"), code("AAAAAA")),
            repository.observeAll().first().map { it.code },
        )
    }

    @Test
    fun `a live observer re-emits on its row's change, and not for other rows`() = runTest {
        repository.observe(code("ABC123")).test {
            assertNull(awaitItem())

            repository.upsert(published("ABC123"))
            assertEquals(published("ABC123"), awaitItem())

            repository.upsert(published("XYZ789"))
            // Room re-queries on its own threads: give a stray re-emission real time to land.
            withContext(Dispatchers.Default) { delay(REEMIT_WINDOW_MS) }
            expectNoEvents()

            repository.updateSolves(code("ABC123"), count = 3, at = Instant.ofEpochMilli(9_000L))
            assertEquals(published("ABC123", solvesCount = 3, solvesCountAt = Instant.ofEpochMilli(9_000L)), awaitItem())

            repository.markUnpublished(code("ABC123"))
            assertEquals(true, awaitItem()?.unpublished)
        }
    }

    @Test
    fun `markUnpublished flips only that row`() = runTest {
        repository.upsert(published("AAAAAA", createdAtMs = 1_000L))
        repository.upsert(published("BBBBBB", createdAtMs = 2_000L))

        repository.markUnpublished(code("AAAAAA"))

        assertEquals(
            mapOf(code("BBBBBB") to false, code("AAAAAA") to true),
            repository.observeAll().first().associate { it.code to it.unpublished },
        )
    }

    @Test
    fun `updateSolves stores the count and its instant on that row only`() = runTest {
        repository.upsert(published("AAAAAA", createdAtMs = 1_000L))
        repository.upsert(published("BBBBBB", createdAtMs = 2_000L))

        repository.updateSolves(code("AAAAAA"), count = 12, at = Instant.ofEpochMilli(8_000L))

        assertEquals(
            published("AAAAAA", solvesCount = 12, solvesCountAt = Instant.ofEpochMilli(8_000L)),
            repository.observe(code("AAAAAA")).first(),
        )
        assertEquals(published("BBBBBB", createdAtMs = 2_000L), repository.observe(code("BBBBBB")).first())
    }

    @Test
    fun `markUnpublished and updateSolves on an unknown code store nothing`() = runTest {
        repository.markUnpublished(code("ABC123"))
        repository.updateSolves(code("ABC123"), count = 1, at = Instant.ofEpochMilli(1_000L))

        assertNull(repository.observe(code("ABC123")).first())
        assertEquals(emptyList<PublishedPuzzle>(), repository.observeAll().first())
    }

    @Test
    fun `instants are kept to the millisecond`() = runTest {
        val subMillis = Instant.ofEpochSecond(1_700_000_000L, 123_456_789L)
        val millis = Instant.ofEpochSecond(1_700_000_000L, 123_000_000L)

        repository.upsert(published("ABC123").copy(createdAt = subMillis))
        repository.updateSolves(code("ABC123"), count = 2, at = subMillis)

        val loaded = repository.observe(code("ABC123")).first()
        assertEquals(millis, loaded?.createdAt)
        assertEquals(millis, loaded?.solvesCountAt)
        assertEquals(listOf(millis), repository.observeAll().first().map { it.createdAt })
    }
}
