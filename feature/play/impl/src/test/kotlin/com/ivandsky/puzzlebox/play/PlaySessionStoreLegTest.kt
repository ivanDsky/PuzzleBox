package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.domain.FinishLeg
import com.ivandsky.puzzlebox.domain.LoadPastDay
import com.ivandsky.puzzlebox.domain.LoadSharedPuzzle
import com.ivandsky.puzzlebox.game.SessionBlob
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.play.api.MatchLegKey
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeMatchRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchResultOutbox
import com.ivandsky.puzzlebox.testing.repository.FakeReceivedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The store's duel-leg plays: a [MatchLegKey] opens its leg's slot, kept only while the key is
 * on a stack, and a live leg is handed back as it stands, never reset.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaySessionStoreLegTest {

    private val alpha = TestGames.alpha
    private val today = LocalDate.parse("2026-11-04")
    private val fakes = FakeGames(listOf(alpha))
    private val daily = FakeDailyRepository()
    private val saved = FakeSavedPlayRepository()
    private val calendar = FakeGameCalendar(today)

    private fun TestScope.store(): PlaySessionStore {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val lane = WriteLane()
        return PlaySessionStore(
            games = fakes.games,
            ui = fakes.registry,
            opening = SlotOpening(
                daily, LoadPastDay(daily, fakes.games), saved, calendar, fakes.games, lane, FakeReceivedPuzzleRepository(),
                FakeMatchRepository(),
                LoadSharedPuzzle(fakes.games, FakeSharedPuzzleRepository(), FakeReceivedPuzzleRepository(), { Budget.Unlimited }, { Instant.EPOCH }),
                testDispatchers(dispatcher),
            ),
            writers = { SlotWriter(it, saved, lane, CoroutineScope(dispatcher)) },
            completions = FakeCompletionRepository(),
            finishLeg = FinishLeg(FakeMatchResultOutbox(), FakeIdentityService()) { Instant.EPOCH },
            calendar = calendar,
            dispatchers = object : DispatcherProvider {
                override val main = dispatcher
                override val io = dispatcher
                override val default = dispatcher
            },
        )
    }

    @Test
    fun `a leg key opens its leg's play and keeps it only while the key is on a stack`() = runTest {
        val legKey = seededLeg()
        val store = store()
        val play = store.open(legKey)!!
        runCurrent()

        assertEquals(PlaySlot.Leg(alpha, legKey.code, legKey.legIndex), play.slot)
        assertEquals(PlayBoardStatus.Ready(banner = null), play.board.value.status)
        store.retain(listOf(legKey))
        assertSame(play, store.peek(play.slot))

        store.retain(emptyList())
        runCurrent()
        assertNull(store.peek(play.slot))
    }

    @Test
    fun `a live leg play is returned as it stands, never reset`() = runTest {
        val legKey = seededLeg()
        val store = store()
        val play = store.open(legKey)!!
        runCurrent()
        fakes[alpha].latest.move()
        fakes[alpha].latest.win()

        assertSame(play, store.open(legKey))
        assertTrue(fakes[alpha].latest.isComplete)
        assertEquals(0, fakes[alpha].latest.replays)
    }

    /** A leg with a saved board, so it opens without the match document. */
    private suspend fun seededLeg(): MatchLegKey {
        val legKey = MatchLegKey("M8T3VQ", 0, alpha.value)
        val session = FakeController.encode(0, false, TestGames.Ref(alpha, PuzzleKey.Daily(today, 7)))
        saved.save(PlaySlot.Leg(alpha, legKey.code, legKey.legIndex), SessionBlob.encode(alpha, session), 0L)
        return legKey
    }
}
