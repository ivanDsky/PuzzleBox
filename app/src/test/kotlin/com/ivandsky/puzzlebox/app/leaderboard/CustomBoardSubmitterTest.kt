package com.ivandsky.puzzlebox.app.leaderboard

import android.app.Application
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.SubmitResult
import com.ivandsky.puzzlebox.data.model.PostState
import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
import com.ivandsky.puzzlebox.data.repository.CustomBoardRepository
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.games.queens.QueensPuzzleRef
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeCustomBoardRepository
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeReceivedPuzzleRepository
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Posting matrix: a friend's first win posts once per puzzle; signed out, the author's own puzzle and
 * an unpublished one never post; rejections count attempts up to LocalOnly. Robolectric: deferred
 * posts log through android.util.Log.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CustomBoardSubmitterTest {

    private val code = ShareCode.parse("K7Q2MX")!!
    private val queens = GameId("queens")
    private val ann = Identity("u1", "Ann", null)

    private val identity = FakeIdentityService(initial = ann)
    private val received = FakeReceivedPuzzleRepository()
    private val completions = FakeCompletionRepository()
    private val fakeBoard = FakeCustomBoardRepository()
    private val board = CountingCustomBoardRepository(fakeBoard)
    private val scheduler = TestCoroutineScheduler()
    private var process: CoroutineScope? = null

    private fun puzzle(
        authorUid: String = "author",
        unpublished: Boolean = false,
        postState: PostState = PostState.Pending,
        postAttempts: Int = 0,
    ) = ReceivedPuzzle(
        code = code,
        gameId = queens,
        variant = VariantId("queens/n8"),
        sharedFormatVersion = 1,
        puzzleBytes = byteArrayOf(1, 2, 3),
        title = "Crown",
        authorName = "Bo",
        note = "",
        authorTimeMs = 102_000,
        authorHints = 0,
        authorUid = authorUid,
        openedAt = Instant.ofEpochMilli(1_700_000_000_000),
        unpublished = unpublished,
        postState = postState,
        postAttempts = postAttempts,
        difficulty = null,
    )

    private fun win(isRecord: Boolean = true) = CompletionRecord(
        puzzleRef = QueensPuzzleRef(PuzzleKey.Custom(code.value)),
        time = 95_000,
        hints = 1,
        stats = RecordStats(schemaVersion = 1, blob = "{}"),
        firstCompletedAt = Instant.ofEpochMilli(1_700_000_100_000),
        isRecord = isRecord,
    )

    private suspend fun given(puzzle: ReceivedPuzzle, vararg records: CompletionRecord) {
        received.upsert(puzzle)
        completions.setRecords(records.toList())
    }

    /** One app process: the previous one dies, a fresh submitter starts on a fresh scope, run until idle. */
    private fun appStart() {
        process?.cancel()
        val scope = CoroutineScope(StandardTestDispatcher(scheduler) + Job())
        process = scope
        CustomBoardSubmitter(identity, received, completions, board, scope).start()
        scheduler.advanceUntilIdle()
    }

    @After
    fun stopProcess() {
        process?.cancel()
    }

    private val row get() = received.rows.getValue(code)

    @Test
    fun `a signed-in friend's win posts once and marks Posted`() = runTest {
        given(puzzle(), win())

        appStart()
        identity.currentIdentity = ann.copy(displayName = "Ann B")
        scheduler.advanceUntilIdle()
        appStart()

        assertEquals(1, board.submits)
        val entry = fakeBoard.entries.getValue(code).single()
        assertEquals("u1", entry.uid)
        assertEquals("custom/queens/K7Q2MX", entry.puzzleRef)
        assertEquals(95_000L, entry.timeMs)
        assertEquals(PostState.Posted, row.postState)
        assertEquals(0, row.postAttempts)
    }

    @Test
    fun `signed out the win stays Pending and posts after sign-in`() = runTest {
        identity.currentIdentity = null
        given(puzzle(), win())

        appStart()
        assertEquals(0, board.submits)
        assertEquals(PostState.Pending, row.postState)

        identity.currentIdentity = ann
        scheduler.advanceUntilIdle()

        assertEquals(1, board.submits)
        assertEquals(PostState.Posted, row.postState)
    }

    @Test
    fun `a win recorded after start posts`() = runTest {
        given(puzzle())

        appStart()
        assertEquals(0, board.submits)
        completions.onCompleted(win())
        scheduler.advanceUntilIdle()

        assertEquals(1, board.submits)
        assertEquals(PostState.Posted, row.postState)
    }

    @Test
    fun `the author's own win never posts`() = runTest {
        given(puzzle(authorUid = ann.uid), win())

        appStart()

        assertEquals(0, board.submits)
        assertEquals(PostState.Pending, row.postState)
        assertEquals(0, row.postAttempts)
    }

    @Test
    fun `an unpublished puzzle never posts`() = runTest {
        given(puzzle(unpublished = true), win())

        appStart()

        assertEquals(0, board.submits)
        assertEquals(PostState.Pending, row.postState)
    }

    @Test
    fun `a puzzle without a first win never posts`() = runTest {
        given(puzzle(), win(isRecord = false))

        appStart()

        assertEquals(0, board.submits)
        assertEquals(PostState.Pending, row.postState)
    }

    @Test
    fun `rows already Posted or LocalOnly never post`() = runTest {
        given(puzzle(postState = PostState.LocalOnly, postAttempts = CustomBoardSubmitter.MAX_ATTEMPTS), win())
        appStart()
        received.upsert(puzzle(postState = PostState.Posted))
        scheduler.advanceUntilIdle()

        assertEquals(0, board.submits)
    }

    @Test
    fun `an existing entry on the board marks Posted`() = runTest {
        fakeBoard.submit(code, entry(uid = ann.uid))
        given(puzzle(), win())

        appStart()

        assertEquals(1, board.submits)
        assertEquals(1, fakeBoard.entries.getValue(code).size)
        assertEquals(PostState.Posted, row.postState)
    }

    @Test
    fun `three rejections end in LocalOnly with no fourth attempt`() = runTest {
        fakeBoard.rejectSubmits = true
        given(puzzle(), win())

        appStart()
        assertEquals(1, board.submits) // one attempt per app start
        assertEquals(PostState.Pending, row.postState)
        assertEquals(1, row.postAttempts)

        appStart()
        assertEquals(PostState.Pending, row.postState)
        assertEquals(2, row.postAttempts)

        appStart()
        assertEquals(3, board.submits)
        assertEquals(PostState.LocalOnly, row.postState)
        assertEquals(CustomBoardSubmitter.MAX_ATTEMPTS, row.postAttempts)

        fakeBoard.rejectSubmits = false
        appStart()
        assertEquals(3, board.submits)
        assertEquals(PostState.LocalOnly, row.postState)
    }

    @Test
    fun `an unavailable board keeps the win Pending without counting an attempt`() = runTest {
        fakeBoard.online = false
        given(puzzle(), win())

        appStart()
        assertEquals(1, board.submits)
        assertEquals(PostState.Pending, row.postState)
        assertEquals(0, row.postAttempts)

        fakeBoard.online = true
        appStart()

        assertEquals(2, board.submits)
        assertEquals(PostState.Posted, row.postState)
        assertEquals(0, row.postAttempts)
    }

    @Test
    fun `an unavailable post retries on the next change`() = runTest {
        fakeBoard.online = false
        given(puzzle(), win())
        appStart()

        fakeBoard.online = true
        identity.currentIdentity = ann.copy(displayName = "Ann B")
        scheduler.advanceUntilIdle()

        assertEquals(2, board.submits)
        assertEquals(PostState.Posted, row.postState)
    }

    private fun entry(uid: String) = LeaderboardEntry(
        uid = uid,
        displayName = "Ann",
        puzzleRef = "custom/queens/K7Q2MX",
        timeMs = 80_000,
        hints = 0,
        completedAtMs = 1_600_000_000_000,
    )
}

/** Counts every submit call, so a test can prove no extra attempt was made. */
private class CountingCustomBoardRepository(
    private val board: FakeCustomBoardRepository,
) : CustomBoardRepository by board {
    var submits = 0
        private set

    override suspend fun submit(code: ShareCode, entry: LeaderboardEntry): SubmitResult {
        submits++
        return board.submit(code, entry)
    }
}
