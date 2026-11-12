package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.repository.DailyEntry
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.CompletionSource
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.game.ScoreModel
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.game.TestGames.Ref
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import com.ivandsky.puzzlebox.testing.game.TestGames.beta
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The shared stats (History, Profile, Result) over the merged history: only edition
 * solves count, per game or across every game. The test games' zone is UTC, so the
 * puzzle day at the fixed clock is Nov 10.
 */
class ObserveGameStatsTest {

    private val today = LocalDate.of(2026, 11, 10)
    private val clock = Clock.fixed(Instant.parse("2026-11-10T12:00:00Z"), ZoneOffset.UTC)
    // beta ranks hints first, as Strands does; alpha keeps the time-first default.
    private val registry = GameRegistry(listOf(TestGames.plugin(alpha), hintsFirst(TestGames.plugin(beta))))
    private val completions = FakeCompletionRepository()
    private val daily = FakeDailyRepository()

    private fun stats(game: GameId? = null) = ObserveGameStats(
        registry,
        ObserveGameHistory(registry, daily, completions),
        ComputeStreak(GetPuzzleDay(registry, clock)),
    )(game)

    /** [game]'s solve [daysAgo] days before today; a null [puzzleNumber] is a practice play. */
    private fun solve(game: GameId, daysAgo: Long, time: Long, hints: Int = 0, puzzleNumber: Int? = 100) =
        CompletionRecord(
            puzzleRef = Ref(game, PuzzleKey.Daily(today.minusDays(daysAgo), puzzleNumber)),
            time = time,
            hints = hints,
            stats = RecordStats(schemaVersion = 1, blob = "{}"),
            isRecord = true,
        )

    /** The same solve as it arrives from a LinkedIn import. */
    private fun imported(game: GameId, daysAgo: Long, time: Long, hints: Int = 0) =
        solve(game, daysAgo, time, hints).copy(source = CompletionSource.LinkedIn)

    /** Marks [game]'s day [daysAgo] as opened from the archive: fetched after its own day. */
    private fun archive(game: GameId, daysAgo: Long) = daily.addEntry(
        DailyEntry(
            gameId = game,
            day = today.minusDays(daysAgo),
            puzzleNumber = 99,
            variant = "v",
            fetchedAtMs = clock.millis(),
            archived = true,
        ),
    )

    @Test
    fun `no solves is zeros and no times`() = runTest {
        assertEquals(
            GameStats(
                currentStreak = 0,
                bestStreak = 0,
                solved = 0,
                hintFree = 0,
                averageTimeMs = null,
                playingSince = null,
                best = null,
                rankOrder = RankOrder.TimeThenHints,
            ),
            stats().first(),
        )
    }

    @Test
    fun `edition solves give every number`() = runTest {
        completions.setRecords(
            listOf(
                solve(alpha, daysAgo = 0, time = 30_000L),
                solve(alpha, daysAgo = 1, time = 60_000L, hints = 2),
                // A gap at day 2, then an older three-day run.
                solve(alpha, daysAgo = 3, time = 90_000L),
                solve(alpha, daysAgo = 4, time = 20_000L, hints = 1),
                solve(alpha, daysAgo = 5, time = 50_000L),
            ),
        )

        assertEquals(
            GameStats(
                currentStreak = 2,
                bestStreak = 3,
                solved = 5,
                hintFree = 3,
                averageTimeMs = 50_000L,
                playingSince = today.minusDays(5),
                best = BestScore(timeMs = 20_000L, hints = 1),
                rankOrder = RankOrder.TimeThenHints,
            ),
            stats().first(),
        )
    }

    @Test
    fun `practice plays move no number`() = runTest {
        completions.setRecords(
            listOf(
                solve(alpha, daysAgo = 0, time = 40_000L),
                solve(alpha, daysAgo = 1, time = 1_000L, puzzleNumber = null),
                solve(alpha, daysAgo = 7, time = 2_000L, puzzleNumber = null),
            ),
        )

        val stats = stats().first()

        assertEquals(1, stats.currentStreak)
        assertEquals(1, stats.bestStreak)
        assertEquals(1, stats.solved)
        assertEquals(BestScore(timeMs = 40_000L, hints = 0), stats.best)
        assertEquals(40_000L, stats.averageTimeMs)
        assertEquals(today, stats.playingSince)
    }

    @Test
    fun `a game's stats count only that game, and no game counts them all`() = runTest {
        completions.setRecords(
            listOf(
                solve(alpha, daysAgo = 0, time = 10_000L),
                solve(beta, daysAgo = 1, time = 30_000L, hints = 1),
            ),
        )

        val alphaStats = stats(alpha).first()
        val all = stats().first()

        assertEquals(1, alphaStats.solved)
        assertEquals(1, alphaStats.currentStreak)
        assertEquals(10_000L, alphaStats.averageTimeMs)
        assertEquals(2, all.solved)
        // Either game's solve keeps the day: today and yesterday run together.
        assertEquals(2, all.currentStreak)
        assertEquals(20_000L, all.averageTimeMs)
        assertEquals(1, all.hintFree)
        assertEquals(today.minusDays(1), all.playingSince)
    }

    @Test
    fun `a hints-first game's best is the fewest-hints solve, time breaking ties`() = runTest {
        completions.setRecords(
            listOf(
                solve(beta, daysAgo = 0, time = 10_000L, hints = 2),
                solve(beta, daysAgo = 1, time = 90_000L, hints = 0),
                solve(beta, daysAgo = 2, time = 60_000L, hints = 0),
                // A hint-free practice play never counts.
                solve(beta, daysAgo = 3, time = 1_000L, hints = 0, puzzleNumber = null),
            ),
        )

        val stats = stats(beta).first()

        assertEquals(RankOrder.HintsThenTime, stats.rankOrder)
        assertEquals(BestScore(timeMs = 60_000L, hints = 0), stats.best)
    }

    @Test
    fun `a time-first game's best is the fastest solve`() = runTest {
        completions.setRecords(
            listOf(
                solve(alpha, daysAgo = 0, time = 10_000L, hints = 2),
                solve(alpha, daysAgo = 1, time = 60_000L, hints = 0),
            ),
        )

        val stats = stats(alpha).first()

        assertEquals(RankOrder.TimeThenHints, stats.rankOrder)
        assertEquals(BestScore(timeMs = 10_000L, hints = 2), stats.best)
    }

    @Test
    fun `an archived solve counts toward solves and times but never the streak`() = runTest {
        completions.setRecords(
            listOf(
                solve(alpha, daysAgo = 0, time = 40_000L),
                solve(alpha, daysAgo = 1, time = 20_000L, hints = 1),
            ),
        )
        // Yesterday was opened from the archive today (A13/A23): fetched after its own day.
        daily.addEntry(
            DailyEntry(
                gameId = alpha,
                day = today.minusDays(1),
                puzzleNumber = 99,
                variant = "v",
                fetchedAtMs = clock.millis(),
                archived = true,
            ),
        )

        assertEquals(
            GameStats(
                currentStreak = 1,
                bestStreak = 1,
                solved = 2,
                hintFree = 1,
                averageTimeMs = 30_000L,
                playingSince = today.minusDays(1),
                best = BestScore(timeMs = 20_000L, hints = 1),
                rankOrder = RankOrder.TimeThenHints,
            ),
            stats().first(),
        )
    }

    @Test
    fun `an archived day with an import counts for the streak`() = runTest {
        completions.setRecords(
            listOf(
                solve(alpha, daysAgo = 0, time = 40_000L),
                imported(alpha, daysAgo = 1, time = 20_000L, hints = 1),
            ),
        )
        archive(alpha, daysAgo = 1)

        assertEquals(
            GameStats(
                currentStreak = 2,
                bestStreak = 2,
                solved = 2,
                hintFree = 1,
                averageTimeMs = 30_000L,
                playingSince = today.minusDays(1),
                best = BestScore(timeMs = 20_000L, hints = 1),
                rankOrder = RankOrder.TimeThenHints,
            ),
            stats().first(),
        )
    }

    @Test
    fun `an import beside an app record counts the day and keeps the app record's numbers`() = runTest {
        completions.setRecords(
            listOf(
                solve(alpha, daysAgo = 0, time = 40_000L),
                solve(alpha, daysAgo = 1, time = 20_000L, hints = 1),
                // The player had already solved the day in the app: the import is a play-again.
                imported(alpha, daysAgo = 1, time = 5_000L, hints = 0).copy(isRecord = false),
            ),
        )
        archive(alpha, daysAgo = 1)

        assertEquals(
            GameStats(
                currentStreak = 2,
                bestStreak = 2,
                solved = 2,
                hintFree = 1,
                averageTimeMs = 30_000L,
                playingSince = today.minusDays(1),
                best = BestScore(timeMs = 20_000L, hints = 1),
                rankOrder = RankOrder.TimeThenHints,
            ),
            stats().first(),
        )
    }

    @Test
    fun `importing a day twice is the same as importing it once`() = runTest {
        completions.setRecords(listOf(solve(alpha, daysAgo = 0, time = 40_000L)))
        archive(alpha, daysAgo = 1)
        val import = imported(alpha, daysAgo = 1, time = 20_000L, hints = 1)
        val expected = GameStats(
            currentStreak = 2,
            bestStreak = 2,
            solved = 2,
            hintFree = 1,
            averageTimeMs = 30_000L,
            playingSince = today.minusDays(1),
            best = BestScore(timeMs = 20_000L, hints = 1),
            rankOrder = RankOrder.TimeThenHints,
        )

        completions.onCompleted(import)
        assertEquals(expected, stats().first())

        completions.onCompleted(import)
        assertEquals(expected, stats().first())
    }

    @Test
    fun `an import with no captured daily row counts for the streak`() = runTest {
        completions.setRecords(
            listOf(
                solve(alpha, daysAgo = 0, time = 40_000L),
                imported(alpha, daysAgo = 1, time = 20_000L, hints = 1),
            ),
        )

        assertEquals(
            GameStats(
                currentStreak = 2,
                bestStreak = 2,
                solved = 2,
                hintFree = 1,
                averageTimeMs = 30_000L,
                playingSince = today.minusDays(1),
                best = BestScore(timeMs = 20_000L, hints = 1),
                rankOrder = RankOrder.TimeThenHints,
            ),
            stats().first(),
        )
    }

    private fun <P> hintsFirst(
        plugin: GamePlugin<P, TestGames.Session, TestGames.Model>,
    ): GamePlugin<P, TestGames.Session, TestGames.Model> = GamePlugin(
        definition = plugin.definition,
        rules = plugin.rules,
        session = plugin.session,
        score = object : ScoreModel<TestGames.Session> {
            override fun completion(session: TestGames.Session) = plugin.score.completion(session)
            override val rankOrder = RankOrder.HintsThenTime
        },
        stateOf = plugin.stateOf,
        initialSession = plugin.initialSession,
        refOf = plugin.refOf,
        playAgainSession = plugin.playAgainSession,
        rendererModel = plugin.rendererModel,
    )
}
