package com.ivandsky.puzzlebox.domain.linkedin

import app.cash.turbine.test
import com.ivandsky.puzzlebox.data.linkedin.LinkedInAccountData
import com.ivandsky.puzzlebox.data.linkedin.LinkedInOwner
import com.ivandsky.puzzlebox.domain.GetPuzzleDay
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.CompletionSource
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.linkedin.FakeLinkedInAccount
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** The Profile summary: what the phone already holds about the linked account, with nothing sent to LinkedIn. */
class ObserveLinkedInAccountTest {

    private val queens = GameId("queens")
    private val zip = GameId("zip")
    private val tango = GameId("tango")
    private val today = LocalDate.of(2026, 11, 10)
    private val registry = TestGames.registry(listOf(queens, zip, tango))
    private val clock = Clock.fixed(Instant.parse("2026-11-10T12:00:00Z"), ZoneOffset.UTC)
    private val account = FakeLinkedInAccount()
    private val completions = FakeCompletionRepository()
    private val observe = ObserveLinkedInAccount(account, completions, GetPuzzleDay(registry, clock), registry)

    private fun record(
        game: GameId,
        daysAgo: Long,
        source: CompletionSource = CompletionSource.LinkedIn,
        key: PuzzleKey = PuzzleKey.Daily(today.minusDays(daysAgo), 100),
    ) = CompletionRecord(
        puzzleRef = TestGames.Ref(game, key),
        time = 60_000,
        hints = 0,
        stats = RecordStats(schemaVersion = 1, blob = "{}"),
        source = source,
    )

    @Test
    fun `an empty phone reports zeros and no games`() = runTest {
        assertEquals(
            LinkedInAccountView(name = null, photoPath = null, connectionsSeen = 0, daysThisWeek = 0, daysTotal = 0, games = emptyList(), updatedAtMs = null),
            observe().first(),
        )
    }

    @Test
    fun `name photo connections and update time pass through`() = runTest {
        account.emit(LinkedInAccountData(LinkedInOwner("Ada Lovelace", "/photos/ada.jpg"), connectionsSeen = 12, lastFetchedAtMs = 1_700_000_000_000))

        val view = observe().first()

        assertEquals("Ada Lovelace", view.name)
        assertEquals("/photos/ada.jpg", view.photoPath)
        assertEquals(12, view.connectionsSeen)
        assertEquals(1_700_000_000_000, view.updatedAtMs)
    }

    @Test
    fun `days are counted per game and date from LinkedIn imports, this week and in total`() = runTest {
        completions.setRecords(
            listOf(
                record(queens, 0),
                record(zip, 0),
                record(queens, 6),
                record(queens, 7),
                record(zip, 30),
            ),
        )

        val view = observe().first()

        // Today and six days back are the week: queens today, zip today, queens 6 days ago.
        assertEquals(3, view.daysThisWeek)
        assertEquals(5, view.daysTotal)
    }

    @Test
    fun `an app solve, a friend puzzle and a game this build does not ship are not LinkedIn days`() = runTest {
        completions.setRecords(
            listOf(
                record(queens, 1, source = CompletionSource.App),
                record(queens, 2, key = PuzzleKey.Custom("friend-1")),
                record(GameId("retired"), 3),
                record(zip, 4),
            ),
        )

        val view = observe().first()

        assertEquals(1, view.daysTotal)
        assertEquals(1, view.daysThisWeek)
        assertEquals(listOf("Zip"), view.games)
    }

    @Test
    fun `games are the display names of the games imported, in registry order`() = runTest {
        completions.setRecords(listOf(record(tango, 1), record(queens, 2), record(tango, 3)))

        assertEquals(listOf("Queens", "Tango"), observe().first().games)
    }

    @Test
    fun `a new import is reflected without asking again`() = runTest {
        observe().test {
            assertEquals(0, awaitItem().daysTotal)
            completions.onCompleted(record(zip, 0))
            assertEquals(1, awaitItem().daysTotal)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
