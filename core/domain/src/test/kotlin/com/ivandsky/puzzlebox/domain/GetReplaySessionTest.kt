package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.SessionBlob
import com.ivandsky.puzzlebox.model.GameId
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
 * The replay hand-off (ticket 14 → 13; tango-05 generalizes it): the day's cached payload
 * dispatches to the NAMED game's own plugin with THIS day's identity (ADR-0005
 * envelope), and untrustable inputs never launch (A16). Per-game round-trips live with
 * each game (ZipPluginTest, TangoReplaySessionTest, QueensPluginTest).
 */
class GetReplaySessionTest {

    private val day: LocalDate = LocalDate.of(2026, 10, 31)

    private fun replay(daily: FakeDailyRepository, games: List<GameId> = listOf(alpha, beta)) =
        GetReplaySession(daily, TestGames.registry(games))

    @Test
    fun `missing pieces launch nothing`() = runTest {
        val empty = FakeDailyRepository()
        assertEquals(ReplayOutcome.Missing, replay(empty)(alpha, day, puzzleNumber = 592))
        assertEquals(ReplayOutcome.Missing, replay(empty)(beta, day, puzzleNumber = 301))

        // A cached day of a game no registration authored is never replayed.
        val unregistered = GameId("not-a-game")
        val daily = FakeDailyRepository().apply { setCached(unregistered, day, TestGames.puzzle(unregistered)) }
        assertEquals(ReplayOutcome.Missing, replay(daily)(unregistered, day, puzzleNumber = 1))
    }

    @Test
    fun `a cached day hands the named game's session with the day's identity`() = runTest {
        val cached = TestGames.puzzle(beta, tag = 7)
        val daily = FakeDailyRepository().apply { setCached(beta, day, cached) }

        val ready = replay(daily)(beta, day, puzzleNumber = 301) as ReplayOutcome.Ready

        // The envelope NAMES the game (ADR-0005) — the play route dispatches on it.
        assertEquals(beta, SessionBlob.decode(ready.session)?.game)
        assertArrayEquals(TestGames.replayBlob(beta, cached, day, 301), ready.session)
    }

    @Test
    fun `missing puzzle number is passed through as the day's identity`() = runTest {
        val cached = TestGames.puzzle(alpha)
        val daily = FakeDailyRepository().apply { setCached(alpha, day, cached) }

        val ready = replay(daily)(alpha, day, puzzleNumber = null) as ReplayOutcome.Ready

        assertArrayEquals(TestGames.replayBlob(alpha, cached, day, null), ready.session)
    }

    @Test
    fun `an undecodable cached payload never launches (A16)`() = runTest {
        val corrupt = Puzzle(alpha, TestGames.variant(alpha), 1, "this is not a payload".toByteArray(Charsets.UTF_8))
        val daily = FakeDailyRepository().apply { setCached(alpha, day, corrupt) }

        assertEquals(
            ReplayOutcome.Unreadable(DecodeError.Malformed("not a alpha payload")),
            replay(daily)(alpha, day, puzzleNumber = 592),
        )
    }
}
