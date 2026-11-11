package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.model.PostState
import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.game.GameRegistry
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
import org.junit.Test

/**
 * From friends (spec A19) from local data only: each received puzzle's status by precedence
 * Needs update › Solved › Taken down › In progress › New, listed unsolved first, then solved,
 * each newest opened first.
 */
class ObserveFriendPuzzlesTest {

    private val alpha = TestGames.alpha
    private val received = FakeReceivedPuzzleRepository()
    private val saved = FakeSavedPlayRepository()
    private val completions = FakeCompletionRepository()
    private val observe = ObserveFriendPuzzles(received, saved, completions, GameRegistry(listOf(versioned(alpha))))

    private suspend fun statusOf(code: ShareCode): FriendStatus =
        observe().first().single { it.code == code }.status

    @Test
    fun `a puzzle opened and never played is New`() = runTest {
        received.upsert(puzzle(A))

        assertEquals(
            listOf(FriendPuzzleRow(A, alpha, "Crown A", "Bo", FriendStatus.New)),
            observe().first(),
        )
    }

    @Test
    fun `a saved play reads In progress at its elapsed time`() = runTest {
        received.upsert(puzzle(A))
        saved.save(PlaySlot.Custom(alpha, A.value), byteArrayOf(1), elapsedMs = 70_000)

        assertEquals(FriendStatus.InProgress(70_000), statusOf(A))
    }

    @Test
    fun `a daily or another code's save never marks a friend puzzle in progress`() = runTest {
        received.upsert(puzzle(A))
        saved.save(PlaySlot.Daily(alpha, LocalDate.parse("2026-11-04")), byteArrayOf(1), elapsedMs = 5_000)
        saved.save(PlaySlot.Custom(alpha, B.value), byteArrayOf(1), elapsedMs = 9_000)

        assertEquals(FriendStatus.New, statusOf(A))
    }

    @Test
    fun `an unpublished puzzle with a save reads Taken down`() = runTest {
        received.upsert(puzzle(A, unpublished = true))
        saved.save(PlaySlot.Custom(alpha, A.value), byteArrayOf(1), elapsedMs = 70_000)

        assertEquals(FriendStatus.TakenDown, statusOf(A))
    }

    @Test
    fun `an unpublished and solved puzzle reads Solved at its record time`() = runTest {
        received.upsert(puzzle(A, unpublished = true))
        completions.onCompleted(win(A, time = 125_000, isRecord = true))
        completions.onCompleted(win(A, time = 98_000, isRecord = false))
        saved.save(PlaySlot.Custom(alpha, A.value), byteArrayOf(1), elapsedMs = 4_000)

        assertEquals(FriendStatus.Solved(125_000), statusOf(A))
    }

    @Test
    fun `an unregistered game reads Needs update even when solved`() = runTest {
        val gone = GameId("gone")
        received.upsert(puzzle(A, game = gone))
        completions.onCompleted(win(A, game = gone, time = 60_000, isRecord = true))

        assertEquals(FriendStatus.NeedsUpdate, statusOf(A))
    }

    @Test
    fun `a board in a newer format than this build reads Needs update`() = runTest {
        received.upsert(puzzle(A, formatVersion = 2, unpublished = true))

        assertEquals(FriendStatus.NeedsUpdate, statusOf(A))
    }

    @Test
    fun `unsolved rows come first newest opened first, then solved rows newest opened first`() = runTest {
        received.upsert(puzzle(A, openedAt = 1))
        received.upsert(puzzle(B, openedAt = 2))
        received.upsert(puzzle(C, openedAt = 3))
        received.upsert(puzzle(D, openedAt = 4))
        received.upsert(puzzle(E, openedAt = 5, formatVersion = 2))
        completions.onCompleted(win(B, time = 1_000, isRecord = true))
        completions.onCompleted(win(D, time = 2_000, isRecord = true))
        saved.save(PlaySlot.Custom(alpha, A.value), byteArrayOf(1), elapsedMs = 3_000)

        assertEquals(listOf(E, C, A, D, B), observe().first().map { it.code })
    }

    @Test
    fun `no received puzzles is an empty list`() = runTest {
        assertEquals(emptyList<FriendPuzzleRow>(), observe().first())
    }

    private fun puzzle(
        code: ShareCode,
        game: GameId = alpha,
        openedAt: Long = 0,
        unpublished: Boolean = false,
        formatVersion: Int = 1,
    ) = ReceivedPuzzle(
        code = code,
        gameId = game,
        variant = TestGames.variant(game),
        sharedFormatVersion = formatVersion,
        puzzleBytes = byteArrayOf(TestGames.MAGIC),
        title = "Crown ${code.value.first()}",
        authorName = "Bo",
        note = "",
        authorTimeMs = 100_000,
        authorHints = 0,
        authorUid = "author",
        openedAt = Instant.ofEpochMilli(1_700_000_000_000 + openedAt),
        unpublished = unpublished,
        postState = PostState.Pending,
        postAttempts = 0,
        difficulty = null,
    )

    private fun win(code: ShareCode, game: GameId = alpha, time: Long, isRecord: Boolean) = CompletionRecord(
        puzzleRef = TestGames.Ref(game, PuzzleKey.Custom(code.value)),
        time = time,
        hints = 0,
        stats = RecordStats(1, "{}"),
        isRecord = isRecord,
    )

    /** [game]'s stand-in, whose boards decode up to format version 1. */
    private fun versioned(game: GameId): GamePlugin<Unit, TestGames.Session, TestGames.Model> {
        val base = TestGames.plugin(game)
        return GamePlugin(
            definition = base.definition,
            rules = base.rules,
            session = base.session,
            score = base.score,
            stateOf = base.stateOf,
            initialSession = { puzzle ->
                if (puzzle.formatVersion > 1) {
                    DecodeResult.Err(DecodeError.UnsupportedVersion(found = puzzle.formatVersion, maxKnown = 1))
                } else {
                    DecodeResult.Ok(TestGames.Session)
                }
            },
            refOf = base.refOf,
            playAgainSession = base.playAgainSession,
            rendererModel = base.rendererModel,
        )
    }

    private companion object {
        val A = checkNotNull(ShareCode.parse("AAAAAA"))
        val B = checkNotNull(ShareCode.parse("BBBBBB"))
        val C = checkNotNull(ShareCode.parse("CCCCCC"))
        val D = checkNotNull(ShareCode.parse("DDDDDD"))
        val E = checkNotNull(ShareCode.parse("EEEEEE"))
    }
}
