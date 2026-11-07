package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.model.PostState
import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeReceivedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Removing a friend's puzzle (spec A19) deletes its received row and its saved play, and
 * nothing else: the completion record stays, so History is unchanged.
 */
class DeleteReceivedPuzzleTest {

    private val alpha = TestGames.alpha
    private val received = FakeReceivedPuzzleRepository()
    private val saved = FakeSavedPlayRepository()
    private val completions = FakeCompletionRepository()
    private val delete = DeleteReceivedPuzzle(received, saved)

    @Test
    fun `the row and its saved play are gone and the record stays`() = runTest {
        received.upsert(puzzle(A))
        saved.save(PlaySlot.Custom(alpha, A.value), byteArrayOf(1), elapsedMs = 70_000)
        completions.onCompleted(win(A))

        delete(A)

        assertNull(received.get(A))
        assertEquals(emptyMap<PlaySlot, ByteArray>(), saved.rows)
        assertEquals(listOf(win(A)), completions.observeCustom(alpha, A.value).first())
    }

    @Test
    fun `another code's row and play and a daily's play stay`() = runTest {
        val daily = PlaySlot.Daily(alpha, LocalDate.parse("2026-11-04"))
        val other = PlaySlot.Custom(alpha, B.value)
        received.upsert(puzzle(A))
        received.upsert(puzzle(B))
        saved.save(PlaySlot.Custom(alpha, A.value), byteArrayOf(1), elapsedMs = 1_000)
        saved.save(other, byteArrayOf(2), elapsedMs = 2_000)
        saved.save(daily, byteArrayOf(3), elapsedMs = 3_000)

        delete(A)

        assertEquals(setOf(B), received.rows.keys)
        assertEquals(setOf(other, daily), saved.rows.keys)
    }

    @Test
    fun `an unknown code changes nothing`() = runTest {
        received.upsert(puzzle(B))
        saved.save(PlaySlot.Custom(alpha, B.value), byteArrayOf(2), elapsedMs = 2_000)

        delete(A)

        assertEquals(setOf(B), received.rows.keys)
        assertEquals(setOf<PlaySlot>(PlaySlot.Custom(alpha, B.value)), saved.rows.keys)
    }

    private fun puzzle(code: ShareCode, game: GameId = alpha) = ReceivedPuzzle(
        code = code,
        gameId = game,
        variant = TestGames.variant(game),
        sharedFormatVersion = 1,
        puzzleBytes = byteArrayOf(TestGames.MAGIC),
        title = "Crown",
        authorName = "Bo",
        note = "",
        authorTimeMs = 100_000,
        authorHints = 0,
        authorUid = "author",
        openedAt = Instant.ofEpochMilli(1_700_000_000_000),
        unpublished = false,
        postState = PostState.Pending,
        postAttempts = 0,
        difficulty = null,
    )

    private fun win(code: ShareCode) = CompletionRecord(
        puzzleRef = TestGames.Ref(alpha, PuzzleKey.Custom(code.value)),
        time = 125_000,
        hints = 0,
        stats = RecordStats(1, "{}"),
        isRecord = true,
    )

    private companion object {
        val A = checkNotNull(ShareCode.parse("AAAAAA"))
        val B = checkNotNull(ShareCode.parse("BBBBBB"))
    }
}
