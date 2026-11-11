package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.repository.DailyDay
import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.SessionBlob
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import com.ivandsky.puzzlebox.testing.game.TestGames.beta
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Opening a past day (ticket 14 → 13; tango-05 generalizes it; ADR-0012): the day's
 * puzzle — cached, or fetched for an archive source — dispatches to the NAMED game's own
 * plugin with THIS day's identity (ADR-0005 envelope), each failure keeps its cause, and
 * untrustable inputs never launch (A16). Per-game round-trips live with each game
 * (ZipPluginTest, TangoPlayAgainSessionTest, QueensPluginTest).
 */
class LoadPastDayTest {

    private val day: LocalDate = LocalDate.of(2026, 10, 31)

    private fun replay(daily: FakeDailyRepository, games: List<GameId> = listOf(alpha, beta)) =
        LoadPastDay(daily, TestGames.registry(games))

    @Test
    fun `missing pieces launch nothing`() = runTest {
        val empty = FakeDailyRepository()
        assertEquals(PastDayOutcome.Missing, replay(empty)(alpha, day, puzzleNumber = 592))
        assertEquals(PastDayOutcome.Missing, replay(empty)(beta, day, puzzleNumber = 301))

        // A cached day of a game no registration authored is never replayed.
        val unregistered = GameId("not-a-game")
        val daily = FakeDailyRepository().apply { setCached(unregistered, day, TestGames.puzzle(unregistered)) }
        assertEquals(PastDayOutcome.Missing, replay(daily)(unregistered, day, puzzleNumber = 1))
        assertEquals(0, daily.dayCalls(unregistered, day))
    }

    @Test
    fun `an archive day fetched on open launches with the fetched puzzle number`() = runTest {
        val fetched = TestGames.puzzle(alpha, tag = 3)
        val daily = FakeDailyRepository().apply { setDay(alpha, day, DailyDay.Ready(fetched, 584)) }

        val ready = replay(daily)(alpha, day, puzzleNumber = null) as PastDayOutcome.Ready

        assertArrayEquals(TestGames.playAgainBlob(alpha, fetched, PuzzleKey.Daily(day, 584)), ready.session)
        assertEquals(1, daily.dayCalls(alpha, day))
    }

    @Test
    fun `out-of-range and failed reads keep their cause`() = runTest {
        val daily = FakeDailyRepository().apply {
            setArchiveRange(alpha, day.minusDays(10)..day.minusDays(1))
        }
        assertEquals(PastDayOutcome.OutOfRange, replay(daily)(alpha, day, puzzleNumber = null))

        listOf(
            FetchError.Unavailable("offline"),
            FetchError.NotFound("404"),
            FetchError.Untrusted("gate"),
            FetchError.Throttled("cooldown"),
        ).forEach { error ->
            daily.setDay(beta, day, DailyDay.Failed(error))
            assertEquals(PastDayOutcome.Failed(error), replay(daily)(beta, day, puzzleNumber = 1))
        }
    }

    @Test
    fun `a cached day hands the named game's session with the day's identity`() = runTest {
        val cached = TestGames.puzzle(beta, tag = 7)
        val daily = FakeDailyRepository().apply { setCached(beta, day, cached) }

        val ready = replay(daily)(beta, day, puzzleNumber = 301) as PastDayOutcome.Ready

        // The envelope NAMES the game (ADR-0005) — the play route dispatches on it.
        assertEquals(beta, SessionBlob.decode(ready.session)?.game)
        assertArrayEquals(TestGames.playAgainBlob(beta, cached, PuzzleKey.Daily(day, 301)), ready.session)
    }

    @Test
    fun `missing puzzle number is passed through as the day's identity`() = runTest {
        val cached = TestGames.puzzle(alpha)
        val daily = FakeDailyRepository().apply { setCached(alpha, day, cached) }

        val ready = replay(daily)(alpha, day, puzzleNumber = null) as PastDayOutcome.Ready

        assertArrayEquals(TestGames.playAgainBlob(alpha, cached, PuzzleKey.Daily(day, null)), ready.session)
    }

    @Test
    fun `an undecodable cached payload never launches (A16)`() = runTest {
        val corrupt = Puzzle(alpha, TestGames.variant(alpha), 1, "this is not a payload".toByteArray(Charsets.UTF_8))
        val daily = FakeDailyRepository().apply { setCached(alpha, day, corrupt) }

        assertEquals(
            PastDayOutcome.Unreadable(DecodeError.Malformed("not a alpha payload")),
            replay(daily)(alpha, day, puzzleNumber = 592),
        )
    }
}
