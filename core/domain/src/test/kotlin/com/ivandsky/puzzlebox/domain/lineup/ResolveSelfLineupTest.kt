package com.ivandsky.puzzlebox.domain.lineup

import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.model.PostState
import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
import com.ivandsky.puzzlebox.domain.ComputeSolvedPool
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
import kotlin.random.Random
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A self duel's lineup: exact rows reserved first, "Any" rows drawn from the solved pool, first failing row wins. */
class ResolveSelfLineupTest {

    private val completions = FakeCompletionRepository()
    private val received = FakeReceivedPuzzleRepository()
    private val pool = ComputeSolvedPool(TestGames.registry(listOf(alpha, beta)), completions, received)

    private fun daily(day: Int) = PuzzleKey.Daily(LocalDate.of(2026, 11, day), puzzleNumber = day)

    private fun dailyRef(game: GameId, day: Int) = LegRef.Daily(game, LocalDate.of(2026, 11, day), day)

    private fun record(game: GameId, key: PuzzleKey, isRecord: Boolean = true, time: Long = 30_000L) = CompletionRecord(
        puzzleRef = TestGames.Ref(game, key),
        time = time,
        hints = 0,
        stats = RecordStats(schemaVersion = 1, blob = "{}"),
        firstCompletedAt = Instant.ofEpochMilli(1_000),
        isRecord = isRecord,
    )

    private suspend fun friend(code: ShareCode, unpublished: Boolean = false) = received.upsert(
        ReceivedPuzzle(
            code = code, gameId = alpha, variant = TestGames.variant(alpha), sharedFormatVersion = 1,
            puzzleBytes = byteArrayOf(TestGames.MAGIC), title = "t", authorName = "bo", note = "",
            authorTimeMs = 1_000, authorHints = 0, authorUid = "bo", openedAt = Instant.EPOCH,
            unpublished = unpublished, postState = PostState.Pending, postAttempts = 0, difficulty = null,
        ),
    )

    private fun exact(ref: LegRef) = ExactPuzzle(ref, authorUid = null, title = "t", author = null, source = PuzzleSource.PastDay)

    private fun SelfDraw.refs() = (this as SelfDraw.Drawn).picked.map { it.first.ref }

    private val friendCode = ShareCode.parse("K7Q2MX")!!
    private val friendKey = PuzzleKey.Custom(friendCode.value)

    @Test
    fun `any rows draw in lineup order, never the same puzzle twice`() = runTest {
        completions.setRecords(listOf(record(alpha, daily(1)), record(alpha, daily(2)), record(beta, daily(3))))
        repeat(8) { seed ->
            val refs = ResolveSelfLineup(pool, completions, received, Random(seed))(
                listOf(AnyGame(alpha), AnyGame(beta), AnyGame(alpha)), includeFriends = false,
            ).refs()
            assertEquals(listOf(alpha, beta, alpha), refs.map { it.gameId })
            assertNotEquals(refs[0], refs[2])
        }
    }

    @Test
    fun `an any row never draws a puzzle an exact row reserves`() = runTest {
        completions.setRecords(listOf(record(alpha, daily(1)), record(alpha, daily(2))))
        repeat(8) { seed ->
            val refs = ResolveSelfLineup(pool, completions, received, Random(seed))(
                listOf(AnyGame(alpha), exact(dailyRef(alpha, 1))), includeFriends = false,
            ).refs()
            assertEquals(listOf(dailyRef(alpha, 2), dailyRef(alpha, 1)), refs)
        }
    }

    @Test
    fun `the third row is undrawable when only one puzzle of its game is solved`() = runTest {
        completions.setRecords(listOf(record(alpha, daily(1)), record(beta, daily(3))))
        val draw = ResolveSelfLineup(pool, completions, received, Random(0))(
            listOf(AnyGame(alpha), AnyGame(beta), AnyGame(alpha)), includeFriends = false,
        )
        assertEquals(SelfDraw.Undrawable(3, alpha), draw)
    }

    @Test
    fun `an exact daily with no first-solve record is unavailable`() = runTest {
        completions.setRecords(listOf(record(alpha, daily(1)), record(alpha, daily(2), isRecord = false)))
        val lineup = listOf(exact(dailyRef(alpha, 1)), exact(dailyRef(alpha, 2)), exact(dailyRef(alpha, 3)))
        assertEquals(SelfDraw.Unavailable(2), ResolveSelfLineup(pool, completions, received, Random(0))(lineup, false))
    }

    @Test
    fun `an exact friend puzzle is unavailable when unpublished or not held, and accepted with the switch off`() = runTest {
        val custom = exact(LegRef.Custom(alpha, friendCode))
        completions.setRecords(listOf(record(alpha, friendKey)))
        val resolver = ResolveSelfLineup(pool, completions, received, Random(0))

        assertEquals(SelfDraw.Unavailable(1), resolver(listOf(custom), includeFriends = false))
        friend(friendCode, unpublished = true)
        assertEquals(SelfDraw.Unavailable(1), resolver(listOf(custom), includeFriends = false))
        friend(friendCode, unpublished = false)
        assertEquals(listOf<LegRef>(LegRef.Custom(alpha, friendCode)), resolver(listOf(custom), includeFriends = false).refs())
    }

    @Test
    fun `an any row skips friend puzzles with the switch off and may draw one with it on`() = runTest {
        friend(friendCode)
        completions.setRecords(listOf(record(alpha, daily(1)), record(alpha, friendKey)))
        val resolver = { seed: Int -> ResolveSelfLineup(pool, completions, received, Random(seed)) }

        repeat(8) { seed ->
            assertEquals(listOf<LegRef>(dailyRef(alpha, 1)), resolver(seed)(listOf(AnyGame(alpha)), false).refs())
        }
        val drawn = (0 until 16).map { resolver(it)(listOf(AnyGame(alpha)), true).refs().single() }
        assertTrue(drawn.any { it is LegRef.Custom })
    }

    @Test
    fun `with two failing rows the lower position is reported`() = runTest {
        completions.setRecords(listOf(record(alpha, daily(1))))
        val lineup = listOf(AnyGame(alpha), exact(dailyRef(alpha, 9)), AnyGame(beta))
        assertEquals(SelfDraw.Unavailable(2), ResolveSelfLineup(pool, completions, received, Random(0))(lineup, false))
        val swapped = listOf(AnyGame(alpha), AnyGame(beta), exact(dailyRef(alpha, 9)))
        assertEquals(SelfDraw.Undrawable(2, beta), ResolveSelfLineup(pool, completions, received, Random(0))(swapped, false))
    }
}
