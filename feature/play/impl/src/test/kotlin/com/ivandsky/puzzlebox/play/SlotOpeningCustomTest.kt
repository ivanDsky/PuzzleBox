package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.model.PostState
import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
import com.ivandsky.puzzlebox.data.repository.DailyToday
import com.ivandsky.puzzlebox.domain.LoadPastDay
import com.ivandsky.puzzlebox.game.SessionBlob
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.api.CustomGameKey
import com.ivandsky.puzzlebox.play.api.GameKey
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeReceivedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A friend's puzzle ([PlaySlot.Custom]) opens fresh from the puzzle stored when its code
 * opened, or resumes its own saved row; every friend puzzle has its own slot, and no friend
 * save stands in for a daily's or a daily's for a friend's (A13). A friend's live board,
 * reopened while it is still on a back stack (rotation, a tab's return), is the play as it
 * stands — a solve the board has not handed off yet is never reset under the player.
 */
class SlotOpeningCustomTest {

    private val alpha = TestGames.alpha
    private val today = LocalDate.parse("2026-11-04")
    private val todaySlot = PlaySlot.Daily(alpha, today)

    private val code = checkNotNull(ShareCode.parse("K7Q2MX"))
    private val other = checkNotNull(ShareCode.parse("P4R9WZ"))
    private val friendSlot = PlaySlot.Custom(alpha, code.value)
    private val otherSlot = PlaySlot.Custom(alpha, other.value)

    private val fakes = FakeGames(listOf(alpha))
    private val daily = FakeDailyRepository().apply { setToday(alpha, DailyToday.Fresh(TestGames.puzzle(alpha))) }
    private val saved = FakeSavedPlayRepository()
    private val received = FakeReceivedPuzzleRepository()
    private val calendar = FakeGameCalendar(today)
    private val lane = WriteLane()
    private val opening = SlotOpening(daily, LoadPastDay(daily, fakes.games), saved, calendar, fakes.games, lane, received)

    @Test
    fun `a friend puzzle with no save starts fresh from the puzzle stored when it opened`() = runTest {
        received.upsert(friendPuzzle(code, tag = 5))

        val origin = opening.open(friendSlot) as SlotOpening.Origin.Fetched

        assertEquals(alpha, origin.puzzle.gameId)
        assertEquals(TestGames.variant(alpha), origin.puzzle.variant)
        assertArrayEquals(TestGames.puzzle(alpha, tag = 5).payload, origin.puzzle.payload)
        assertNull("a friend's puzzle has no edition number", origin.puzzleNumber)
        assertNull(origin.banner)
        assertEquals("never today's fetch", 0, daily.todayCalls(alpha))
    }

    @Test
    fun `a friend puzzle's save resumes before the stored puzzle`() = runTest {
        received.upsert(friendPuzzle(code))
        val blob = SessionBlob.encode(alpha, byteArrayOf(4, 2))
        saved.save(friendSlot, blob, elapsedMs = 30_000L)

        val origin = opening.open(friendSlot) as SlotOpening.Origin.Restored

        assertArrayEquals(blob, origin.session)
        assertEquals(0, daily.todayCalls(alpha))
    }

    @Test
    fun `every friend puzzle has its own slot`() = runTest {
        received.upsert(friendPuzzle(code, tag = 1))
        received.upsert(friendPuzzle(other, tag = 2))
        val blob = SessionBlob.encode(alpha, byteArrayOf(1))
        saved.save(friendSlot, blob, elapsedMs = 30_000L)

        val fresh = opening.open(otherSlot) as SlotOpening.Origin.Fetched

        assertArrayEquals("the other code's own puzzle", TestGames.puzzle(alpha, tag = 2).payload, fresh.puzzle.payload)
        assertArrayEquals("the first code's save is kept", blob, saved.rows.getValue(friendSlot))
        assertArrayEquals(blob, (opening.open(friendSlot) as SlotOpening.Origin.Restored).session)
    }

    @Test
    fun `a friend save and today's save never stand in for each other`() = runTest {
        received.upsert(friendPuzzle(code))
        val friendBlob = SessionBlob.encode(alpha, byteArrayOf(1))
        saved.save(friendSlot, friendBlob, elapsedMs = 30_000L)

        // Today has no save of its own: it is fetched, the friend's row untouched.
        assertTrue(opening.open(todaySlot) is SlotOpening.Origin.Fetched)
        assertEquals(1, daily.todayCalls(alpha))
        assertArrayEquals(friendBlob, saved.rows.getValue(friendSlot))

        val todayBlob = SessionBlob.encode(alpha, byteArrayOf(2))
        saved.save(todaySlot, todayBlob, elapsedMs = 12_000L)

        assertArrayEquals(todayBlob, (opening.open(todaySlot) as SlotOpening.Origin.Restored).session)
        assertArrayEquals(friendBlob, (opening.open(friendSlot) as SlotOpening.Origin.Restored).session)
    }

    @Test
    fun `an undecodable friend save is dropped for a fresh start and today's save is kept`() = runTest {
        received.upsert(friendPuzzle(code))
        val todayBlob = SessionBlob.encode(alpha, byteArrayOf(2))
        saved.save(todaySlot, todayBlob, elapsedMs = 12_000L)
        saved.save(friendSlot, byteArrayOf(7, 7), elapsedMs = 30_000L)

        assertTrue(opening.open(friendSlot) is SlotOpening.Origin.Fetched)

        assertEquals(setOf(todaySlot), saved.rows.keys)
        assertArrayEquals(todayBlob, saved.rows.getValue(todaySlot))
    }

    @Test
    fun `a friend puzzle no longer stored here is unavailable without a retry`() = runTest {
        val origin = opening.open(friendSlot) as SlotOpening.Origin.Unavailable

        assertFalse(origin.retryable)
        assertEquals(0, daily.todayCalls(alpha))
    }

    @Test
    fun `a friend board reopened while it is still on a back stack is its play as it stands`() = runTest {
        received.upsert(friendPuzzle(code))
        val store = store()
        val key = CustomGameKey(alpha.value, code.value)

        val play = checkNotNull(store.open(key))
        assertEquals(PlayBoardStatus.Ready(banner = null), play.board.value.status)
        fakes[alpha].latest.move()
        fakes[alpha].latest.win()

        val reopened = store.open(key)

        assertSame(play, reopened)
        assertTrue("the solve waits for its hand-off", fakes[alpha].latest.isComplete)
        assertEquals("never reset in place", 0, fakes[alpha].latest.replays)
    }

    @Test
    fun `a solved daily board reopened is played again`() = runTest {
        val store = store()
        val key = GameKey(alpha.value, today.toString())
        checkNotNull(store.open(key))
        fakes[alpha].latest.move()
        fakes[alpha].latest.win()

        store.open(key)

        assertFalse(fakes[alpha].latest.isComplete)
        assertEquals(1, fakes[alpha].latest.replays)
    }

    private fun store(): PlaySessionStore {
        val dispatcher = UnconfinedTestDispatcher()
        return PlaySessionStore(
            games = fakes.games,
            ui = fakes.registry,
            opening = opening,
            writers = { SlotWriter(it, saved, lane, CoroutineScope(dispatcher)) },
            completions = FakeCompletionRepository(),
            calendar = calendar,
            dispatchers = object : DispatcherProvider {
                override val main = dispatcher
                override val io = dispatcher
                override val default = dispatcher
            },
        )
    }

    private fun friendPuzzle(code: ShareCode, tag: Byte = 0) = ReceivedPuzzle(
        code = code,
        gameId = alpha,
        variant = TestGames.variant(alpha),
        sharedFormatVersion = 1,
        puzzleBytes = TestGames.puzzle(alpha, tag).payload,
        title = "Castle",
        authorName = "Ivan",
        note = "",
        authorTimeMs = 102_000L,
        authorHints = 0,
        authorUid = "author",
        openedAt = Instant.parse("2026-11-04T09:00:00Z"),
        unpublished = false,
        postState = PostState.Pending,
        postAttempts = 0,
        difficulty = null,
    )
}
