package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.model.PostState
import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
import com.ivandsky.puzzlebox.data.repository.DailyToday
import com.ivandsky.puzzlebox.domain.DeleteReceivedPuzzle
import com.ivandsky.puzzlebox.domain.LoadPastDay
import com.ivandsky.puzzlebox.domain.LoadSharedPuzzle
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.api.CustomGameKey
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchRepository
import com.ivandsky.puzzlebox.testing.repository.FakeReceivedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A friend's puzzle removed while a game has it open: the delete that closes the Undo window
 * drops the live play first, so no save lands behind it; Undo keeps the live play and its
 * progress.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FriendRemovalPlayTest {

    private val alpha = TestGames.alpha
    private val today = LocalDate.parse("2026-11-04")
    private val slot = PlaySlot.Custom(alpha, A.value)
    private val fakes = FakeGames(listOf(alpha))
    private val received = FakeReceivedPuzzleRepository()
    private val saved = FakeSavedPlayRepository()

    private fun TestScope.store(): PlaySessionStore {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val daily = FakeDailyRepository().apply {
            setToday(alpha, DailyToday.Fresh(TestGames.puzzle(alpha)))
        }
        val calendar = FakeGameCalendar(today)
        val lane = WriteLane()
        return PlaySessionStore(
            games = fakes.games,
            ui = fakes.registry,
            opening = SlotOpening(
                daily, LoadPastDay(daily, fakes.games), saved, calendar,
                fakes.games, lane, received, FakeMatchRepository(),
                LoadSharedPuzzle(fakes.games, FakeSharedPuzzleRepository(), received, { Budget.Unlimited }, { Instant.EPOCH }),
                testDispatchers(dispatcher),
            ),
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

    private fun TestScope.removal(store: PlaySessionStore) =
        FriendRemoval(DeleteReceivedPuzzle(received, saved), this, store::discard)

    @Test
    fun `a removed puzzle's open play never saves behind the delete`() = runTest {
        received.upsert(puzzle(A))
        val store = store()
        assertNotNull(store.open(CustomGameKey(alpha.value, A.value)))
        runCurrent()
        val removal = removal(store)

        removal.remove(A, alpha)
        advanceTimeBy(FriendRemoval.UNDO_WINDOW_MS - 100)
        fakes[alpha].latest.move()
        advanceTimeBy(101)
        runCurrent()
        assertTrue("the delete ran with the save still pending", received.rows.isEmpty())
        store.flush()
        advanceTimeBy(SAVE_WINDOW_MS)
        runCurrent()

        assertTrue("no saved play for the removed slot", slot !in saved.rows)
        assertNull(store.peek(slot))
    }

    @Test
    fun `Undo inside the window keeps the live play and its progress`() = runTest {
        received.upsert(puzzle(A))
        val store = store()
        val play = checkNotNull(store.open(CustomGameKey(alpha.value, A.value)))
        runCurrent()
        fakes[alpha].latest.move()
        val removal = removal(store)

        removal.remove(A, alpha)
        advanceTimeBy(FriendRemoval.UNDO_WINDOW_MS - 1)
        removal.undo()
        advanceTimeBy(FriendRemoval.UNDO_WINDOW_MS)
        runCurrent()

        assertEquals(play, store.peek(slot))
        assertEquals(1, fakes[alpha].latest.moves)
        assertEquals(setOf(A), received.rows.keys)
        assertTrue("the live play still saves", slot in saved.rows)
    }

    private fun puzzle(code: ShareCode) = ReceivedPuzzle(
        code = code,
        gameId = alpha,
        variant = TestGames.variant(alpha),
        sharedFormatVersion = 1,
        puzzleBytes = TestGames.puzzle(alpha).payload,
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

    private companion object {
        const val SAVE_WINDOW_MS = 300L
        val A = checkNotNull(ShareCode.parse("K7Q2MX"))
    }
}
