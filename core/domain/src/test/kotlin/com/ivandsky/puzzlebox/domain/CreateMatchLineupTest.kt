package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.match.CreateResult
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.LocalMatch
import com.ivandsky.puzzlebox.data.match.MatchDraft
import com.ivandsky.puzzlebox.data.match.MatchRepository
import com.ivandsky.puzzlebox.domain.lineup.AnyGame
import com.ivandsky.puzzlebox.domain.lineup.ExactPuzzle
import com.ivandsky.puzzlebox.domain.lineup.LineupEntry
import com.ivandsky.puzzlebox.domain.lineup.PuzzleSource
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
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeMatchLocalRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchRepository
import com.ivandsky.puzzlebox.testing.repository.FakeReceivedPuzzleRepository
import java.time.LocalDate
import kotlin.random.Random
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Creating a duel from a lineup: the legs follow it exactly, a dry "Any" refuses by position, a retry never redraws. */
class CreateMatchLineupTest {

    private val today = LocalDate.of(2026, 11, 9)
    private val registry = TestGames.registry(listOf(alpha, beta))
    private val daily = FakeDailyRepository().apply {
        listOf(alpha, beta).forEach { game ->
            setArchiveRange(game, today.minusDays(29)..today)
            setPuzzleNumbers(game, ::number)
        }
    }
    private val completions = FakeCompletionRepository()
    private val server = FakeMatchRepository().apply { signedInUid = ME.uid }
    private val repo = RecordingMatchRepository(server)
    private val local = FakeMatchLocalRepository()
    private val history = ObserveGameHistory(registry, daily, completions)

    private fun create(repository: MatchRepository = repo) = CreateMatch(
        repository, local,
        ComputeMatchLegs(registry, daily, FakeGameCalendar(today), history, FakeReceivedPuzzleRepository(), Random(DRAW_SEED)),
        FakeIdentityService(ME), Random(CODE_SEED),
    )

    private fun number(day: LocalDate): Int = (day.toEpochDay() % 10_000).toInt() + 1

    private fun settings(lineup: List<LineupEntry>, count: Int = 1, games: Set<GameId> = emptySet()) = DuelSettings(
        count, games, todayOnly = true, deadlineHours = 48, includeFriends = false, pastedCodes = emptyList(), lineup = lineup,
    )

    /** Leaves exactly the [open] days of alpha unplayed. */
    private suspend fun solveAlphaExcept(open: Set<Long>) {
        (0L until 30L).filter { it !in open }.forEach { daysAgo ->
            val day = today.minusDays(daysAgo)
            completions.onCompleted(
                CompletionRecord(
                    puzzleRef = TestGames.Ref(alpha, PuzzleKey.Daily(day, number(day))),
                    time = 30_000L, hints = 0, stats = RecordStats(schemaVersion = 1, blob = "{}"), isRecord = true,
                ),
            )
        }
    }

    private suspend fun rows(): List<LocalMatch> = local.observeAll().first()

    @Test
    fun `a lineup creates its legs in order, ignoring count and games, with distinct puzzles of a repeated game`() = runTest {
        solveAlphaExcept(open = setOf(0L, 5L, 9L))
        val custom = LegRef.Custom(alpha, requireNotNull(ShareCode.parse("EXACT1")))
        val lineup = listOf(AnyGame(beta), ExactPuzzle(custom, "bo", "t", "Bo", PuzzleSource.Friend), AnyGame(alpha), AnyGame(alpha))

        val created = create()(settings(lineup, count = 1, games = setOf(beta))) as CreateOutcome.Created

        assertEquals(listOf(beta, alpha, alpha, alpha), created.legs.map { it.ref.gameId })
        assertEquals(custom, created.legs[1].ref)
        assertEquals("bo", created.legs[1].authorUid)
        assertEquals(created.legs, server.matches.getValue(created.code).legs)
        assertEquals(created.legs.size, created.legs.map { it.ref }.toSet().size)
    }

    @Test
    fun `an Any entry with a single unplayed puzzle left refuses with its position and game and writes nothing`() = runTest {
        solveAlphaExcept(open = setOf(3L))
        val custom = LegRef.Custom(alpha, requireNotNull(ShareCode.parse("EXACT1")))
        val lineup = listOf(AnyGame(beta), ExactPuzzle(custom, null, "t", null, PuzzleSource.Community), AnyGame(alpha), AnyGame(alpha))

        assertEquals(CreateOutcome.EntryUndrawable(4, alpha), create()(settings(lineup)))
        assertEquals(0, repo.checked)
        assertTrue(server.matches.isEmpty())
        assertTrue(rows().isEmpty())
    }

    @Test
    fun `a retry after a taken code re-sends the same drawn legs`() = runTest {
        repo.createAnswer = { draft -> if (repo.drafts.size == 1) CreateResult.CodeTaken else server.create(draft) }

        val created = create()(settings(List(4) { AnyGame(alpha) })) as CreateOutcome.Created

        assertEquals(2, repo.drafts.size)
        assertEquals(repo.drafts[0].legs, repo.drafts[1].legs)
        assertEquals(created.legs, repo.drafts[1].legs)
    }

    /** The real fake behind a recorder; [createAnswer] scripts the answer. */
    private class RecordingMatchRepository(private val server: FakeMatchRepository) : MatchRepository by server {
        var checked = 0
        val drafts = mutableListOf<MatchDraft>()
        var createAnswer: suspend (MatchDraft) -> CreateResult = { server.create(it) }

        override suspend fun puzzleCodeTaken(code: ShareCode) = server.puzzleCodeTaken(code).also { checked++ }

        override suspend fun create(draft: MatchDraft): CreateResult {
            drafts += draft
            return createAnswer(draft)
        }
    }

    private companion object {
        const val CODE_SEED = 7
        const val DRAW_SEED = 3
        val ME = Identity(uid = "ann", displayName = "Ann", photoUrl = null)
    }
}
