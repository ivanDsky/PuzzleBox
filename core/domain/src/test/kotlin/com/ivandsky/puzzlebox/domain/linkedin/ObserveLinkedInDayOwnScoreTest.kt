package com.ivandsky.puzzlebox.domain.linkedin

import com.ivandsky.puzzlebox.data.linkedin.LinkState
import com.ivandsky.puzzlebox.data.linkedin.LinkedInDayData
import com.ivandsky.puzzlebox.data.linkedin.LinkedInOwnState
import com.ivandsky.puzzlebox.data.linkedin.LinkedInSync
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.CompletionSource
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.game.linkedin.LinkedInRead.Rejected.Kind
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.linkedin.FakeLinkedInDays
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The player's own imported result counts on the boards whatever the link says: an import is a completion on the phone,
 * and a pause or a disconnect keeps it. The status line is a different thing and still says what the link is doing.
 */
class ObserveLinkedInDayOwnScoreTest {

    private val zip = GameId("zip")
    private val day = LocalDate.of(2026, 11, 5)
    private val days = FakeLinkedInDays()
    private val observeLinkedInDay = ObserveLinkedInDay(days, TestGames.registry(listOf(zip)))

    private fun imported(timeMs: Long, hints: Int = 0) = LinkedInOwnState.Imported(
        CompletionRecord(
            puzzleRef = TestGames.Ref(zip, PuzzleKey.Daily(day, 235)),
            time = timeMs,
            hints = hints,
            stats = RecordStats(schemaVersion = 1, blob = "{}"),
            source = CompletionSource.LinkedIn,
        ),
    )

    private suspend fun viewOf(
        link: LinkState,
        own: LinkedInOwnState,
        lastSync: LinkedInSync? = null,
        boardFetchedAtMs: Long? = 1L,
    ): LinkedInDayView {
        days.emit(
            zip, day,
            LinkedInDayData(link, delta = 3, boardInWindow = true, inFlight = false, lastSync = lastSync, own = own, ghosts = emptyList(), boardFetchedAtMs = boardFetchedAtMs),
        )
        return observeLinkedInDay(zip, day).first()
    }

    @Test
    fun `a paused link keeps the imported day as the own score and still says it is paused`() = runTest {
        for (kind in Kind.entries) {
            val view = viewOf(LinkState.Paused("me", kind), imported(60_000, hints = 1))

            assertEquals(BestScore(60_000, 1), view.ownScore)
            val expected = if (kind == Kind.SessionExpired) LinkedInDayStatus.SessionExpired else LinkedInDayStatus.Paused(kind)
            assertEquals(expected, view.status)
        }
    }

    /** A disconnect keeps completions, so the days imported before it keep counting; it says NotConnected like it always did. */
    @Test
    fun `a disconnected player keeps the imported day as the own score`() = runTest {
        val view = viewOf(LinkState.NotConnected, imported(60_000))

        assertEquals(BestScore(60_000, 0), view.ownScore)
        assertEquals(LinkedInDayStatus.NotConnected, view.status)
    }

    @Test
    fun `an offline sync that left the board unread keeps the imported day as the own score`() = runTest {
        val view = viewOf(LinkState.Connected("me"), imported(60_000), lastSync = LinkedInSync.Offline, boardFetchedAtMs = null)

        assertEquals(LinkedInDayStatus.Offline, view.status)
        assertEquals(BestScore(60_000, 0), view.ownScore)
    }

    @Test
    fun `without an import a paused or disconnected day has no own score`() = runTest {
        for (link in listOf(LinkState.Paused("me", Kind.Unexpected), LinkState.NotConnected)) {
            assertEquals(null, viewOf(link, LinkedInOwnState.Unknown).ownScore)
            assertEquals(null, viewOf(link, LinkedInOwnState.NotPlayed).ownScore)
        }
    }
}
