package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.data.linkedin.LinkState
import com.ivandsky.puzzlebox.data.linkedin.LinkedInDayData
import com.ivandsky.puzzlebox.data.linkedin.LinkedInOwnState
import com.ivandsky.puzzlebox.data.linkedin.LinkedInPostState
import com.ivandsky.puzzlebox.domain.linkedin.ObserveLinkedInDay
import com.ivandsky.puzzlebox.domain.linkedin.ObserveLinkedInDayView
import com.ivandsky.puzzlebox.domain.linkedin.SyncLinkedInDay
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.CompletionSource
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.play.domain.linkedin.ObserveLinkedInPost
import com.ivandsky.puzzlebox.play.domain.linkedin.SubmitLinkedInResult
import com.ivandsky.puzzlebox.resultui.LinkedInPostRowState
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.linkedin.FakeLinkedInDays
import com.ivandsky.puzzlebox.testing.linkedin.FakeLinkedInLink
import com.ivandsky.puzzlebox.testing.linkedin.FakeLinkedInPosts
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The Post to LinkedIn row on a result page opened cold (the widget, the Today tile): the door says Post from what the
 * phone holds, and the phone only learns that LinkedIn already has the day's result by asking. So a Post the door would
 * offer is held (the row stays in its slot, invisible and inert) until LinkedIn has answered for the day or the cap
 * passes, and a day LinkedIn already holds is never offered as Post.
 */
class ResultPostButtonHoldTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val game = TestGames.alpha
    private val day = LocalDate.parse("2026-10-31")
    private val posts = FakeLinkedInPosts()
    private val days = FakeLinkedInDays()
    private val dispatcher = main.testDispatcher

    /** The view model's scope, which the button's flows and a tap's post run in. */
    private val viewModel = CoroutineScope(dispatcher + Job())

    /** The screen: its collectors are what make the button observe and ask. */
    private val screen = CoroutineScope(dispatcher + Job())
    private val button = ResultPostButton(
        scope = viewModel,
        observePost = ObserveLinkedInPost(posts),
        submitPost = SubmitLinkedInResult(posts),
        dayView = ObserveLinkedInDayView(
            ObserveLinkedInDay(days, TestGames.registry(listOf(game))), SyncLinkedInDay(days, 500), FakeLinkedInLink(), days,
        ),
        game = game,
        day = day,
    )

    /** Every row the screen was shown, with whether it was held. */
    private val seen = mutableListOf<Pair<LinkedInPostRowState?, Boolean>>()

    @After
    fun leaveScreen() {
        screen.cancel()
        viewModel.cancel()
    }

    private fun showScreen() {
        screen.launch { combine(button.state, button.pending) { row, held -> row to held }.collect { seen += it } }
        dispatcher.scheduler.runCurrent()
    }

    private fun advance(ms: Long) {
        dispatcher.scheduler.advanceTimeBy(ms)
        dispatcher.scheduler.runCurrent()
    }

    private fun data(inFlight: Boolean = false, own: LinkedInOwnState = LinkedInOwnState.Unknown) = LinkedInDayData(
        link = LinkState.Connected("member-1"),
        delta = null,
        boardInWindow = true,
        inFlight = inFlight,
        lastSync = null,
        own = own,
        ghosts = emptyList(),
        boardFetchedAtMs = null,
    )

    private fun importedResult() = LinkedInOwnState.Imported(
        CompletionRecord(
            TestGames.Ref(game, PuzzleKey.Daily(day, 7)), time = 58_000, hints = 0, stats = RecordStats(1, "{}"),
            source = CompletionSource.LinkedIn,
        ),
    )

    /** A day whose Post the door offers, and whose own result LinkedIn has not been asked for. */
    private fun offeredAndUnasked() {
        posts.emit(game, day, LinkedInPostState.Ready)
        days.emit(game, day, data())
        days.syncGate = CompletableDeferred()
    }

    @Test
    fun `a Post the door offers is held while LinkedIn has not said whether it holds the day`() {
        offeredAndUnasked()

        showScreen()
        advance(ResultPostButton.HOLD_CAP_MS - 1)

        assertEquals(LinkedInPostRowState.Post to true, seen.last())
        assertEquals("one ask, with no wait for a scrub that cannot happen here", listOf(game to day), days.syncCalls)
    }

    @Test
    fun `a result LinkedIn already holds is never offered as Post`() {
        offeredAndUnasked()
        showScreen()

        // The sync imports LinkedIn's result: the day says so, then the door.
        days.emit(game, day, data(own = importedResult()))
        advance(100)
        posts.emit(game, day, LinkedInPostState.AlreadyOnLinkedIn)
        advance(100)

        assertFalse("Post was shown: $seen", seen.contains(LinkedInPostRowState.Post to false))
        assertEquals(LinkedInPostRowState.AlreadyOnLinkedIn to false, seen.last())
    }

    @Test
    fun `a day LinkedIn says was not played releases Post at once`() {
        offeredAndUnasked()
        showScreen()

        days.emit(game, day, data(own = LinkedInOwnState.NotPlayed))
        advance(0)

        assertEquals(LinkedInPostRowState.Post to false, seen.last())
    }

    @Test
    fun `a sync that ends without an answer releases Post, and a tap checks LinkedIn itself`() {
        offeredAndUnasked()
        showScreen()

        days.syncGate!!.complete(Unit)
        advance(0)

        assertEquals(LinkedInPostRowState.Post to false, seen.last())
    }

    @Test
    fun `a LinkedIn that never answers releases Post at the cap`() {
        offeredAndUnasked()
        showScreen()

        advance(ResultPostButton.HOLD_CAP_MS)

        assertEquals(LinkedInPostRowState.Post to false, seen.last())
    }

    @Test
    fun `a tap during the hold posts nothing`() {
        offeredAndUnasked()
        showScreen()

        button.tap()
        advance(0)
        assertTrue(posts.submitCalls.isEmpty())

        days.emit(game, day, data(own = LinkedInOwnState.NotPlayed))
        advance(0)
        button.tap()
        advance(0)
        assertEquals(listOf(game to day), posts.submitCalls)
    }

    @Test
    fun `a day with no Post to offer asks LinkedIn nothing`() {
        posts.emit(game, day, LinkedInPostState.Posted)

        showScreen()
        advance(ResultPostButton.HOLD_CAP_MS)

        assertTrue(days.syncCalls.isEmpty())
        assertEquals(LinkedInPostRowState.Posted to false, seen.last())
    }
}
