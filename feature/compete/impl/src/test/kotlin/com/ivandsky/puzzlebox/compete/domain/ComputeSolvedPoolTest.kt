package com.ivandsky.puzzlebox.compete.domain

import com.ivandsky.puzzlebox.data.model.PostState
import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import com.ivandsky.puzzlebox.testing.game.TestGames.beta
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeReceivedPuzzleRepository
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The solved pool: solved dailies of the chosen games, friends' puzzles only when asked, each puzzle once. */
class ComputeSolvedPoolTest {

    private val gamma = GameId("gamma")
    private val registry = TestGames.registry(listOf(alpha, beta))
    private val completions = FakeCompletionRepository()
    private val received = FakeReceivedPuzzleRepository()
    private val pool = ComputeSolvedPool(registry, completions, received)

    private val friendCode = ShareCode.parse("K7Q2MX")!!

    private fun record(game: GameId, key: PuzzleKey, isRecord: Boolean = true) = CompletionRecord(
        puzzleRef = TestGames.Ref(game, key),
        time = 30_000L,
        hints = 0,
        stats = RecordStats(schemaVersion = 1, blob = "{}"),
        firstCompletedAt = Instant.EPOCH,
        isRecord = isRecord,
    )

    private fun daily(day: Int) = PuzzleKey.Daily(LocalDate.of(2026, 11, day), puzzleNumber = day)

    private suspend fun friend(unpublished: Boolean = false) = received.upsert(
        ReceivedPuzzle(
            code = friendCode, gameId = alpha, variant = TestGames.variant(alpha), sharedFormatVersion = 1,
            puzzleBytes = byteArrayOf(TestGames.MAGIC), title = "t", authorName = "bo", note = "",
            authorTimeMs = 1_000, authorHints = 0, authorUid = "bo", openedAt = Instant.EPOCH,
            unpublished = unpublished, postState = PostState.Pending, postAttempts = 0, difficulty = null,
        ),
    )

    @Test
    fun `lists solved dailies of the chosen games only`() = runTest {
        completions.setRecords(listOf(record(alpha, daily(1)), record(beta, daily(2)), record(gamma, daily(3))))

        assertEquals(listOf(TestGames.Ref(alpha, daily(1))), pool(setOf(alpha), includeFriends = false))
        assertEquals(2, pool(setOf(alpha, beta, gamma), includeFriends = false).size)
    }

    @Test
    fun `friend puzzles join only with friends on`() = runTest {
        friend()
        val custom = PuzzleKey.Custom(friendCode.value)
        completions.setRecords(listOf(record(alpha, daily(1)), record(alpha, custom)))

        assertEquals(1, pool(setOf(alpha), includeFriends = false).size)
        assertTrue(TestGames.Ref(alpha, custom) in pool(setOf(alpha), includeFriends = true))
    }

    @Test
    fun `a friend puzzle the device no longer holds or that was unpublished is left out`() = runTest {
        completions.setRecords(listOf(record(alpha, PuzzleKey.Custom(friendCode.value))))
        assertTrue(pool(setOf(alpha), includeFriends = true).isEmpty())

        friend(unpublished = true)
        assertTrue(pool(setOf(alpha), includeFriends = true).isEmpty())
    }

    @Test
    fun `a daily without a source number is left out and a puzzle is listed once`() = runTest {
        completions.setRecords(
            listOf(
                record(alpha, PuzzleKey.Daily(LocalDate.of(2026, 11, 1), puzzleNumber = null)),
                record(alpha, daily(2)),
                record(alpha, daily(2), isRecord = false),
            ),
        )

        assertEquals(listOf(TestGames.Ref(alpha, daily(2))), pool(setOf(alpha), includeFriends = false))
    }
}
