package com.ivandsky.puzzlebox.data.repository

import app.cash.turbine.test
import com.ivandsky.puzzlebox.data.model.PostState
import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val REEMIT_WINDOW_MS = 300L

/**
 * Behavior every [ReceivedPuzzleRepository] adapter must pass (S15). Subclasses supply a fresh,
 * empty [repository]; [ReceivedPuzzleRepositoryContractTest] runs it on Room and on the
 * `:core:testing` fake, plus the Room-only cases for rows written by another app version.
 */
abstract class ReceivedPuzzleRepositoryContract {

    protected abstract val repository: ReceivedPuzzleRepository

    protected fun code(raw: String): ShareCode = requireNotNull(ShareCode.parse(raw))

    /** Every byte value once, so a lossy blob mapping cannot pass. */
    protected fun allBytes(): ByteArray = ByteArray(256) { it.toByte() }

    protected fun received(
        raw: String,
        openedAtMs: Long = 1_000L,
        postState: PostState = PostState.Pending,
        postAttempts: Int = 0,
        difficulty: Difficulty.Level? = Difficulty.Level.Medium,
    ) = ReceivedPuzzle(
        code = code(raw),
        gameId = GameId("queens"),
        variant = VariantId("queens/n8"),
        sharedFormatVersion = 1,
        puzzleBytes = allBytes(),
        title = "Puzzle $raw",
        authorName = "Ann",
        note = "Good luck",
        authorTimeMs = 42_000L,
        authorHints = 1,
        authorUid = "uid-ann",
        openedAt = Instant.ofEpochMilli(openedAtMs),
        unpublished = false,
        postState = postState,
        postAttempts = postAttempts,
        difficulty = difficulty,
    )

    @Test
    fun `upsert then get and observe return an equal row`() = runTest {
        val saved = received("ABC123", postState = PostState.Posted, postAttempts = 2)

        repository.upsert(saved)

        assertEquals(saved, repository.get(code("ABC123")))
        assertEquals(saved, repository.observe(code("ABC123")).first())
        assertNull(repository.get(code("ZZZ999")))
        assertNull(repository.observe(code("ZZZ999")).first())
    }

    @Test
    fun `puzzleBytes round-trip byte for byte`() = runTest {
        val bytes = allBytes()
        repository.upsert(received("ABC123").copy(puzzleBytes = bytes))
        bytes.fill(0)

        assertArrayEquals(allBytes(), repository.get(code("ABC123"))?.puzzleBytes)
        assertArrayEquals(allBytes(), repository.observe(code("ABC123")).first()?.puzzleBytes)
        assertArrayEquals(allBytes(), repository.observeAll().first().single().puzzleBytes)
    }

    @Test
    fun `every difficulty level and none round-trip`() = runTest {
        val levels = Difficulty.Level.entries + null
        levels.forEachIndexed { i, level -> repository.upsert(received("AAAAA$i", difficulty = level)) }

        assertEquals(levels, levels.indices.map { repository.get(code("AAAAA$it"))?.difficulty })
    }

    @Test
    fun `upsert of an existing code replaces it`() = runTest {
        repository.upsert(received("ABC123"))
        repository.upsert(received("ABC123").copy(title = "Renamed"))

        assertEquals(listOf("Renamed"), repository.observeAll().first().map { it.title })
    }

    @Test
    fun `observeAll orders newest openedAt first`() = runTest {
        repository.upsert(received("AAAAAA", openedAtMs = 1_000L))
        repository.upsert(received("CCCCCC", openedAtMs = 3_000L))
        repository.upsert(received("BBBBBB", openedAtMs = 2_000L))

        assertEquals(
            listOf(code("CCCCCC"), code("BBBBBB"), code("AAAAAA")),
            repository.observeAll().first().map { it.code },
        )
    }

    @Test
    fun `updatePost changes only that row's post state and attempts`() = runTest {
        repository.upsert(received("AAAAAA", openedAtMs = 1_000L))
        repository.upsert(received("BBBBBB", openedAtMs = 2_000L))

        repository.updatePost(code("AAAAAA"), PostState.LocalOnly, attempts = 3)

        assertEquals(
            received("AAAAAA", openedAtMs = 1_000L, postState = PostState.LocalOnly, postAttempts = 3),
            repository.get(code("AAAAAA")),
        )
        assertEquals(received("BBBBBB", openedAtMs = 2_000L), repository.get(code("BBBBBB")))
    }

    @Test
    fun `markUnpublished changes only that row's unpublished flag`() = runTest {
        repository.upsert(received("AAAAAA", openedAtMs = 1_000L, postState = PostState.Posted, postAttempts = 1))
        repository.upsert(received("BBBBBB", openedAtMs = 2_000L))

        repository.markUnpublished(code("AAAAAA"))

        assertEquals(
            received("AAAAAA", openedAtMs = 1_000L, postState = PostState.Posted, postAttempts = 1).copy(unpublished = true),
            repository.get(code("AAAAAA")),
        )
        assertEquals(received("BBBBBB", openedAtMs = 2_000L), repository.get(code("BBBBBB")))
    }

    @Test
    fun `delete removes only that row and is idempotent`() = runTest {
        repository.upsert(received("AAAAAA", openedAtMs = 1_000L))
        repository.upsert(received("BBBBBB", openedAtMs = 2_000L))

        repository.delete(code("AAAAAA"))
        repository.delete(code("AAAAAA"))
        repository.delete(code("ZZZ999"))

        assertNull(repository.get(code("AAAAAA")))
        assertNull(repository.observe(code("AAAAAA")).first())
        assertEquals(listOf(code("BBBBBB")), repository.observeAll().first().map { it.code })
    }

    @Test
    fun `updatePost and markUnpublished on an unknown code store nothing`() = runTest {
        repository.updatePost(code("ABC123"), PostState.Posted, attempts = 1)
        repository.markUnpublished(code("ABC123"))

        assertNull(repository.get(code("ABC123")))
        assertEquals(emptyList<ReceivedPuzzle>(), repository.observeAll().first())
    }

    @Test
    fun `a live observer re-emits on its row's change, and not for other rows`() = runTest {
        repository.observe(code("ABC123")).test {
            assertNull(awaitItem())

            repository.upsert(received("ABC123"))
            assertEquals(received("ABC123"), awaitItem())

            repository.upsert(received("XYZ789"))
            // Room re-queries on its own threads: give a stray re-emission real time to land.
            withContext(Dispatchers.Default) { delay(REEMIT_WINDOW_MS) }
            expectNoEvents()

            repository.updatePost(code("ABC123"), PostState.Posted, attempts = 1)
            assertEquals(received("ABC123", postState = PostState.Posted, postAttempts = 1), awaitItem())

            repository.markUnpublished(code("ABC123"))
            assertEquals(true, awaitItem()?.unpublished)

            repository.delete(code("ABC123"))
            assertNull(awaitItem())
        }
    }

    @Test
    fun `openedAt is kept to the millisecond`() = runTest {
        val subMillis = Instant.ofEpochSecond(1_700_000_000L, 123_456_789L)
        val millis = Instant.ofEpochSecond(1_700_000_000L, 123_000_000L)

        repository.upsert(received("ABC123").copy(openedAt = subMillis))

        assertEquals(millis, repository.get(code("ABC123"))?.openedAt)
        assertEquals(listOf(millis), repository.observeAll().first().map { it.openedAt })
    }
}
