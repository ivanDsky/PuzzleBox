package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.match.OutboxResult
import com.ivandsky.puzzlebox.data.match.ResultPostState
import com.ivandsky.puzzlebox.domain.FinishLeg
import com.ivandsky.puzzlebox.domain.FinishResult
import com.ivandsky.puzzlebox.domain.LegFinish
import com.ivandsky.puzzlebox.domain.LoadPastDay
import com.ivandsky.puzzlebox.domain.LoadSharedPuzzle
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.SessionBlob
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.api.CustomGameKey
import com.ivandsky.puzzlebox.play.api.GameKey
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How a duel leg ends in the store: a solve queues the leg's result first (the outbox row is
 * "taken"), then writes its completion tagged with the match; a give up queues a loss and drops
 * the leg's save. Both run on the store's own scope, so a release or a kill right after keeps them.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaySessionStoreLegFinishTest {

    private val alpha = TestGames.alpha
    private val today = LocalDate.parse("2026-11-04")
    private val dayKey = PuzzleKey.Daily(today, 7)
    private val legKey = MatchLegKey("M8T3VQ", 1, alpha.value)
    private val code = ShareCode.parse(legKey.code)!!
    private val legSlot = PlaySlot.Leg(alpha, code.value, legKey.legIndex)
    private val me = Identity(uid = "me", displayName = "Me", photoUrl = null)

    private val fakes = FakeGames(listOf(alpha))
    private val daily = FakeDailyRepository()
    private val saved = FakeSavedPlayRepository()
    private val calendar = FakeGameCalendar(today)
    private val completions = FakeCompletionRepository()
    private val outbox = FakeMatchResultOutbox()
    private val identity = FakeIdentityService(me)
    private val finishLeg = FinishLeg(outbox, identity) { Instant.EPOCH }

    private fun TestScope.store(dispatcher: TestDispatcher = UnconfinedTestDispatcher(testScheduler)): PlaySessionStore {
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
            completions = completions,
            finishLeg = finishLeg,
            calendar = calendar,
            dispatchers = object : DispatcherProvider {
                override val main = dispatcher
                override val io = dispatcher
                override val default = dispatcher
            },
        )
    }

    @Test
    fun aLegSolveWritesOneRecordTaggedWithTheMatchAndQueuesOneResult() = runTest {
        seed(legSlot)
        val play = store().open(legKey)!!
        runCurrent()
        fakes[alpha].latest.win()
        runCurrent()

        assertEquals(code.value, records().single().matchCode)
        val row = rows().single()
        assertEquals(ResultPostState.Pending, row.state)
        assertSolveOf(play, row)
    }

    @Test
    fun aDailyLegSolveKeepsTheDailyIdentityOfTheRecord() = runTest {
        seed(legSlot)
        store().open(legKey)!!
        runCurrent()
        fakes[alpha].latest.win()
        runCurrent()

        val ref = records().single().puzzleRef
        assertEquals(alpha, ref.gameId)
        assertEquals(dayKey, ref.key)
    }

    @Test
    fun aSolveFollowedByReleaseStillQueuesTheResult() = runTest {
        seed(legSlot)
        val store = store(StandardTestDispatcher(testScheduler))
        val play = store.open(legKey)!!
        runCurrent()
        fakes[alpha].latest.win()
        store.retain(emptyList())
        advanceUntilIdle()

        assertNull(store.peek(legSlot))
        assertSolveOf(play, rows().single())
        assertEquals(code.value, records().single().matchCode)
    }

    @Test
    fun aRetriedRecordWriteDoesNotQueueASecondResult() = runTest {
        seed(legSlot)
        val play = store().open(legKey)!!
        runCurrent()
        completions.fail = true
        fakes[alpha].latest.win()
        runCurrent()
        assertTrue(play.record.value.writeFailed)

        completions.fail = false
        play.retryRecordWrite()
        runCurrent()

        assertFalse(play.record.value.writeFailed)
        assertSolveOf(play, rows().single())
        assertEquals(code.value, records().single().matchCode)
    }

    @Test
    fun aKillBetweenTheEnqueueAndTheCompletionStillReadsTheLegAsTaken() = runTest {
        seed(legSlot)
        val play = store().open(legKey)!!
        runCurrent()
        completions.fail = true
        fakes[alpha].latest.win()
        runCurrent()
        val solve = play.record.value.solve!!

        val fresh = store()
        runCurrent()

        assertTrue(records().isEmpty())
        assertSolveOf(play, rows().single())
        assertEquals(FinishResult.AlreadyRecorded, finishLeg(code, legKey.legIndex, LegFinish.Solved(solve.timeMs, solve.hints)))
        assertEquals(FinishResult.AlreadyRecorded, fresh.giveUp(legKey))
        assertSolveOf(play, rows().single())
    }

    @Test
    fun givingUpQueuesALossDeletesTheLegSaveAndWritesNoRecord() = runTest {
        seed(legSlot)
        val store = store()
        store.open(legKey)!!
        runCurrent()
        fakes[alpha].latest.move()

        assertEquals(FinishResult.Queued, store.giveUp(legKey))
        advanceUntilIdle()

        assertNull(saved.load(legSlot))
        assertTrue(rows().single().result.gaveUp)
        assertTrue(records().isEmpty())
        assertNull(store.peek(legSlot))
    }

    @Test
    fun givingUpWithNoLivePlayStillDeletesTheSavedLegRow() = runTest {
        seed(legSlot)
        val store = store()

        assertEquals(FinishResult.Queued, store.giveUp(legKey))
        advanceUntilIdle()

        assertNull(saved.load(legSlot))
        assertTrue(rows().single().result.gaveUp)
        assertTrue(records().isEmpty())
    }

    @Test
    fun givingUpTwiceQueuesOneLoss() = runTest {
        seed(legSlot)
        val store = store()

        assertEquals(FinishResult.Queued, store.giveUp(legKey))
        assertEquals(FinishResult.AlreadyRecorded, store.giveUp(legKey))
        advanceUntilIdle()

        assertTrue(rows().single().result.gaveUp)
    }

    @Test
    fun givingUpSignedOutKeepsThePlayAndItsSaveAndQueuesNothing() = runTest {
        seed(legSlot)
        val store = store()
        val play = store.open(legKey)!!
        runCurrent()
        identity.currentIdentity = null

        assertEquals(FinishResult.SignedOut, store.giveUp(legKey))
        advanceUntilIdle()

        assertSame(play, store.peek(legSlot))
        assertNotNull(saved.load(legSlot))
        assertTrue(outbox.observeAll().first().isEmpty())
        assertTrue(records().isEmpty())
    }

    @Test
    fun aDailyOrFriendPlayIsNeverWrappedOrTagged() = runTest {
        val dailySlot = PlaySlot.Daily(alpha, today)
        val friendSlot = PlaySlot.Custom(alpha, "FRND42")
        seed(dailySlot)
        seed(friendSlot)
        val store = store()

        store.open(GameKey(alpha.value, today.toString()))!!
        runCurrent()
        fakes[alpha].latest.win()
        store.open(CustomGameKey(alpha.value, friendSlot.puzzleId))!!
        runCurrent()
        fakes[alpha].latest.win()
        runCurrent()

        assertEquals(listOf(null, null), records().map { it.matchCode })
        assertTrue(outbox.observeAll().first().isEmpty())
    }

    /** A leg (or day, or friend's puzzle) with a saved board of [dayKey], so it opens from its row. */
    private suspend fun seed(slot: PlaySlot) {
        val session = FakeController.encode(0, false, TestGames.Ref(alpha, dayKey))
        saved.save(slot, SessionBlob.encode(alpha, session), 0L)
    }

    private suspend fun records(): List<CompletionRecord> = completions.observeRecords().first()

    private suspend fun rows(): List<OutboxResult> = outbox.observe(code).first()

    /** [row] is [play]'s win as the game reported it, queued for this leg and player. */
    private fun assertSolveOf(play: ActivePlay, row: OutboxResult) {
        val solve = play.record.value.solve!!
        assertEquals(legKey.legIndex, row.result.legIndex)
        assertEquals(me.uid, row.result.uid)
        assertEquals(solve.timeMs, row.result.timeMs)
        assertEquals(solve.hints, row.result.hints)
        assertFalse(row.result.gaveUp)
    }
}
