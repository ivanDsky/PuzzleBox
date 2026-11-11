package com.ivandsky.puzzlebox.domain.linkedin

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.ivandsky.puzzlebox.data.linkedin.LinkState
import com.ivandsky.puzzlebox.data.linkedin.LinkedInDayData
import com.ivandsky.puzzlebox.data.linkedin.LinkedInGhost
import com.ivandsky.puzzlebox.data.linkedin.LinkedInOwnState
import com.ivandsky.puzzlebox.data.linkedin.LinkedInSync
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.CompletionSource
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.game.linkedin.LinkedInRead.Rejected.Kind
import com.ivandsky.puzzlebox.game.linkedin.linkedInScore
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.linkedin.FakeLinkedInDays
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What the day card shows for each state of a day's LinkedIn data: the status line and the connections board. */
class ObserveLinkedInDayTest {

    private val zip = GameId("zip")
    private val day = LocalDate.of(2026, 11, 5)
    private val fetchedAtMs = 1_700_000_000_000

    private val registry = TestGames.registry(listOf(zip))
    private val days = FakeLinkedInDays()
    private val observeLinkedInDay = ObserveLinkedInDay(days, registry)

    /** A connected day inside the board window whose board was read and came back empty. */
    private fun data(
        link: LinkState = LinkState.Connected("me"),
        delta: Int? = 3,
        boardInWindow: Boolean = true,
        inFlight: Boolean = false,
        lastSync: LinkedInSync? = null,
        own: LinkedInOwnState = LinkedInOwnState.Unknown,
        ghosts: List<LinkedInGhost> = emptyList(),
        boardFetchedAtMs: Long? = fetchedAtMs,
    ) = LinkedInDayData(link, delta, boardInWindow, inFlight, lastSync, own, ghosts, boardFetchedAtMs)

    private fun ghost(i: Int, timeMs: Long?, hintFree: Boolean = true) =
        LinkedInGhost("member-$i", "Member $i", timeMs, hintFree, flawless = false)

    private fun imported(timeMs: Long, hints: Int = 0) = LinkedInOwnState.Imported(
        CompletionRecord(
            puzzleRef = TestGames.Ref(zip, PuzzleKey.Daily(day, 235)),
            time = timeMs,
            hints = hints,
            stats = RecordStats(schemaVersion = 1, blob = "{}"),
            source = CompletionSource.LinkedIn,
        ),
    )

    /** Runs [block] against the observed day, past the unlinked view every day starts with. */
    private fun observing(block: suspend ReceiveTurbine<LinkedInDayView>.() -> Unit) = runTest {
        observeLinkedInDay(zip, day).test {
            assertEquals(LinkedInDayView(LinkedInDayStatus.NotConnected, LinkedInBoardView.Hidden), awaitItem())
            block()
            cancelAndIgnoreRemainingEvents()
        }
    }

    private suspend fun ReceiveTurbine<LinkedInDayView>.viewOf(data: LinkedInDayData): LinkedInDayView {
        days.emit(zip, day, data)
        return awaitItem()
    }

    private suspend fun ReceiveTurbine<LinkedInDayView>.shownOf(data: LinkedInDayData): LinkedInBoardView.Shown =
        viewOf(data).board as LinkedInBoardView.Shown

    @Test
    fun `not connected shows NotConnected and hides the board`() = observing {
        val view = viewOf(
            data(link = LinkState.NotConnected, own = imported(60_000), ghosts = listOf(ghost(1, 50_000))),
        )

        assertEquals(LinkedInDayView(LinkedInDayStatus.NotConnected, LinkedInBoardView.Hidden), view)
    }

    @Test
    fun `a paused link shows Paused(kind) over an imported result`() = observing {
        for (kind in Kind.entries) {
            val view = viewOf(
                data(link = LinkState.Paused("me", kind), own = imported(60_000), ghosts = listOf(ghost(1, 50_000))),
            )

            val expected = if (kind == Kind.SessionExpired) LinkedInDayStatus.SessionExpired else LinkedInDayStatus.Paused(kind)
            assertEquals(expected, view.status)
            // What was read before the pause is still on the phone, so it stays on screen.
            assertEquals(
                LinkedInBoardView.Shown(listOf(ghost(1, 50_000)), yourRank = 2, total = 2, updatedAtMs = fetchedAtMs, listGhosts = true),
                view.board,
            )
        }
    }

    @Test
    fun `an imported day shows Imported with the record time`() = observing {
        assertEquals(LinkedInDayStatus.Imported(61_000), viewOf(data(own = imported(61_000))).status)
        // The result is known, so neither a running sync nor how the last one ended has anything to add.
        assertEquals(
            LinkedInDayStatus.Imported(61_000),
            viewOf(data(own = imported(61_000), inFlight = true, lastSync = LinkedInSync.Offline)).status,
        )
    }

    @Test
    fun `a not-played day shows DidNotPlay`() = observing {
        assertEquals(LinkedInDayStatus.DidNotPlay, viewOf(data(own = LinkedInOwnState.NotPlayed)).status)
        assertEquals(
            LinkedInDayStatus.DidNotPlay,
            viewOf(data(own = LinkedInOwnState.NotPlayed, inFlight = true, lastSync = LinkedInSync.OverBudget)).status,
        )
    }

    @Test
    fun `an unknown day shows Loading only while a sync is in flight`() = observing {
        assertEquals(LinkedInDayStatus.Loading, viewOf(data(inFlight = true)).status)
        assertEquals(LinkedInDayStatus.Loading, viewOf(data(inFlight = true, lastSync = LinkedInSync.Offline)).status)

        assertEquals(LinkedInDayStatus.Offline, viewOf(data(lastSync = LinkedInSync.Offline)).status)
        assertEquals(LinkedInDayStatus.OverBudget, viewOf(data(lastSync = LinkedInSync.OverBudget)).status)

        assertEquals(LinkedInDayStatus.Idle, viewOf(data(lastSync = LinkedInSync.Done)).status)
        assertEquals(LinkedInDayStatus.Idle, viewOf(data(lastSync = null)).status)
    }

    @Test
    fun `3rd of 12`() = observing {
        // Board order is not rank order: the two faster ghosts sit in the middle.
        val slower = (1..9).map { ghost(it, 60_000L + it * 1_000) }
        val faster = listOf(ghost(10, 55_000), ghost(11, 50_000))
        val board = slower.take(4) + faster + slower.drop(4)

        val shown = shownOf(data(own = imported(60_000), ghosts = board))

        assertEquals(3, shown.yourRank)
        assertEquals(12, shown.total)
        assertEquals(listOf(ghost(11, 50_000), ghost(10, 55_000)) + slower, shown.ghosts)
        assertEquals(fetchedAtMs, shown.updatedAtMs)
    }

    @Test
    fun `a tie shares the rank`() = observing {
        val tied = shownOf(data(own = imported(60_000), ghosts = listOf(ghost(1, 60_000), ghost(2, 70_000))))
        assertEquals(1, tied.yourRank)
        assertEquals(3, tied.total)

        val behindOne = shownOf(data(own = imported(60_000), ghosts = listOf(ghost(1, 60_000), ghost(2, 59_000))))
        assertEquals(2, behindOne.yourRank)
        // Equal scores keep the order the board came in.
        val equals = listOf(ghost(3, 60_000), ghost(1, 60_000), ghost(2, 60_000))
        assertEquals(equals, shownOf(data(ghosts = equals)).ghosts)
    }

    @Test
    fun `hidden times go last and never rank`() = observing {
        val board = listOf(ghost(1, null), ghost(2, 70_000), ghost(3, null), ghost(4, 50_000))

        val shown = shownOf(data(own = imported(80_000), ghosts = board))

        assertEquals(listOf(ghost(4, 50_000), ghost(2, 70_000), ghost(1, null), ghost(3, null)), shown.ghosts)
        assertEquals(3, shown.yourRank)
        assertEquals(5, shown.total)
    }

    @Test
    fun `no import means no rank`() = observing {
        val board = listOf(ghost(1, 70_000), ghost(2, null), ghost(3, 50_000))

        for (own in listOf(LinkedInOwnState.Unknown, LinkedInOwnState.NotPlayed)) {
            val shown = shownOf(data(own = own, ghosts = board))

            assertNull(shown.yourRank)
            assertEquals(board.size, shown.total)
        }
    }

    @Test
    fun `a board outside the data window is OutsideWindow`() = observing {
        val board = listOf(ghost(1, 50_000))

        assertEquals(LinkedInBoardView.OutsideWindow, viewOf(data(boardInWindow = false, ghosts = board)).board)
        assertEquals(LinkedInBoardView.OutsideWindow, viewOf(data(delta = null, boardInWindow = false, ghosts = board)).board)
        assertEquals(
            LinkedInBoardView.Shown(board, yourRank = null, total = 1, updatedAtMs = fetchedAtMs, listGhosts = true),
            viewOf(data(boardInWindow = true, ghosts = board)).board,
        )
    }

    @Test
    fun `no board time yet is Hidden`() = observing {
        assertEquals(LinkedInBoardView.Hidden, viewOf(data(boardFetchedAtMs = null)).board)
        assertEquals(LinkedInBoardView.Hidden, viewOf(data(boardFetchedAtMs = null, boardInWindow = false)).board)
    }

    @Test
    fun `an empty fetched board is Shown with no ghosts`() = observing {
        assertEquals(
            LinkedInBoardView.Shown(emptyList(), yourRank = null, total = 0, updatedAtMs = fetchedAtMs, listGhosts = true),
            viewOf(data()).board,
        )
        assertEquals(
            LinkedInBoardView.Shown(emptyList(), yourRank = 1, total = 1, updatedAtMs = fetchedAtMs, listGhosts = true),
            viewOf(data(own = imported(60_000))).board,
        )
    }

    @Test
    fun `the ghost list shows from day 2`() = observing {
        val listed = listOf(0, 1, 2, 7).associateWith { delta ->
            shownOf(data(delta = delta, boardInWindow = true, ghosts = listOf(ghost(1, 50_000)))).listGhosts
        }

        assertEquals(mapOf(0 to false, 1 to false, 2 to true, 7 to true), listed)
    }

    @Test
    fun `a faster-hints ghost outranks a slower tie`() = observing {
        val hinted = ghost(1, 60_000, hintFree = false)
        val clean = ghost(2, 60_000, hintFree = true)
        // Whether hints split equal times is the game's rule, so the expectation asks the registry for it.
        val cleanIsBetter = registry.rankOrderOf(zip).comparator
            .compare(linkedInScore(60_000, hintFree = true), linkedInScore(60_000, hintFree = false)) < 0

        val shown = shownOf(data(own = imported(60_000, hints = 1), ghosts = listOf(hinted, clean)))

        assertEquals(if (cleanIsBetter) listOf(clean, hinted) else listOf(hinted, clean), shown.ghosts)
        assertEquals(if (cleanIsBetter) 2 else 1, shown.yourRank)
        assertEquals(3, shown.total)
    }
}
