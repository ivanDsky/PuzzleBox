package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.MatchLeg
import com.ivandsky.puzzlebox.data.model.PostState
import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
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
import org.junit.Test

/** Properties of a duel's draw: local pools only, distinct puzzles, never more than asked. */
class ComputeMatchLegsTest {

    private val today = LocalDate.of(2026, 11, 8)
    private val gamma = GameId("gamma")
    private val registry = TestGames.registry(listOf(alpha, beta, gamma))
    private val daily = FakeDailyRepository()
    private val completions = FakeCompletionRepository()
    private val received = FakeReceivedPuzzleRepository()
    private val history = ObserveGameHistory(registry, daily, completions)

    private suspend fun draw(request: DrawRequest, seed: Int = 0): List<MatchLeg> =
        ComputeMatchLegs(registry, daily, FakeGameCalendar(today), history, received, Random(seed))(request)

    private fun request(
        count: Int = 5,
        games: Set<GameId> = setOf(alpha, beta),
        todayOnly: Boolean = false,
        includeFriends: Boolean = false,
        pasted: List<ShareCode> = emptyList(),
    ) = DrawRequest(count, games, todayOnly, includeFriends, pasted, me = ME)

    /** [game]'s source numbers every day by [numbering]; the archive spans the last [days] days when given. */
    private fun source(game: GameId, days: Long?, numbering: (LocalDate) -> Int? = ::number) {
        daily.setArchiveRange(game, days?.let { today.minusDays(it - 1)..today })
        daily.setPuzzleNumbers(game, numbering)
    }

    private fun number(day: LocalDate): Int = (day.toEpochDay() % 10_000).toInt() + 1

    private suspend fun solve(game: GameId, day: LocalDate) = completions.onCompleted(
        CompletionRecord(
            puzzleRef = TestGames.Ref(game, PuzzleKey.Daily(day, number(day))),
            time = 30_000L,
            hints = 0,
            stats = RecordStats(schemaVersion = 1, blob = "{}"),
            isRecord = true,
        ),
    )

    private suspend fun friend(code: ShareCode, game: GameId = alpha, author: String = "bo", unpublished: Boolean = false) =
        received.upsert(
            ReceivedPuzzle(
                code = code, gameId = game, variant = TestGames.variant(game), sharedFormatVersion = 1,
                puzzleBytes = byteArrayOf(TestGames.MAGIC), title = "t", authorName = author, note = "",
                authorTimeMs = 1_000, authorHints = 0, authorUid = author, openedAt = Instant.EPOCH,
                unpublished = unpublished, postState = PostState.Pending, postAttempts = 0, difficulty = null,
            ),
        )

    private fun List<MatchLeg>.dailies() = map { it.ref }.filterIsInstance<LegRef.Daily>()
    private fun List<MatchLeg>.customs() = map { it.ref }.filterIsInstance<LegRef.Custom>()

    @Test
    fun `daily legs lie inside the archive, carry the source number and skip unnumbered days`() = runTest {
        source(alpha, days = 30) { day -> if (day.dayOfMonth % 2 == 0) null else number(day) }
        source(beta, days = 10)
        val range = mapOf(alpha to today.minusDays(29)..today, beta to today.minusDays(9)..today)

        repeat(SEEDS) { seed ->
            val legs = draw(request(count = 10), seed).dailies()

            assertEquals(10, legs.size)
            legs.forEach { leg ->
                assertTrue("$leg outside its archive", leg.day in range.getValue(leg.gameId))
                assertEquals(number(leg.day), leg.number)
            }
            assertTrue(legs.none { it.gameId == alpha && it.day.dayOfMonth % 2 == 0 })
        }
    }

    @Test
    fun `no game and day repeats and the draw never exceeds count`() = runTest {
        source(alpha, days = 4)
        source(beta, days = 3)

        repeat(SEEDS) { seed ->
            for (count in 0..10) {
                val legs = draw(request(count = count), seed)

                assertEquals(minOf(count, 7), legs.size)
                assertEquals(legs.size, legs.dailies().map { it.gameId to it.day }.toSet().size)
            }
        }
    }

    @Test
    fun `solved days are skipped while unsolved ones remain`() = runTest {
        source(alpha, days = 6)
        val unsolved = setOf(today, today.minusDays(4))
        (0L until 6L).map { today.minusDays(it) }.filter { it !in unsolved }.forEach { solve(alpha, it) }

        repeat(SEEDS) { seed ->
            val legs = draw(request(count = 5, games = setOf(alpha)), seed).dailies()

            assertEquals(unsolved, legs.map { it.day }.toSet())
            assertEquals(unsolved.size, legs.size)
        }
    }

    @Test
    fun `a fully solved game falls back to its solved days`() = runTest {
        source(alpha, days = 4)
        (0L until 4L).forEach { solve(alpha, today.minusDays(it)) }

        val legs = draw(request(count = 3, games = setOf(alpha))).dailies()

        assertEquals(3, legs.size)
        assertTrue(legs.all { it.gameId == alpha && it.day in today.minusDays(3)..today })
    }

    @Test
    fun `a one-day pool does not stop the other games from filling the count`() = runTest {
        source(alpha, days = null)
        source(beta, days = 10)

        repeat(SEEDS) { seed ->
            val legs = draw(request(count = 6), seed).dailies()

            assertEquals(6, legs.size)
            assertEquals(listOf(today), legs.filter { it.gameId == alpha }.map { it.day })
        }
    }

    @Test
    fun `the draw is shorter than count only when the combined pool is smaller`() = runTest {
        source(alpha, days = 1)
        source(beta, days = 2)

        assertEquals(3, draw(request(count = 5)).size)
    }

    @Test
    fun `todayOnly draws today's puzzle of each chosen game and nothing else`() = runTest {
        source(alpha, days = 30)
        source(beta, days = 30)
        source(gamma, days = null)

        val legs = draw(request(count = 5, games = setOf(alpha, beta, gamma), todayOnly = true)).dailies()

        assertEquals(setOf(alpha, beta, gamma), legs.map { it.gameId }.toSet())
        assertEquals(3, legs.size)
        assertTrue(legs.all { it.day == today && it.number == number(today) })
    }

    @Test
    fun `a game without an archive range still yields today`() = runTest {
        source(gamma, days = null)

        val legs = draw(request(count = 3, games = setOf(gamma))).dailies()

        assertEquals(listOf(LegRef.Daily(gamma, today, number(today))), legs)
    }

    @Test
    fun `friends' puzzles skip my own, unpublished ones and games outside the request`() = runTest {
        source(alpha, days = 2)
        friend(code("AAAAA1"))
        friend(code("AAAAA2"), author = "cy")
        friend(code("AAAAA3"), author = ME)
        friend(code("AAAAA4"), unpublished = true)
        friend(code("AAAAA5"), game = beta)

        repeat(SEEDS) { seed ->
            val legs = draw(request(count = 10, games = setOf(alpha), includeFriends = true), seed)

            assertEquals(setOf(code("AAAAA1"), code("AAAAA2")), legs.customs().map { it.code }.toSet())
            assertEquals(4, legs.size)
        }
    }

    @Test
    fun `friends' puzzles stay out unless asked for`() = runTest {
        source(alpha, days = 2)
        friend(code("AAAAA1"))

        assertTrue(draw(request(count = 10, games = setOf(alpha))).customs().isEmpty())
    }

    @Test
    fun `pasted codes come first in order, unknown ones skipped and duplicates collapsed`() = runTest {
        source(alpha, days = 5)
        friend(code("BBBBB1"), game = beta)
        friend(code("BBBBB2"))
        friend(code("BBBBB3"), author = ME)
        val pasted = listOf("BBBBB2", "ZZZZZ9", "BBBBB1", "BBBBB3", "BBBBB2").map(::code)

        val legs = draw(request(count = 4, games = setOf(alpha), includeFriends = true, pasted = pasted))

        assertEquals(listOf(code("BBBBB2"), code("BBBBB1")), legs.take(2).map { (it.ref as LegRef.Custom).code })
        assertEquals(2, legs.customs().size)
        assertEquals(4, legs.size)
    }

    @Test
    fun `pasted codes never push the draw past count`() = runTest {
        friend(code("CCCCC1"))
        friend(code("CCCCC2"))

        val legs = draw(request(count = 1, pasted = listOf(code("CCCCC1"), code("CCCCC2"))))

        assertEquals(listOf(code("CCCCC1")), legs.customs().map { it.code })
    }

    @Test
    fun `a custom leg names its author and a daily leg names none`() = runTest {
        source(alpha, days = 3)
        friend(code("DDDDD1"), author = "cy")

        val legs = draw(request(count = 10, games = setOf(alpha), includeFriends = true))

        assertEquals(listOf("cy"), legs.filter { it.ref is LegRef.Custom }.map { it.authorUid })
        assertTrue(legs.filter { it.ref is LegRef.Daily }.all { it.authorUid == null })
        assertEquals(4, legs.size)
    }

    @Test
    fun `no games or no pool at all draws nothing`() = runTest {
        source(alpha, days = 5)
        source(beta, days = null) { null }

        assertEquals(emptyList<MatchLeg>(), draw(request(games = emptySet())))
        assertEquals(emptyList<MatchLeg>(), draw(request(games = setOf(beta))))
        assertEquals(emptyList<MatchLeg>(), draw(request(games = setOf(GameId("unshipped")))))
    }

    private fun code(value: String): ShareCode = requireNotNull(ShareCode.parse(value))

    private companion object {
        const val ME = "me"
        const val SEEDS = 8
    }
}
