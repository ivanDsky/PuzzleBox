package com.ivandsky.puzzlebox.domain.lineup

import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.Match
import com.ivandsky.puzzlebox.data.model.PostState
import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
import com.ivandsky.puzzlebox.domain.DrawPools
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.lineup.ResolveLineup.Draw
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import com.ivandsky.puzzlebox.testing.game.TestGames.beta
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeReceivedPuzzleRepository
import java.time.Instant
import java.time.LocalDate
import kotlin.random.Random
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** The lineup draw's rules: order kept, "Any" never repeats or takes an exact puzzle, a dry "Any" names its slot. */
class ResolveLineupTest {

    private val today = LocalDate.of(2026, 11, 8)
    private val registry = TestGames.registry(listOf(alpha, beta))
    private val daily = FakeDailyRepository()
    private val completions = FakeCompletionRepository()
    private val received = FakeReceivedPuzzleRepository()

    private fun resolve(seed: Int = 0) = ResolveLineup(
        DrawPools(registry, daily, FakeGameCalendar(today), ObserveGameHistory(registry, daily, completions), received, Random(seed)),
    )

    private fun archive(game: GameId, days: Long, numbering: (LocalDate) -> Int? = ::number) {
        daily.setArchiveRange(game, today.minusDays(days - 1)..today)
        daily.setPuzzleNumbers(game, numbering)
    }

    private fun number(day: LocalDate): Int = (day.toEpochDay() % 10_000).toInt() + 1

    private fun dailyRef(game: GameId, daysAgo: Long) = LegRef.Daily(game, today.minusDays(daysAgo), number(today.minusDays(daysAgo)))

    private fun exact(ref: LegRef, authorUid: String? = null) = ExactPuzzle(ref, authorUid, "t", null, PuzzleSource.Community)

    private suspend fun solve(game: GameId, daysAgo: Long) {
        val day = today.minusDays(daysAgo)
        completions.onCompleted(
            CompletionRecord(
                puzzleRef = TestGames.Ref(game, PuzzleKey.Daily(day, number(day))),
                time = 30_000L, hints = 0, stats = RecordStats(schemaVersion = 1, blob = "{}"), isRecord = true,
            ),
        )
    }

    private suspend fun friend(code: ShareCode, game: GameId = alpha, author: String = "bo") = received.upsert(
        ReceivedPuzzle(
            code = code, gameId = game, variant = TestGames.variant(game), sharedFormatVersion = 1,
            puzzleBytes = byteArrayOf(TestGames.MAGIC), title = "t", authorName = author, note = "",
            authorTimeMs = 1_000, authorHints = 0, authorUid = author, openedAt = Instant.EPOCH,
            unpublished = false, postState = PostState.Pending, postAttempts = 0, difficulty = null,
        ),
    )

    private fun code(value: String) = requireNotNull(ShareCode.parse(value))

    private suspend fun ResolveLineup.draw(entries: List<LineupEntry>, includeFriends: Boolean = false) =
        invoke(entries, ME, includeFriends)

    @Test
    fun `legs follow the lineup order, a repeated game included, and an exact entry passes through`() = runTest {
        archive(alpha, days = 20)
        archive(beta, days = 20)
        val custom = LegRef.Custom(alpha, code("EXACT1"))

        repeat(SEEDS) { seed ->
            val drawn = resolve(seed).draw(listOf(AnyGame(beta), exact(custom, "bo"), AnyGame(alpha), AnyGame(beta))) as Draw.Drawn

            assertEquals(4, drawn.legs.size)
            assertEquals(listOf(beta, alpha, alpha, beta), drawn.legs.map { it.ref.gameId })
            assertEquals(custom, drawn.legs[1].ref)
            assertEquals("bo", drawn.legs[1].authorUid)
            assertEquals(4, drawn.legs.map { it.ref }.toSet().size)
        }
    }

    @Test
    fun `two Any entries of one game never draw the same puzzle`() = runTest {
        archive(alpha, days = 3)

        repeat(SEEDS) { seed ->
            val drawn = resolve(seed).draw(List(3) { AnyGame(alpha) }) as Draw.Drawn

            assertEquals(3, drawn.legs.map { it.ref }.toSet().size)
        }
    }

    @Test
    fun `an Any entry never draws a puzzle that is also an exact entry, wherever the exact one sits`() = runTest {
        archive(alpha, days = 3)
        val taken = dailyRef(alpha, 1)

        repeat(SEEDS) { seed ->
            val drawn = resolve(seed).draw(listOf(AnyGame(alpha), AnyGame(alpha), exact(taken))) as Draw.Drawn

            assertEquals(taken, drawn.legs[2].ref)
            assertEquals(setOf(dailyRef(alpha, 0), dailyRef(alpha, 2)), drawn.legs.take(2).map { it.ref }.toSet())
        }
    }

    @Test
    fun `an Any entry with no puzzle left names its position and game`() = runTest {
        archive(alpha, days = 2)

        val outcome = resolve().draw(listOf(AnyGame(alpha), exact(dailyRef(alpha, 0)), AnyGame(alpha), AnyGame(alpha)))

        assertEquals(Draw.Undrawable(position = 3, gameId = alpha), outcome)
    }

    @Test
    fun `days the player solved are never drawn, and a fully solved game is undrawable`() = runTest {
        archive(alpha, days = 4)
        solve(alpha, daysAgo = 0)
        solve(alpha, daysAgo = 1)
        solve(alpha, daysAgo = 3)

        repeat(SEEDS) { seed ->
            assertEquals(listOf(dailyRef(alpha, 2)), (resolve(seed).draw(listOf(AnyGame(alpha))) as Draw.Drawn).legs.map { it.ref })
        }
        solve(alpha, daysAgo = 2)
        assertEquals(Draw.Undrawable(1, alpha), resolve().draw(listOf(AnyGame(alpha))))
    }

    @Test
    fun `unnumbered days and a game without a daily are undrawable`() = runTest {
        archive(alpha, days = 5) { null }

        assertEquals(Draw.Undrawable(1, alpha), resolve().draw(listOf(AnyGame(alpha))))
        val unshipped = GameId("unshipped")
        assertEquals(Draw.Undrawable(2, unshipped), resolve().draw(listOf(exact(dailyRef(alpha, 0)), AnyGame(unshipped))))
    }

    @Test
    fun `friends' puzzles are drawn only with the switch on, never my own, and never twice`() = runTest {
        archive(alpha, days = 1) // today only
        friend(code("FRIEN1"))
        friend(code("FRIEN2"), author = ME)

        assertEquals(Draw.Undrawable(2, alpha), resolve().draw(listOf(AnyGame(alpha), AnyGame(alpha))))
        repeat(SEEDS) { seed ->
            val drawn = resolve(seed).draw(listOf(AnyGame(alpha), AnyGame(alpha)), includeFriends = true) as Draw.Drawn

            assertEquals(setOf(dailyRef(alpha, 0), LegRef.Custom(alpha, code("FRIEN1"))), drawn.legs.map { it.ref }.toSet())
        }
        assertEquals(Draw.Undrawable(3, alpha), resolve().draw(List(3) { AnyGame(alpha) }, includeFriends = true))
    }

    @Test
    fun `an Any draw on a three-year archive probes a handful of days, never the range`() = runTest {
        var probes = 0
        archive(alpha, days = THREE_YEARS) { day -> probes++; number(day) }

        val drawn = resolve().draw(List(Match.MAX_LEGS) { AnyGame(alpha) }) as Draw.Drawn

        assertEquals(Match.MAX_LEGS, drawn.legs.size)
        assertTrue("probed $probes days", probes <= Match.MAX_LEGS * 3)
    }

    @Test
    fun `a lineup of none or of more than the match allows is a programmer error`() = runTest {
        for (size in listOf(0, Match.MAX_LEGS + 1)) {
            try {
                resolve().draw(List(size) { AnyGame(alpha) })
                fail("$size entries must throw")
            } catch (expected: IllegalArgumentException) {
                continue
            }
        }
    }

    private companion object {
        const val ME = "me"
        const val SEEDS = 8
        const val THREE_YEARS = 3L * 365
    }
}
